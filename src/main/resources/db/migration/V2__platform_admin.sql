CREATE TABLE platform_admin (
    id UUID PRIMARY KEY,
    email VARCHAR(320) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    password_algorithm VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX platform_admin_email_uq ON platform_admin (email);
