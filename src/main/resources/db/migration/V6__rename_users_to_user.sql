ALTER TABLE users RENAME TO "user";

ALTER INDEX users_email_uq RENAME TO user_email_uq;

ALTER TABLE "user" RENAME CONSTRAINT users_tenant_id_id_uq TO user_tenant_id_id_uq;
ALTER TABLE "user" RENAME CONSTRAINT users_tenant_fk TO user_tenant_fk;
