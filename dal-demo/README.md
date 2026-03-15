# DAL Explorer — Data Access Layer Demo

A Docker Compose project demonstrating a **Data Access Layer (DAL)** with
**RBAC** (Role-Based Access Control) and **ABAC** (Attribute-Based Access Control)
enforcement between a browser frontend and PostgreSQL.

---

## Architecture

```
┌──────────────┐       ┌──────────────────────────────────┐       ┌────────────┐
│   Browser    │──────▶│     Python DAL  (Flask :5000)     │──────▶│ PostgreSQL │
│  HTML / JS   │  JWT  │                                    │  SQL  │   :5432    │
│  Tailwind    │◀──────│  Auth → ACL → RBAC → ABAC → Audit │◀──────│   + RLS    │
└──────────────┘       └──────────────────────────────────┘       └────────────┘
       ▲                            ▲
       │        Nginx :8080         │
       └── static files + /api/ ───┘
            reverse-proxy
```

### DAL Pipeline (every request)

| Step | Name           | What Happens                                              |
|------|----------------|-----------------------------------------------------------|
| 1    | Authenticate   | JWT is validated; user identity & attributes extracted     |
| 2    | Load ACL       | ACL rules for the user's role + resource fetched from DB   |
| 3    | RBAC Gate      | Requested action checked against `allowed_actions`         |
| 4    | ABAC Rewrite   | `attribute_filter` → SQL WHERE clauses injected            |
| 5    | Execute Query  | Rewritten SQL runs on PostgreSQL                           |
| 6    | Post-Filter    | Rows further filtered (e.g. content redaction)             |
| 7    | Audit          | Access attempt logged (granted or denied)                  |

---

## Demo Users

| User    | Role    | Department  | What They See                                                 |
|---------|---------|-------------|---------------------------------------------------------------|
| alice   | admin   | engineering | Everything — all docs, all departments, all classifications   |
| bob     | editor  | marketing   | Read + write on marketing docs only                           |
| charlie | viewer  | engineering | Read-only engineering docs; max classification = internal     |
| diana   | viewer  | marketing   | Read-only marketing docs; max classification = internal       |
| eve     | auditor | compliance  | Read-only across ALL departments (compliance view)            |

> **Password for all users:** `password123`

---

## Quick Start

```bash
# Clone / download the project, then:
docker compose up --build

# Open in browser:
#   http://localhost:8080
```

### What to Try

1. **Log in as `alice` (admin)** — you see all 10 documents, unrestricted.
2. **Switch to `charlie` (viewer/engineering)** — only engineering docs up to
   "internal" classification; confidential content is `[REDACTED]`.
3. **Switch to `diana` (viewer/marketing)** — same restrictions, different dept.
4. **Log in as `eve` (auditor)** — sees everything (read-only); check the Audit
   Log tab to see every access attempt.
5. **Check ACL Rules tab** — shows the raw ACL row for your current role.

---

## Project Structure

```
dal-demo/
├── docker-compose.yml          # Orchestration
├── db/
│   └── init.sql                # Schema, seed data, RLS policies
├── dal/
│   ├── Dockerfile
│   ├── requirements.txt
│   └── app.py                  # Flask DAL API
├── frontend/
│   ├── nginx.conf              # Reverse proxy config
│   └── index.html              # Tailwind SPA
└── README.md
```

---

## API Endpoints

| Method | Path                   | Auth?  | Description                          |
|--------|------------------------|--------|--------------------------------------|
| POST   | `/api/login`           | No     | Returns JWT + user info              |
| GET    | `/api/documents`       | Yes    | Documents filtered by DAL            |
| GET    | `/api/documents/:id`   | Yes    | Single document (DAL-filtered)       |
| GET    | `/api/acl`             | Yes    | ACL rules for current role           |
| GET    | `/api/audit`           | Yes    | Audit log (admin/auditor only)       |
| GET    | `/api/dal-info`        | No     | Pipeline & role descriptions         |
| GET    | `/api/health`          | No     | Health check                         |

---

## Extending

- **Add a new role** — insert into `users` and `acl_rules`; the DAL picks it up
  automatically.
- **Add ABAC attributes** — extend `attribute_filter` JSON and add matching
  logic in `DAL.build_where_clauses()`.
- **Swap PostgreSQL for Elasticsearch** — implement the same `DAL` class with
  query-DSL bool/filter rewriting instead of SQL WHERE clauses.
- **Add write operations** — the ACL already stores `write`/`delete` actions;
  add corresponding Flask routes and DAL methods.

---

## License

MIT — use freely for learning and prototyping.
