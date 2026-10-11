CREATE INDEX users_tenant_id_id_idx
    ON users (tenant_id, id);

CREATE INDEX role_tenant_id_id_idx
    ON "role" (tenant_id, id);

CREATE INDEX user_role_user_id_tenant_id_idx
    ON user_role (user_id, tenant_id);

CREATE INDEX user_role_role_id_tenant_id_idx
    ON user_role (role_id, tenant_id);
