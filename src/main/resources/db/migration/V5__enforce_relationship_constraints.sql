ALTER TABLE users
    ADD CONSTRAINT users_tenant_id_id_uq UNIQUE (tenant_id, id),
    ADD CONSTRAINT users_tenant_fk FOREIGN KEY (tenant_id)
        REFERENCES tenant (id) ON DELETE RESTRICT;

ALTER TABLE user_credential
    ADD CONSTRAINT user_credential_user_fk FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT;

ALTER TABLE resource
    ADD CONSTRAINT resource_tenant_id_id_uq UNIQUE (tenant_id, id),
    ADD CONSTRAINT resource_tenant_fk FOREIGN KEY (tenant_id)
        REFERENCES tenant (id) ON DELETE RESTRICT,
    ADD CONSTRAINT resource_parent_fk FOREIGN KEY (tenant_id, parent_id)
        REFERENCES resource (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE "role"
    ADD CONSTRAINT role_tenant_id_id_uq UNIQUE (tenant_id, id),
    ADD CONSTRAINT role_tenant_fk FOREIGN KEY (tenant_id)
        REFERENCES tenant (id) ON DELETE RESTRICT;

ALTER TABLE user_role
    ADD CONSTRAINT user_role_user_fk FOREIGN KEY (tenant_id, user_id)
        REFERENCES users (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT user_role_role_fk FOREIGN KEY (tenant_id, role_id)
        REFERENCES "role" (tenant_id, id) ON DELETE RESTRICT;

ALTER TABLE role_resource
    ADD CONSTRAINT role_resource_role_fk FOREIGN KEY (tenant_id, role_id)
        REFERENCES "role" (tenant_id, id) ON DELETE RESTRICT,
    ADD CONSTRAINT role_resource_resource_fk FOREIGN KEY (resource_id)
        REFERENCES resource (id) ON DELETE RESTRICT;

DROP INDEX users_tenant_id_id_idx;
DROP INDEX role_tenant_id_id_idx;

CREATE INDEX role_resource_resource_id_idx
    ON role_resource (resource_id);
