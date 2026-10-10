ALTER TABLE users
    ADD COLUMN name VARCHAR(255),
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT now();

ALTER TABLE user_credential
    RENAME COLUMN password_hash TO password;

ALTER TABLE user_credential
    RENAME COLUMN reset_token_hash TO reset_token;

ALTER INDEX user_credential_reset_token_hash_uq
    RENAME TO user_credential_reset_token_uq;
