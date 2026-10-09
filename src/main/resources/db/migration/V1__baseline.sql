CREATE EXTENSION IF NOT EXISTS pgcrypto;

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

CREATE TABLE user_refresh_session (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX user_refresh_session_token_hash_uq ON user_refresh_session (token_hash);
CREATE INDEX user_refresh_session_user_id_idx ON user_refresh_session (user_id);

CREATE TABLE user_recovery_request (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX user_recovery_request_token_hash_uq ON user_recovery_request (token_hash);

CREATE TABLE tenant (
    id UUID PRIMARY KEY,
    slug VARCHAR(63) NOT NULL,
    name VARCHAR(255) NOT NULL,
    disabled_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tenant_slug_format_ck CHECK (slug ~ '^[a-z0-9]([a-z0-9-]{1,61}[a-z0-9])$')
);

CREATE UNIQUE INDEX tenant_slug_uq ON tenant (slug);

CREATE TABLE tenant_user_invitation (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    email VARCHAR(320) NOT NULL,
    target_role VARCHAR(16) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    accepted_at TIMESTAMPTZ NULL,
    revoked_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT tenant_user_invitation_tenant_id_fk FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT tenant_user_invitation_target_role_ck CHECK (target_role IN ('TenantAdmin', 'TenantUser'))
);

CREATE UNIQUE INDEX tenant_user_invitation_token_hash_uq ON tenant_user_invitation (token_hash);
CREATE UNIQUE INDEX tenant_user_invitation_pending_uq
    ON tenant_user_invitation (tenant_id, email)
    WHERE accepted_at IS NULL AND revoked_at IS NULL;
CREATE INDEX tenant_user_invitation_tenant_id_idx ON tenant_user_invitation (tenant_id);

INSERT INTO permission (id, code) VALUES
    (gen_random_uuid(), 'user.profile.read'),
    (gen_random_uuid(), 'user.password.write'),
    (gen_random_uuid(), 'tenant.create'),
    (gen_random_uuid(), 'tenant.disable'),
    (gen_random_uuid(), 'tenant.enable'),
    (gen_random_uuid(), 'tenant.user.invite'),
    (gen_random_uuid(), 'tenant.user.manage'),
    (gen_random_uuid(), 'device.read'),
    (gen_random_uuid(), 'device.credential.read'),
    (gen_random_uuid(), 'device.command.issue'),
    (gen_random_uuid(), 'alarm.ack');

INSERT INTO "role" (id, code, scope) VALUES
    (gen_random_uuid(), 'SystemAdmin', 'SYSTEM'),
    (gen_random_uuid(), 'TenantAdmin', 'TENANT'),
    (gen_random_uuid(), 'TenantUser', 'TENANT');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM "role" r
JOIN permission p ON p.code IN (
    'user.profile.read',
    'user.password.write',
    'tenant.create',
    'tenant.disable',
    'tenant.enable')
WHERE r.code = 'SystemAdmin';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM "role" r
JOIN permission p ON p.code IN (
    'tenant.user.invite',
    'tenant.user.manage',
    'device.read',
    'device.credential.read',
    'device.command.issue',
    'alarm.ack')
WHERE r.code = 'TenantAdmin';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM "role" r
JOIN permission p ON p.code IN ('device.read', 'device.command.issue', 'alarm.ack')
WHERE r.code = 'TenantUser';
