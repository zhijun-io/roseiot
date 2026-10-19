-- Run through psql with variables: email, display_name, and password_hash.
-- The caller must provide an encoded password hash; plaintext passwords are rejected.
BEGIN;

-- The separate statement boundary makes the later guard see a preceding transaction's commit.
SELECT pg_advisory_xact_lock(1380930927, 1);

DO $bootstrap$
DECLARE
    system_admin_role_id BIGINT;
BEGIN
    SELECT id
    INTO system_admin_role_id
    FROM roles
    WHERE tenant_id = (SELECT id FROM tenants WHERE code = 'default')
      AND code = 'SYSTEM_ADMIN'
      AND protected
    FOR UPDATE;

    IF system_admin_role_id IS NULL THEN
        RAISE EXCEPTION 'Protected SYSTEM_ADMIN role is not initialized';
    END IF;

    IF EXISTS (
        SELECT 1
        FROM user_role
        WHERE role_id = system_admin_role_id
    ) THEN
        RAISE EXCEPTION 'Initial system administrator has already been created';
    END IF;
END
$bootstrap$;

CREATE TEMPORARY TABLE bootstrap_created_user (id BIGINT NOT NULL) ON COMMIT DROP;

WITH created_user AS (
    INSERT INTO users (tenant_id, email, display_name, status)
    SELECT tenant.id, :'email', :'display_name', 'ACTIVE'
    FROM tenants AS tenant
    WHERE tenant.code = 'default'
    RETURNING id
)
INSERT INTO bootstrap_created_user (id)
SELECT id FROM created_user;

INSERT INTO user_credentials (user_id, password_hash, password_change_required)
SELECT id, :'password_hash', TRUE
FROM bootstrap_created_user;

INSERT INTO user_role (user_id, role_id)
SELECT created_user.id, role.id
FROM bootstrap_created_user AS created_user
CROSS JOIN roles AS role
JOIN tenants AS tenant ON tenant.id = role.tenant_id
WHERE tenant.code = 'default'
  AND role.code = 'SYSTEM_ADMIN'
  AND role.protected;

INSERT INTO audit_records (
    tenant_id,
    actor_user_id,
    action,
    target_type,
    target_id,
    outcome
)
SELECT user_account.tenant_id,
       user_account.id,
       'bootstrap.system-admin.initialize',
       'User',
       user_account.id::TEXT,
       'SUCCESS'
FROM users AS user_account
JOIN bootstrap_created_user AS created_user ON created_user.id = user_account.id;

COMMIT;
