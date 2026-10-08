-- 早期 V5 若已写入 platform.* 权限键，重命名为 identity.*（与 identity 能力域一致）。

UPDATE permission
SET code = REPLACE(code, 'platform.', 'identity.')
WHERE code LIKE 'platform.%';
