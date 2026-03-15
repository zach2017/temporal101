"""
Data Access Layer (DAL) — Flask API
====================================
Demonstrates RBAC + ABAC enforcement between a frontend and PostgreSQL.
"""

import os, json, datetime, functools, decimal, logging
import jwt
import psycopg2
import psycopg2.extras
from flask import Flask, request, jsonify, g
from flask_cors import CORS

# ── Logging ───────────────────────────────────────────────────
logging.basicConfig(level=logging.DEBUG)
log = logging.getLogger("dal")

# ── Config ────────────────────────────────────────────────────
app = Flask(__name__)
CORS(app)

DATABASE_URL  = os.environ["DATABASE_URL"]
JWT_SECRET    = os.environ.get("JWT_SECRET", "supersecretkey123")
JWT_ALGORITHM = "HS256"
JWT_EXP_HOURS = 24

CLASSIFICATION_RANK = {"public": 0, "internal": 1, "confidential": 2}


# ── JSON serialization helper ─────────────────────────────────
# Convert all DB rows to JSON-safe dicts BEFORE calling jsonify().

def serialize(obj):
    """Recursively convert datetime / Decimal / etc. to JSON-safe types."""
    if obj is None:
        return None
    if isinstance(obj, (str, int, float, bool)):
        return obj
    if isinstance(obj, (datetime.datetime, datetime.date)):
        return obj.isoformat()
    if isinstance(obj, datetime.timedelta):
        return str(obj)
    if isinstance(obj, decimal.Decimal):
        return float(obj)
    if isinstance(obj, dict):
        return {k: serialize(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [serialize(i) for i in obj]
    return str(obj)


# ── DB helpers ────────────────────────────────────────────────

def get_db():
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
    result = [serialize(dict(r)) for r in rows]
    if one:
        return result[0] if result else None
    return result

def execute(sql, params=None):
    cur = get_db().cursor()
    cur.execute(sql, params or ())
    cur.close()


# ── Audit helper ──────────────────────────────────────────────

def audit(user_id, action, resource, details=None):
    try:
        execute(
            "INSERT INTO audit_log (user_id, action, resource, details) VALUES (%s,%s,%s,%s)",
            (user_id, action, resource, json.dumps(details or {})),
        )
    except Exception as e:
        log.error(f"Audit log failed: {e}")


# ── JWT helpers ───────────────────────────────────────────────

def create_token(user):
    payload = {
        "sub":  user["id"],
        "usr":  user["username"],
        "role": user["role"],
        "dept": user["department"],
        "exp":  datetime.datetime.now(datetime.timezone.utc)
                + datetime.timedelta(hours=JWT_EXP_HOURS),
    }
    token = jwt.encode(payload, JWT_SECRET, algorithm=JWT_ALGORITHM)
    # PyJWT >=2.0 returns str; older versions return bytes
    if isinstance(token, bytes):
        token = token.decode("utf-8")
    log.debug(f"Created token for user={user['username']}, len={len(token)}")
    return token

def decode_token(token):
    return jwt.decode(
        token,
        JWT_SECRET,
        algorithms=[JWT_ALGORITHM],
        options={"verify_exp": True, "require": ["exp", "sub"]},
        leeway=datetime.timedelta(seconds=10),
    )

def auth_required(fn):
    """Decorator — extracts and validates the JWT, sets g.user."""
    @functools.wraps(fn)
    def wrapper(*a, **kw):
        header = request.headers.get("Authorization", "")
        log.debug(f"Auth header: present={bool(header)}, bearer={header[:7] if header else ''}")
        if not header.startswith("Bearer "):
            return jsonify({"error": "Missing or invalid Authorization header"}), 401
        token = header[7:].strip()
        if not token:
            return jsonify({"error": "Empty token"}), 401
        try:
            g.user = decode_token(token)
            log.debug(f"Token OK: user={g.user.get('usr')}, role={g.user.get('role')}")
        except jwt.ExpiredSignatureError:
            return jsonify({"error": "Token expired"}), 401
        except jwt.InvalidTokenError as e:
            log.error(f"JWT decode failed: {e}")
            log.error(f"  token_len={len(token)}, first_20={token[:20]}...")
            return jsonify({"error": "Invalid token"}), 401
        return fn(*a, **kw)
    return wrapper


# ══════════════════════════════════════════════════════════════
#  DAL CORE — the access-control engine
# ══════════════════════════════════════════════════════════════

class DAL:

    @staticmethod
    def load_acl(role, resource):
        return query(
            "SELECT * FROM acl_rules WHERE role = %s AND resource = %s",
            (role, resource),
            one=True,
        )

    @staticmethod
    def check_permission(acl, action):
        if not acl:
            return False
        return action in acl["allowed_actions"]

    @staticmethod
    def build_where_clauses(acl, user):
        clauses, params = [], []
        filt = acl.get("attribute_filter") or {}

        if filt.get("same_department"):
            clauses.append("d.department = %s")
            params.append(user["dept"])

        max_cls = filt.get("max_classification")
        if max_cls:
            allowed = [k for k, v in CLASSIFICATION_RANK.items()
                       if v <= CLASSIFICATION_RANK.get(max_cls, 0)]
            placeholders = ",".join(["%s"] * len(allowed))
            clauses.append(f"d.classification IN ({placeholders})")
            params.extend(allowed)

        return (" AND ".join(clauses), params) if clauses else ("", [])

    @staticmethod
    def post_filter(rows, acl, user):
        if user["role"] in ("admin", "auditor"):
            return rows
        out = []
        for r in rows:
            r = dict(r)
            if r.get("classification") == "confidential":
                r["content"] = "[REDACTED — insufficient clearance]"
            out.append(r)
        return out

    @classmethod
    def access_documents(cls, user, action="read", doc_id=None):
        acl = cls.load_acl(user["role"], "documents")

        if not cls.check_permission(acl, action):
            audit(user["sub"], "DENIED", "documents",
                  {"action": action, "reason": "RBAC"})
            return 403, {"error": f"Role '{user['role']}' cannot '{action}' documents"}

        where_sql, where_params = cls.build_where_clauses(acl, user)

        sql = """
            SELECT d.id, d.title, d.content, d.classification,
                   d.department, u.username AS owner, d.created_at
            FROM   documents d
            JOIN   users u ON u.id = d.owner_id
        """
        params, conditions = [], []

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
        rows = cls.post_filter(rows, acl, user)

        audit(user["sub"], action, "documents",
              {"count": len(rows), "doc_id": doc_id})

        return 200, rows


# ══════════════════════════════════════════════════════════════
#  ROUTES
# ══════════════════════════════════════════════════════════════

@app.route("/api/login", methods=["POST"])
def login():
    body = request.get_json(force=True)
    log.debug(f"Login attempt: {body.get('username')}")
    user = query(
        "SELECT * FROM users WHERE username = %s AND password = %s",
        (body.get("username"), body.get("password")),
        one=True,
    )
    if not user:
        return jsonify({"error": "Invalid credentials"}), 401
    token = create_token(user)
    log.debug(f"Login OK: {user['username']}, token_type={type(token).__name__}")
    return jsonify({
        "token": token,
        "user": {
            "id": user["id"],
            "username": user["username"],
            "role": user["role"],
            "department": user["department"],
        },
    })


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


@app.route("/api/acl", methods=["GET"])
@auth_required
def get_acl():
    rules = query("SELECT * FROM acl_rules WHERE role = %s", (g.user["role"],))
    return jsonify(rules)


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


@app.route("/api/health")
def health():
    return jsonify({"status": "ok"})


@app.route("/api/dal-info")
def dal_info():
    return jsonify({
        "pipeline": [
            {"step": 1, "name": "Authenticate",    "desc": "JWT token is validated; user identity and attributes are extracted."},
            {"step": 2, "name": "Load ACL",         "desc": "ACL rules for the user's role + requested resource are fetched from the database."},
            {"step": 3, "name": "RBAC Gate",        "desc": "The requested action (read/write/delete) is checked against allowed_actions in the ACL."},
            {"step": 4, "name": "ABAC Rewrite",     "desc": "attribute_filter conditions are translated into SQL WHERE clauses."},
            {"step": 5, "name": "Execute Query",    "desc": "The rewritten, restricted SQL is executed against PostgreSQL."},
            {"step": 6, "name": "Post-Filter",      "desc": "Returned rows are further filtered (e.g., redacting content for non-privileged roles)."},
            {"step": 7, "name": "Audit",            "desc": "Every access attempt (granted or denied) is logged for compliance."},
        ],
        "roles": {
            "admin":   "Full read/write/delete on all documents, all departments, all classifications.",
            "editor":  "Read + write within own department only.",
            "viewer":  "Read-only within own department; max classification = internal.",
            "auditor": "Read-only across all departments and classifications (compliance role).",
        },
    })


# ── Debug: test JWT on startup ────────────────────────────────
@app.before_request
def log_request():
    log.debug(f"{request.method} {request.path}")


if __name__ == "__main__":
    log.info(f"PyJWT version: {jwt.__version__}")
    log.info(f"JWT_SECRET loaded: len={len(JWT_SECRET)}")
    log.info(f"DATABASE_URL: {DATABASE_URL[:40]}...")
    app.run(host="0.0.0.0", port=5000, debug=True)
