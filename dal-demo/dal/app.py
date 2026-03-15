"""
Data Access Layer (DAL) — Flask API
====================================
Demonstrates RBAC + ABAC enforcement between a frontend and PostgreSQL.

Flow:
  1. User authenticates → receives JWT
  2. Every data request carries the JWT
  3. DAL resolves the user's role & attributes
  4. ACL rules are evaluated (RBAC check)
  5. SQL query is *rewritten* with WHERE clauses (ABAC filters)
  6. Results are post-filtered if needed
  7. Action is written to the audit log
"""

import os, json, datetime, functools, decimal
import jwt
import psycopg2
import psycopg2.extras
from flask import Flask, request, jsonify, g
from flask_cors import CORS

# ── Config ────────────────────────────────────────────────────
app = Flask(__name__)
CORS(app)

# Custom JSON encoder for psycopg2 types (datetime, Decimal, etc.)
class CustomJSONProvider(app.json_provider_class):
    def default(self, o):
        if isinstance(o, (datetime.datetime, datetime.date)):
            return o.isoformat()
        if isinstance(o, datetime.timedelta):
            return str(o)
        if isinstance(o, decimal.Decimal):
            return float(o)
        return super().default(o)

app.json_provider_class = CustomJSONProvider
app.json = CustomJSONProvider(app)

DATABASE_URL = os.environ["DATABASE_URL"]
JWT_SECRET   = os.environ.get("JWT_SECRET", "change-me")
JWT_EXP_HOURS = 24

CLASSIFICATION_RANK = {"public": 0, "internal": 1, "confidential": 2}

# ── DB helpers ────────────────────────────────────────────────

def get_db():
    """Return a per-request database connection."""
    if "db" not in g:
        g.db = psycopg2.connect(DATABASE_URL)
        g.db.autocommit = True
    return g.db

@app.teardown_appcontext
def close_db(_exc):
    db = g.pop("db", None)
    if db is not None:
        db.close()

def query(sql, params=None, one=False):
    cur = get_db().cursor(cursor_factory=psycopg2.extras.RealDictCursor)
    cur.execute(sql, params or ())
    rows = cur.fetchall()
    cur.close()
    if one:
        return dict(rows[0]) if rows else None
    return [dict(r) for r in rows]

def execute(sql, params=None):
    cur = get_db().cursor()
    cur.execute(sql, params or ())
    cur.close()

# ── Audit helper ──────────────────────────────────────────────

def audit(user_id, action, resource, details=None):
    execute(
        "INSERT INTO audit_log (user_id, action, resource, details) VALUES (%s,%s,%s,%s)",
        (user_id, action, resource, json.dumps(details or {})),
    )

# ── JWT helpers ───────────────────────────────────────────────

def create_token(user):
    payload = {
        "sub":  user["id"],
        "usr":  user["username"],
        "role": user["role"],
        "dept": user["department"],
        "exp":  datetime.datetime.utcnow() + datetime.timedelta(hours=JWT_EXP_HOURS),
    }
    return jwt.encode(payload, JWT_SECRET, algorithm="HS256")

def decode_token(token):
    return jwt.decode(token, JWT_SECRET, algorithms=["HS256"])

def auth_required(fn):
    """Decorator – extracts and validates the JWT, sets g.user."""
    @functools.wraps(fn)
    def wrapper(*a, **kw):
        header = request.headers.get("Authorization", "")
        if not header.startswith("Bearer "):
            return jsonify({"error": "Missing or invalid Authorization header"}), 401
        try:
            g.user = decode_token(header[7:])
        except jwt.ExpiredSignatureError:
            return jsonify({"error": "Token expired"}), 401
        except jwt.InvalidTokenError:
            return jsonify({"error": "Invalid token"}), 401
        return fn(*a, **kw)
    return wrapper

# ══════════════════════════════════════════════════════════════
#  DAL CORE — the access-control engine
# ══════════════════════════════════════════════════════════════

