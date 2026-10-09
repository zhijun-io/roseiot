# 阶段 1 · Identity 与 RBAC Spec

> 状态：已交付

## Data Model

- 人类主体统一存于 `users`。`tenant_id IS NULL` 为系统用户，否则为租户用户；邮箱全局唯一。
- 会话与密码重置请求分别存于 `user_refresh_session`、`password_reset_request`（实体 `PasswordResetRequest`），均以 `user_id` 关联主体。
- RBAC 表为 `permission`、`role`、`role_permission`、`user_role`。
- 内置角色为 `SystemAdmin`（`SYSTEM`）、`TenantAdmin`（`TENANT`）和 `TenantUser`（`TENANT`）。
- `SystemAdmin` 拥有 `user.profile.*` 与 `tenant.*` 系统管理权限，不拥有设备数据或设备凭证读取权限。

## Constraints

- 当前 Flyway 基线仅支持空数据库；已存在的数据库必须清空重建。
- 所有公开请求默认拒绝；资源授权在应用边界执行。
- **`/api/users/me/**`**：系统用户（`ctx=system`）已交付；租户用户（`ctx=tenant`）在 Spec #3 会话切片交付后共用同一路径（**不**提供 `/api/tenants/users/me`）。`ctx=system` 不得访问租户管理员 API（如 `/api/tenants/users/**` 列管他人）。

## Verification

- 空库应用 Flyway 后，当前表和 RBAC seed 均存在。
- bootstrap 用户拥有 `SystemAdmin` 绑定。
- 系统用户 JWT 含正确的 `ctx` 与 `azv`；`ctx=tenant` 且 `tid` 与 `users.tenant_id` 一致时可访问 `/api/users/me/**`（需 `user.profile.*`）。
- `./mvnw -q verify` 通过。

## Revision log

- 2026-10-09 | 租户 self profile 不再使用 `/api/tenants/users/me`，与 Spec #3 对齐为 `/api/users/me/**` | Verification | yes — Spec #3 Plan Slice 4
