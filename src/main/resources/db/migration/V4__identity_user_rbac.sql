-- Identity: users 表与 RBAC 表结构；自 platform_admin 迁移行数据；重命名会话/恢复表。

CREATE TABLE users (
    id UUID PRIMARY KEY,
    tenant_id UUID NULL,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_algorithm VARCHAR(32) NOT NULL,
    authorization_version INT NOT NULL DEFAULT 1,
    disabled_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX users_email_uq ON users (email);

CREATE TABLE permission (
    id UUID PRIMARY KEY,
    code VARCHAR(128) NOT NULL
);

CREATE UNIQUE INDEX permission_code_uq ON permission (code);

CREATE TABLE "role" (
    id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL,
    scope VARCHAR(16) NOT NULL
);

CREATE UNIQUE INDEX role_code_uq ON "role" (code);

CREATE TABLE role_permission (
    role_id UUID NOT NULL,
    permission_id UUID NOT NULL,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE user_role (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    scope_tenant_id UUID NULL
);

CREATE INDEX user_role_user_id_idx ON user_role (user_id);

INSERT INTO users (id, tenant_id, email, password_hash, password_algorithm, authorization_version, disabled_at, created_at)
SELECT id, NULL, email, password_hash, password_algorithm, 1, NULL, created_at
FROM platform_admin;

ALTER TABLE platform_admin_refresh_session RENAME TO user_refresh_session;
ALTER TABLE user_refresh_session RENAME COLUMN platform_admin_id TO user_id;

ALTER INDEX platform_admin_refresh_session_token_hash_uq RENAME TO user_refresh_session_token_hash_uq;
ALTER INDEX platform_admin_refresh_session_admin_id_idx RENAME TO user_refresh_session_user_id_idx;

ALTER TABLE platform_admin_recovery_request RENAME TO user_recovery_request;
ALTER TABLE user_recovery_request RENAME COLUMN platform_admin_id TO user_id;

ALTER INDEX platform_admin_recovery_request_token_hash_uq RENAME TO user_recovery_request_token_hash_uq;

ALTER TABLE user_refresh_session DROP CONSTRAINT IF EXISTS platform_admin_refresh_session_platform_admin_id_fkey;
ALTER TABLE user_recovery_request DROP CONSTRAINT IF EXISTS platform_admin_recovery_request_platform_admin_id_fkey;

DROP TABLE platform_admin;
