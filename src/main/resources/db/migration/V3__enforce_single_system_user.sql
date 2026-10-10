CREATE UNIQUE INDEX users_single_system_user_uq
    ON users ((tenant_id IS NULL))
    WHERE tenant_id IS NULL;
