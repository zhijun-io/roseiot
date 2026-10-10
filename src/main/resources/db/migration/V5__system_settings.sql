CREATE TABLE system_setting (
    id UUID PRIMARY KEY,
    type VARCHAR(16) NOT NULL UNIQUE CHECK (type IN ('SETUP', 'GENERAL')),
    setting JSONB NOT NULL CHECK (jsonb_typeof(setting) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
