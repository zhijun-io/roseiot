-- The platform always has one reserved tenant. Users are created separately by a future
-- first-administrator initialization flow.
INSERT INTO tenant (id, slug, name)
VALUES (gen_random_uuid(), 'default', 'Default');

WITH catalogue(code, name, type, parent_code, scope, route, sort_order) AS (
    VALUES
        ('menu.management', 'Management', 'MENU', NULL, NULL, 'management', 10),
        ('menu.devices', 'Devices', 'MENU', NULL, NULL, 'devices', 20),
        ('menu.alarms', 'Alarms', 'MENU', NULL, NULL, 'alarms', 30),
        ('tenant.user.manage', 'Manage members', 'ACTION', 'menu.management', 'TENANT', NULL, 101),
        ('tenant.user.invite', 'Invite members', 'ACTION', 'menu.management', 'TENANT', NULL, 102),
        ('tenant.role.manage', 'Manage roles', 'ACTION', 'menu.management', 'TENANT', NULL, 103),
        ('tenant.create', 'Create tenants', 'ACTION', 'menu.management', 'PLATFORM', NULL, 104),
        ('tenant.disable', 'Disable tenants', 'ACTION', 'menu.management', 'PLATFORM', NULL, 105),
        ('tenant.enable', 'Enable tenants', 'ACTION', 'menu.management', 'PLATFORM', NULL, 106),
        ('device.read', 'View devices', 'ACTION', 'menu.devices', 'TENANT', NULL, 201),
        ('device.command.issue', 'Issue device commands', 'ACTION', 'menu.devices', 'TENANT', NULL, 202),
        ('device.credential.read', 'Read device credentials', 'ACTION', 'menu.devices', 'TENANT', NULL, 203),
        ('alarm.ack', 'Acknowledge alarms', 'ACTION', 'menu.alarms', 'TENANT', NULL, 301)
)
INSERT INTO resource (id, tenant_id, code, name, type, parent_id, scope, active, route, sort_order)
SELECT gen_random_uuid(), t.id, c.code, c.name, c.type, p.id, c.scope, true, c.route, c.sort_order
FROM catalogue c
CROSS JOIN tenant t
LEFT JOIN resource p ON p.tenant_id = t.id AND p.code = c.parent_code
WHERE t.slug = 'default';

INSERT INTO "role" (id, tenant_id, code, name, built_in)
SELECT gen_random_uuid(), t.id, r.code, r.name, true
FROM tenant t
CROSS JOIN (VALUES
    ('TENANT_ADMIN', 'Administrator'),
    ('DEVICE_OPERATOR', 'Operator'),
    ('DEVICE_VIEWER', 'Viewer')
) AS r(code, name)
WHERE t.slug = 'default';

INSERT INTO role_resource (tenant_id, role_id, resource_id)
SELECT r.tenant_id, r.id, a.id
FROM "role" r
JOIN tenant t ON t.id = r.tenant_id AND t.slug = 'default'
JOIN (VALUES
    ('TENANT_ADMIN', 'tenant.create'),
    ('TENANT_ADMIN', 'tenant.disable'),
    ('TENANT_ADMIN', 'tenant.enable'),
    ('DEVICE_OPERATOR', 'device.read'),
    ('DEVICE_OPERATOR', 'device.command.issue'),
    ('DEVICE_OPERATOR', 'alarm.ack'),
    ('DEVICE_VIEWER', 'device.read')
) AS granted(role_code, action_code) ON granted.role_code = r.code
JOIN resource a ON a.tenant_id = t.id AND a.code = granted.action_code AND a.type = 'ACTION';

DROP TABLE platform_configuration;
