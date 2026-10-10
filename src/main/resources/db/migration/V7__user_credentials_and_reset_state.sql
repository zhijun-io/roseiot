ALTER TABLE users
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE';

UPDATE users
SET status = CASE WHEN disabled_at IS NULL THEN 'ACTIVE' ELSE 'DISABLED' END;

ALTER TABLE users
    ADD CONSTRAINT users_status_ck CHECK (status IN ('PENDING_ACTIVATION', 'ACTIVE', 'DISABLED')),
    DROP COLUMN disabled_at;

CREATE TABLE user_credential (
    user_id UUID PRIMARY KEY REFERENCES users (id),
    password_hash VARCHAR(255) NOT NULL,
    reset_token_hash CHAR(64),
    reset_expires_at TIMESTAMPTZ,
    reset_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT user_credential_reset_state_ck CHECK (
        (reset_token_hash IS NULL AND reset_expires_at IS NULL AND reset_at IS NULL)
        OR (reset_token_hash IS NOT NULL AND reset_expires_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX user_credential_reset_token_hash_uq
    ON user_credential (reset_token_hash)
    WHERE reset_token_hash IS NOT NULL;

INSERT INTO user_credential (
    user_id,
    password_hash,
    reset_token_hash,
    reset_expires_at,
    reset_at,
    created_at,
    updated_at
)
SELECT u.id,
       u.password_hash,
       latest.token_hash,
       latest.expires_at,
       latest.used_at,
       u.created_at,
       now()
FROM users u
LEFT JOIN LATERAL (
    SELECT p.token_hash, p.expires_at, p.used_at, p.created_at
    FROM password_reset_request p
    WHERE p.user_id = u.id
    ORDER BY p.created_at DESC, p.id DESC
    LIMIT 1
) latest ON TRUE;

DROP TABLE password_reset_request;
ALTER TABLE users DROP COLUMN password_hash;
ALTER TABLE users DROP COLUMN password_algorithm;
