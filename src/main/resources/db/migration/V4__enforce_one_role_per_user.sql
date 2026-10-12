CREATE UNIQUE INDEX user_role_one_role_per_user_uq
    ON user_role (tenant_id, user_id);
