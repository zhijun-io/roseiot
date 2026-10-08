-- Stage 0 schema baseline; business tables begin in later phases.

CREATE TABLE roseiot_schema_baseline (
    id SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO roseiot_schema_baseline (id) VALUES (1);
