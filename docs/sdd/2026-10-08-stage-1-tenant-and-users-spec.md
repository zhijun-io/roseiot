# 阶段 1 · Spec #3：租户与租户用户

> 状态：待批准 / 未实现

## Goal

系统管理员管理租户生命周期；租户管理员邀请和管理本租户用户；租户用户在自身租户边界内工作。

## Contract

- 系统侧：`POST /api/tenants`、`PATCH /api/tenants/{tenantId}`，要求 `ctx=system` 和 `tenant.*` permission。
- 租户侧：`GET|PATCH /api/tenants/users/me`；管理员可管理 `/api/tenants/users/**` 与 `/api/tenants/user-invitations/**`。
- 匿名接受邀请：`POST /api/auth/user-invitation-acceptances`。
- 租户上下文从 JWT 推导；客户端不得传入或覆盖当前租户 ID。
- `TenantUser` 不具有 `tenant.user.invite` 或 `tenant.user.manage`；不得停用或降级最后一名有效 `TenantAdmin`。
- `tenant` 与 `tenant_user_invitation` 是本 Spec 的新持久化模型；不创建 `member` 表。

## Non-goals

产品、设备、租户删除、自定义角色、OAuth 和多租户归属均不在本 Spec 范围。

## Acceptance

- 租户 CRUD、邀请接受、跨租户拒绝和最后管理员保护均有集成测试。
- 系统与租户 JWT 不可越过对方路径边界。
- `./mvnw -q verify` 通过。
