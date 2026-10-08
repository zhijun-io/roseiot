CREATE TABLE platform_admin_refresh_session (
    id UUID PRIMARY KEY,
    platform_admin_id UUID NOT NULL REFERENCES platform_admin (id),
    family_id UUID NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX platform_admin_refresh_session_token_hash_uq
    ON platform_admin_refresh_session (token_hash);

CREATE INDEX platform_admin_refresh_session_admin_id_idx
    ON platform_admin_refresh_session (platform_admin_id);

CREATE TABLE platform_admin_recovery_request (
    id UUID PRIMARY KEY,
    platform_admin_id UUID NOT NULL REFERENCES platform_admin (id),
    token_hash CHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX platform_admin_recovery_request_token_hash_uq
    ON platform_admin_recovery_request (token_hash);
