-- RBAC-0 内置目录（附录 A）；非测试夹具。升级库中为已迁移平台用户补 PlatformOperator 绑定。

INSERT INTO permission (id, code) VALUES
    (gen_random_uuid(), 'identity.profile.read'),
    (gen_random_uuid(), 'identity.profile.password.write'),
    (gen_random_uuid(), 'identity.tenant.create'),
    (gen_random_uuid(), 'identity.tenant.disable'),
    (gen_random_uuid(), 'identity.tenant.enable'),
    (gen_random_uuid(), 'tenant.member.invite'),
    (gen_random_uuid(), 'tenant.member.manage'),
    (gen_random_uuid(), 'device.read'),
    (gen_random_uuid(), 'device.credentials.read'),
    (gen_random_uuid(), 'device.command.issue'),
    (gen_random_uuid(), 'alarm.ack');

INSERT INTO "role" (id, code, scope) VALUES
    (gen_random_uuid(), 'PlatformOperator', 'PLATFORM'),
    (gen_random_uuid(), 'TenantAdmin', 'TENANT'),
    (gen_random_uuid(), 'TenantMember', 'TENANT');

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM "role" r
JOIN permission p ON p.code LIKE 'identity.%'
WHERE r.code = 'PlatformOperator';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM "role" r
JOIN permission p ON p.code IN (
    'tenant.member.invite', 'tenant.member.manage',
    'device.read', 'device.credentials.read', 'device.command.issue', 'alarm.ack')
WHERE r.code = 'TenantAdmin';

INSERT INTO role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM "role" r
JOIN permission p ON p.code IN ('device.read', 'device.command.issue', 'alarm.ack')
WHERE r.code = 'TenantMember';

INSERT INTO user_role (id, user_id, role_id, scope_tenant_id)
SELECT gen_random_uuid(), u.id, r.id, NULL
FROM users u
JOIN "role" r ON r.code = 'PlatformOperator'
WHERE u.tenant_id IS NULL
  AND NOT EXISTS (
      SELECT 1 FROM user_role ur WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
