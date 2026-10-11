INSERT INTO resource (tenant_id, code, name, type, parent_id, sort_order)
SELECT 1, action.code, action.name, 'ACTION', parent.id, action.sort_order
FROM (VALUES
    ('tenant.read', 'View tenants', 'menu.management', 107),
    ('system.setting.manage', 'Manage system settings', 'menu.management', 108)
) AS action(code, name, parent_code, sort_order)
JOIN resource parent
    ON parent.tenant_id = 1
    AND parent.code = action.parent_code
WHERE NOT EXISTS (
    SELECT 1
    FROM resource existing
    WHERE existing.tenant_id = 1
      AND existing.code = action.code
);
