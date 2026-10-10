# 阶段 1 · Spec #3：租户与租户用户

> 状态：已交付（Spec #3 本阶段范围）

## Goal

系统管理员管理租户生命周期；租户管理员邀请和管理本租户用户；租户用户在自身租户边界内工作。

## HTTP Contract

### 系统侧（`ctx=system`）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/tenants` | 创建租户；body `{name, slug, initialAdminEmail}`；**201** `{id,name,slug}`；原子写入首位 `TenantAdmin` 待处理邀请（不经下列 invitations API） |
| PATCH | `/api/tenants/{tenantId}` | 仅 `name`、`disabled` |

### 租户侧（`ctx=tenant`；租户 ID 来自 JWT `tid`，不得由客户端指定）

| 方法 | 路径 | Permission | 说明 |
| --- | --- | --- | --- |
| GET | `/api/tenants/users` | `tenant.user.manage` | **200** 用户列表 |
| PATCH | `/api/tenants/users/{userId}` | `tenant.user.manage` | 角色/停用；**204** |
| GET | `/api/tenants/invitations` | `tenant.user.invite` 或 `tenant.user.manage` | **200** 邀请列表（默认仅 `pending`；可选 `?status=pending\|accepted\|revoked\|expired`） |
| POST | `/api/tenants/invitations` | `tenant.user.invite` | 创建邀请；body `{email, targetRole}`（`TenantAdmin` \| `TenantUser`）；**201** + `Location: …/invitations/{id}`；响应 `{id, email, targetRole, expiresAt, createdAt}`，**不含** token |
| GET | `/api/tenants/invitations/{invitationId}` | 同上 | **200** 单条邀请元数据（**不含** token） |
| DELETE | `/api/tenants/invitations/{invitationId}` | `tenant.user.invite` | 撤销待处理邀请；**204** |

租户用户读 profile、改密：**无** `/api/tenants/users/me`；共用 **`/api/users/me/**`**（见 Identity / 系统用户 Spec，Slice 4 交付 `ctx=tenant`）。

### 匿名（`/api/noauth/`；kebab-case；与密码重置同构）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/api/noauth/tenant-invitations?invitationToken=` | 邮件链接校验：**200** + `{ "status": "valid"|"expired"|"invalid" }` |
| POST | `/api/noauth/tenant-invitations` | 接受邀请；body `{invitationToken, password}`；成功 **201** `{accessToken, refreshToken}`（与 `/api/login` 同形）；无效/已用 token **400** + `invalid_invitation_token` |

路径约定：

- 租户内集合资源用 **复数名词** `invitations`、`users`；**不用** RPC 式 `…-acceptance`、**不用** `user-invitations`（`tenants` 前缀已表达租户范围）。
- noauth 侧用完整资源名 **`tenant-invitations`**（无 `/api/tenants/` 前缀时需消歧）。
- 子动作若未来需要（如重发邮件）使用 **`POST …/invitations/{id}/resend`**，首版不交付。

## Behavior

- 租户上下文从 JWT 推导；客户端不得传入或覆盖当前租户 ID。
- `TenantUser` 不具有 `tenant.user.invite` 或 `tenant.user.manage`；不得停用或降级最后一名有效 `TenantAdmin`。
- 同一租户的成员角色和启用状态变更必须串行检查最后管理员约束，避免并发操作同时通过检查并移除多名管理员。
- `tenant` 与 `tenant_user_invitation` 是本 Spec 的持久化模型；不创建 `member` 表。
- 邀请 token 有效期 **7 天**、单次使用、可 **DELETE** 撤销；同一 `(tenant_id, email)` 仅一条 **pending** 邀请（DB 约束）。响应、日志、通知 **不得** 含 token 明文；测试经 `TenantInvitationNotifier` 取得。
- 接受邀请创建 `users` + `user_role`，标记邀请终态，并签发 **`ctx=tenant` + `tid`** 会话。
- 租户停用后拒绝新 login 和 refresh；已签发 access JWT 不额外撤销。
- 未知或无权邀请/用户操作遵循不泄露存在性原则（与 `PRD-002` 一致；具体 status/code 在实现 Plan 中列明）。

## Non-goals

产品、设备、租户删除、自定义角色、OAuth 和多租户归属均不在本 Spec 范围。

## Acceptance

- **AC-1**：以 `{name,slug,initialAdminEmail}` 创建租户返回 `201 {id,name,slug}`，并原子写入首位 `TenantAdmin` 邀请；`POST/GET/DELETE …/invitations` 与 noauth 接受闭环有测试；跨租户拒绝和最后管理员保护有测试。
- **AC-2**：系统与租户 JWT 不可越过对方路径边界。
- **AC-3**：`./mvnw -q verify` 通过。

## Revision log

- 2026-10-08 | 恢复租户创建时原子生成首位 TenantAdmin 邀请的闭环契约 | AC-1 | yes — Plan 须先交付该闭环
- 2026-10-08 | 固定租户请求/响应、PATCH 字段、邀请时效与租户停用会话语义 | AC-1, AC-2 | no — 不改变 Plan 切片或验证命令
- 2026-10-09 | 取消 `/api/tenants/users/me`；租户 self profile/改密统一 `/api/users/me/**` | AC-2 | yes — Plan Slice 4 与 Identity/系统用户 Spec 边界同步修订
- 2026-10-09 | 邀请 REST  redesign：`/api/tenants/invitations` + `/api/noauth/tenant-invitations`（GET/POST）；废弃 `user-invitations` 与 `user-invitation-acceptance` | AC-1 | yes — Plan Slice 2/3 与 Q518
- 2026-10-10 | 明确并发成员变更下的最后管理员保护 | AC-1 | yes — 使用租户行锁串行化成员角色/启用状态变更
