-- ============================================================
--  DAL Demo – Schema, Seed Data & Row-Level Security Policies
-- ============================================================

-- ── 1. Users & Roles ─────────────────────────────────────────
CREATE TABLE users (
    id          SERIAL PRIMARY KEY,
    username    VARCHAR(50)  UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,          -- bcrypt hash in real life
    role        VARCHAR(20)  NOT NULL DEFAULT 'viewer',
    department  VARCHAR(50)  NOT NULL,
    created_at  TIMESTAMPTZ  DEFAULT now()
);

-- ── 2. Access Control Lists ──────────────────────────────────
CREATE TABLE acl_rules (
    id              SERIAL PRIMARY KEY,
    role            VARCHAR(20) NOT NULL,
    resource        VARCHAR(50) NOT NULL,       -- table or entity name
    allowed_actions TEXT[]      NOT NULL,        -- {'read','write','delete'}
    attribute_filter JSONB      DEFAULT '{}'     -- ABAC conditions
);

-- ── 3. Business Data – Documents ─────────────────────────────
CREATE TABLE documents (
    id              SERIAL PRIMARY KEY,
    title           VARCHAR(200)  NOT NULL,
    content         TEXT          NOT NULL,
    classification  VARCHAR(20)   NOT NULL DEFAULT 'internal',  -- public/internal/confidential
    department      VARCHAR(50)   NOT NULL,
    owner_id        INTEGER       REFERENCES users(id),
    created_at      TIMESTAMPTZ   DEFAULT now(),
    updated_at      TIMESTAMPTZ   DEFAULT now()
);

-- ── 4. Audit Log ─────────────────────────────────────────────
CREATE TABLE audit_log (
    id          SERIAL PRIMARY KEY,
    user_id     INTEGER       REFERENCES users(id),
    action      VARCHAR(20)   NOT NULL,
    resource    VARCHAR(50)   NOT NULL,
    details     JSONB         DEFAULT '{}',
    created_at  TIMESTAMPTZ   DEFAULT now()
);

-- ── 5. Seed Users ────────────────────────────────────────────
INSERT INTO users (username, password, role, department) VALUES
    ('alice',   'password123', 'admin',   'engineering'),
    ('bob',     'password123', 'editor',  'marketing'),
    ('charlie', 'password123', 'viewer',  'engineering'),
    ('diana',   'password123', 'viewer',  'marketing'),
    ('eve',     'password123', 'auditor', 'compliance');

-- ── 6. Seed ACL Rules ────────────────────────────────────────
-- Admin: full access, no attribute restrictions
INSERT INTO acl_rules (role, resource, allowed_actions, attribute_filter) VALUES
    ('admin',   'documents', '{read,write,delete}', '{}'),
    ('editor',  'documents', '{read,write}',        '{"same_department": true}'),
    ('viewer',  'documents', '{read}',              '{"same_department": true, "max_classification": "internal"}'),
    ('auditor', 'documents', '{read}',              '{"audit_only": true}');

-- ── 7. Seed Documents ────────────────────────────────────────
INSERT INTO documents (title, content, classification, department, owner_id) VALUES
    ('Q4 Engineering Roadmap',     'Detailed plans for Q4 sprint cycles and milestones.',           'internal',      'engineering', 1),
    ('Marketing Budget 2025',      'Annual marketing budget allocation and campaign forecasts.',     'confidential',  'marketing',   2),
    ('API Design Guidelines',      'Best practices for RESTful API design across all teams.',        'public',        'engineering', 1),
    ('Brand Style Guide v3',       'Updated brand colours, typography, and tone-of-voice rules.',    'internal',      'marketing',   2),
    ('Incident Response Playbook', 'Step-by-step procedures for P1/P2 security incidents.',          'confidential',  'engineering', 1),
    ('Social Media Calendar',      'Planned posts and campaigns for Q1 across all platforms.',       'internal',      'marketing',   2),
    ('Compliance Audit Report',    'Annual compliance audit findings and remediation steps.',         'confidential',  'compliance',  5),
    ('Onboarding Handbook',        'New-hire onboarding checklist and resource links.',              'public',        'engineering', 3),
    ('Competitive Analysis',       'Market positioning analysis against top three competitors.',      'confidential',  'marketing',   2),
    ('Infrastructure Cost Report', 'Monthly cloud-spend breakdown by service and environment.',      'internal',      'engineering', 1);

-- ── 8. Row-Level Security (PostgreSQL-native RBAC) ───────────
--    This shows how a *real* DAL can leverage Postgres RLS
--    as an additional defense layer.

ALTER TABLE documents ENABLE ROW LEVEL SECURITY;

-- Policy: public docs visible to everyone
CREATE POLICY doc_public ON documents
    FOR SELECT
    USING (classification = 'public');

-- Policy: internal docs visible to same department
CREATE POLICY doc_internal ON documents
    FOR SELECT
    USING (
        classification = 'internal'
        -- In production the DAL sets `current_setting('app.department')`
    );

-- All policies are permissive (OR logic) so the DAL's
-- query-rewriting layer is the primary enforcement point.
