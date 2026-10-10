-- Identity model rebuild: tenant-owned roles, shared resource catalogue, deployment mode record.
-- Initialization data (default tenant, catalogue, built-in roles) is created by the bootstrap CLI, not here.

DROP TABLE user_role;
DROP TABLE role_permission;
DROP TABLE permission;
DROP TABLE "role";

ALTER TABLE users ALTER COLUMN tenant_id SET NOT NULL;
ALTER TABLE users ADD CONSTRAINT users_tenant_fk FOREIGN KEY (tenant_id) REFERENCES tenant (id);
ALTER TABLE users ADD CONSTRAINT users_tenant_id_uq UNIQUE (tenant_id, id);

CREATE TABLE resource (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant (id),
    code VARCHAR(128) NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(16) NOT NULL CHECK (type IN ('MENU', 'ACTION')),
    parent_id UUID REFERENCES resource (id),
    scope VARCHAR(16) CHECK (scope IN ('PLATFORM', 'TENANT', 'BOTH')),
    active BOOLEAN NOT NULL DEFAULT true,
    route VARCHAR(255),
    icon VARCHAR(128),
    sort_order INTEGER NOT NULL DEFAULT 0,
    CONSTRAINT resource_code_uq UNIQUE (code),
    CONSTRAINT resource_tree_ck CHECK (parent_id IS DISTINCT FROM id),
    CONSTRAINT resource_shape_ck CHECK (
        (type = 'ACTION' AND scope IS NOT NULL AND route IS NULL) OR (type = 'MENU' AND scope IS NULL))
);

CREATE TABLE "role" (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL REFERENCES tenant (id),
    code VARCHAR(64) NOT NULL CHECK (code ~ '^[A-Z][A-Z0-9]*(_[A-Z0-9]+)*$'),
    name VARCHAR(255) NOT NULL,
    built_in BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT role_tenant_code_uq UNIQUE (tenant_id, code),
    CONSTRAINT role_tenant_id_uq UNIQUE (tenant_id, id)
);

CREATE TABLE user_role (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role_id UUID NOT NULL,
    CONSTRAINT user_role_assignment_uq UNIQUE (user_id, role_id),
    FOREIGN KEY (tenant_id, user_id) REFERENCES users (tenant_id, id),
    FOREIGN KEY (tenant_id, role_id) REFERENCES "role" (tenant_id, id)
);

CREATE TABLE role_resource (
    tenant_id UUID NOT NULL,
    role_id UUID NOT NULL,
    resource_id UUID NOT NULL REFERENCES resource (id),
    PRIMARY KEY (role_id, resource_id),
    FOREIGN KEY (tenant_id, role_id) REFERENCES "role" (tenant_id, id)
);

CREATE TABLE platform_configuration (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    tenant_mode VARCHAR(16) NOT NULL CHECK (tenant_mode IN ('SINGLE_TENANT', 'MULTI_TENANT')),
    default_tenant_id UUID NOT NULL REFERENCES tenant (id),
    initialized_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

ALTER TABLE tenant_user_invitation ADD COLUMN role_id UUID NOT NULL;
ALTER TABLE tenant_user_invitation DROP COLUMN target_role;
ALTER TABLE tenant_user_invitation ADD CONSTRAINT invitation_role_fk
    FOREIGN KEY (tenant_id, role_id) REFERENCES "role" (tenant_id, id);