class DAL:
    """
    Central Data Access Layer.

    For every data request it:
      1. Loads ACL rules for the caller's role + resource
      2. Checks if the requested action is allowed  (RBAC)
      3. Builds extra SQL WHERE clauses              (ABAC)
      4. Optionally post-filters rows                (ABAC)
    """

    # ── 1. Load ACL ───────────────────────────────────────────
    @staticmethod
    def load_acl(role, resource):
        return query(
            "SELECT * FROM acl_rules WHERE role = %s AND resource = %s",
            (role, resource),
            one=True,
        )

    # ── 2. RBAC check ────────────────────────────────────────
    @staticmethod
    def check_permission(acl, action):
        if not acl:
            return False
        return action in acl["allowed_actions"]

    # ── 3. ABAC — build SQL WHERE clauses ────────────────────
    @staticmethod
    def build_where_clauses(acl, user):
        """
        Reads attribute_filter from the ACL rule and returns
        (sql_fragment, params) to inject into the query.
        """
        clauses = []
        params  = []
        filt = acl.get("attribute_filter") or {}

        # Same-department restriction
        if filt.get("same_department"):
            clauses.append("d.department = %s")
            params.append(user["dept"])

        # Classification ceiling
        max_cls = filt.get("max_classification")
        if max_cls:
            allowed = [k for k, v in CLASSIFICATION_RANK.items()
                       if v <= CLASSIFICATION_RANK.get(max_cls, 0)]
            placeholders = ",".join(["%s"] * len(allowed))
            clauses.append(f"d.classification IN ({placeholders})")
            params.extend(allowed)

        # Auditor flag — read-only, full access (no extra clauses)
        # This is an example of an attribute that *removes* restrictions.

        return (" AND ".join(clauses), params) if clauses else ("", [])

    # ── 4. Post-filter (optional second ABAC pass) ───────────
    @staticmethod
    def post_filter(rows, acl, user):
        """
        Example: strip the 'content' field from confidential docs
        unless the user is admin or auditor.
        """
        if user["role"] in ("admin", "auditor"):
            return rows
        out = []
        for r in rows:
            r = dict(r)
            if r.get("classification") == "confidential":
                r["content"] = "[REDACTED — insufficient clearance]"
            out.append(r)
        return out

    # ── Combined entry-point ──────────────────────────────────
    @classmethod
    def access_documents(cls, user, action="read", doc_id=None):
        """
        Full DAL pipeline for the 'documents' resource.
        Returns (status_code, data_or_error).
        """
        acl = cls.load_acl(user["role"], "documents")

        # RBAC gate
        if not cls.check_permission(acl, action):
            audit(user["sub"], "DENIED", "documents",
                  {"action": action, "reason": "RBAC"})
            return 403, {"error": f"Role '{user['role']}' cannot '{action}' documents"}

        # ABAC — query rewriting
        where_sql, where_params = cls.build_where_clauses(acl, user)

        sql = """
            SELECT d.id, d.title, d.content, d.classification,
                   d.department, u.username AS owner, d.created_at
            FROM   documents d
            JOIN   users u ON u.id = d.owner_id
        """
        params = []
        conditions = []

        if where_sql:
            conditions.append(where_sql)
            params.extend(where_params)

        if doc_id is not None:
            conditions.append("d.id = %s")
            params.append(doc_id)

        if conditions:
            sql += " WHERE " + " AND ".join(conditions)

        sql += " ORDER BY d.created_at DESC"

        rows = query(sql, params)

        # ABAC — post-filter
        rows = cls.post_filter(rows, acl, user)

        audit(user["sub"], action, "documents",
              {"count": len(rows), "doc_id": doc_id})

        return 200, rows


# ══════════════════════════════════════════════════════════════
#  ROUTES
# ══════════════════════════════════════════════════════════════

# ── Auth ──────────────────────────────────────────────────────
@app.route("/api/login", methods=["POST"])
def login():
    body = request.get_json(force=True)
    user = query(
        "SELECT * FROM users WHERE username = %s AND password = %s",
        (body.get("username"), body.get("password")),
        one=True,
    )
    if not user:
        return jsonify({"error": "Invalid credentials"}), 401
    token = create_token(user)
    return jsonify({
        "token": token,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "role": user["role"],
            "department": user["department"],
        },
    })

# ── Documents (through DAL) ──────────────────────────────────
@app.route("/api/documents", methods=["GET"])
@auth_required
def get_documents():
    code, data = DAL.access_documents(g.user, action="read")
    return jsonify(data), code

@app.route("/api/documents/<int:doc_id>", methods=["GET"])
@auth_required
def get_document(doc_id):
    code, data = DAL.access_documents(g.user, action="read", doc_id=doc_id)
    return jsonify(data), code

# ── ACL introspection (useful for the frontend) ──────────────
@app.route("/api/acl", methods=["GET"])
@auth_required
def get_acl():
    rules = query("SELECT * FROM acl_rules WHERE role = %s", (g.user["role"],))
    return jsonify(rules)

# ── Audit log (admin/auditor only) ───────────────────────────
@app.route("/api/audit", methods=["GET"])
@auth_required
def get_audit():
    if g.user["role"] not in ("admin", "auditor"):
        return jsonify({"error": "Forbidden"}), 403
    rows = query("""
        SELECT a.*, u.username
        FROM   audit_log a
        JOIN   users u ON u.id = a.user_id
        ORDER  BY a.created_at DESC
        LIMIT  50
    """)
    return jsonify(rows)

# ── Health ────────────────────────────────────────────────────
@app.route("/api/health")
def health():
    return jsonify({"status": "ok"})

# ── DAL explanation endpoint ──────────────────────────────────
@app.route("/api/dal-info")
def dal_info():
    """Returns a summary of how the DAL pipeline works."""
    return jsonify({
        "pipeline": [
            {"step": 1, "name": "Authenticate",    "desc": "JWT token is validated; user identity and attributes are extracted."},
            {"step": 2, "name": "Load ACL",         "desc": "ACL rules for the user's role + requested resource are fetched from the database."},
            {"step": 3, "name": "RBAC Gate",        "desc": "The requested action (read/write/delete) is checked against allowed_actions in the ACL."},
            {"step": 4, "name": "ABAC Rewrite",     "desc": "attribute_filter conditions are translated into SQL WHERE clauses (e.g., same-department, classification ceiling)."},
            {"step": 5, "name": "Execute Query",    "desc": "The rewritten, restricted SQL is executed against PostgreSQL."},
            {"step": 6, "name": "Post-Filter",      "desc": "Returned rows are further filtered (e.g., redacting content of confidential docs for non-privileged roles)."},
            {"step": 7, "name": "Audit",            "desc": "Every access attempt (granted or denied) is logged for compliance."},
        ],
        "roles": {
            "admin":   "Full read/write/delete on all documents, all departments, all classifications.",
            "editor":  "Read + write within own department only.",
            "viewer":  "Read-only within own department; max classification = internal.",
            "auditor": "Read-only across all departments and classifications (compliance role).",
        },
    })


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000, debug=True)
