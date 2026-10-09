# 阶段 1 · Spec #3：租户与租户用户

> 状态：已批准 / 未实现

## Goal

系统管理员管理租户生命周期；租户管理员邀请和管理本租户用户；租户用户在自身租户边界内工作。

## Contract

- 系统侧：`POST /api/tenants`、`PATCH /api/tenants/{tenantId}`，要求 `ctx=system` 和 `tenant.*` permission。创建 body 为 `{name, slug, initialAdminEmail}`；创建租户与写入目标角色为 `TenantAdmin` 的 pending 邀请在同一事务完成，响应为 `201 {id,name,slug}` 且不含邀请 token。PATCH 仅接受 `name` 与 `disabled`。
- 租户侧：`GET|PATCH /api/tenants/users/me`；管理员可管理 `/api/tenants/users/**` 与 `/api/tenants/user-invitations/**`。
- 匿名接受邀请：`POST /api/auth/user-invitation-acceptances`。
- 租户上下文从 JWT 推导；客户端不得传入或覆盖当前租户 ID。
- `TenantUser` 不具有 `tenant.user.invite` 或 `tenant.user.manage`；不得停用或降级最后一名有效 `TenantAdmin`。
- `tenant` 与 `tenant_user_invitation` 是本 Spec 的新持久化模型；不创建 `member` 表。
- 邀请 token 有效期为 7 天、只能使用一次且可撤销。自动化测试通过内存通知器取得 token，生产响应与日志不得返回 token 明文。
- 租户停用后拒绝新 login 和 refresh；已签发的 access JWT 不额外撤销，最多在其既定 TTL 内失效。

## Non-goals

产品、设备、租户删除、自定义角色、OAuth 和多租户归属均不在本 Spec 范围。

## Acceptance

- **AC-1**：以 `{name,slug,initialAdminEmail}` 创建租户返回 `201 {id,name,slug}`，并原子写入首位 `TenantAdmin` 邀请；仅允许 PATCH `name` 与 `disabled`。邀请接受、跨租户拒绝和最后管理员保护均有集成测试。
- **AC-2**：系统与租户 JWT 不可越过对方路径边界。
- **AC-3**：`./mvnw -q verify` 通过。

## Revision log

- 2026-10-08 | 恢复租户创建时原子生成首位 TenantAdmin 邀请的闭环契约 | AC-1 | yes — Plan 须先交付该闭环
- 2026-10-08 | 固定租户请求/响应、PATCH 字段、邀请时效与租户停用会话语义 | AC-1, AC-2 | no — 不改变 Plan 切片或验证命令
