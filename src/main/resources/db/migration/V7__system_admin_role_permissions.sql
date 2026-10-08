-- 术语迁移：保留 V5/V6 校验和，迁移既有 RBAC-0 数据到 system/user 命名。
-- role 与 permission 的 ID 不变，因此既有 role_permission 与 user_role 绑定会随之保留。

UPDATE permission
SET code = CASE code
    WHEN 'identity.profile.read' THEN 'user.profile.read'
    WHEN 'identity.profile.password.write' THEN 'user.password.write'
    WHEN 'identity.tenant.create' THEN 'tenant.create'
    WHEN 'identity.tenant.disable' THEN 'tenant.disable'
    WHEN 'identity.tenant.enable' THEN 'tenant.enable'
    WHEN 'tenant.member.invite' THEN 'tenant.user.invite'
    WHEN 'tenant.member.manage' THEN 'tenant.user.manage'
    WHEN 'device.credentials.read' THEN 'device.credential.read'
END
WHERE code IN (
    'identity.profile.read',
    'identity.profile.password.write',
    'identity.tenant.create',
    'identity.tenant.disable',
    'identity.tenant.enable',
    'tenant.member.invite',
    'tenant.member.manage',
    'device.credentials.read');

UPDATE "role"
SET code = CASE code
    WHEN 'PlatformOperator' THEN 'SystemAdmin'
    WHEN 'TenantMember' THEN 'TenantUser'
END,
    scope = CASE scope
        WHEN 'PLATFORM' THEN 'SYSTEM'
        ELSE scope
    END
WHERE code IN ('PlatformOperator', 'TenantMember') OR scope = 'PLATFORM';
