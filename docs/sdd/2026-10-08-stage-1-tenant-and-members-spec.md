# 阶段 1 · Spec #3：租户生命周期、租户用户邀请与会话

> 状态：待批准  
> 更新日期：2026-10-08  
> 位置：身份迁移 Spec 交付之后；产品/设备（Spec #4～#5）之前  

## Goal

系统管理员可创建、停用与恢复租户；租户管理员可邀请与管理**租户用户**；租户用户通过 **`/api/auth`** 获得 `ctx=tenant` 令牌，在 **`/api/tenants`** 下以 **RESTful** 资源（**`users`**、**`user-invitations`**）完成 profile 与管理；对外路径与文档统一使用 **user**，不再使用 **member** 作为 HTTP 或本 Spec 资源名（内置角色名 **`TenantUser`** 仍为 RBAC 产品词汇）。

## Scope

- **IAM-002**～**IAM-012** 租户相关子集（同前 Spec 意图）；**IAM-005**～**IAM-006** 表述改为**租户用户**邀请与停用。
- **IAM-011**：租户 REST 前缀 **`/api/tenants`**；租户上下文路由**不含**客户端 `tenantId`（**Q377**）；系统侧按 **`/api/tenants/{tenantId}`** 操作且须 **`tenant.*`**（**`ctx=system`**）。
- **REST**：复数资源名、标准 HTTP 方法；停用租户 **`PATCH /api/tenants/{tenantId}`**；**禁止** RPC 路径（`* /disable`、`* /accept` 作动词段）。
- **持久化**：`tenant`、`tenant_user_invitation`（或 Plan 写死的同义表名）；**不**建 `member` 表名。
- **RBAC**：V5 已有 **`tenant.user.*`**；本 Spec 在 **V7+** 将 permission 键**重命名**为 **`tenant.user.invite`**、**`tenant.user.manage`**（同 V6 重命名策略），并追加 **`tenant.profile.*`**（附录 A）。
- **包布局**：**`io.roseiot.tenant.*`**；HTTP **`/api/tenants/**`**；会话 **`/api/auth/**`**；系统 profile **`/api/users/me/**`**（已实现）。
- **README**：REST 资源表与 curl。

## Non-goals

（同前：无产品/设备、无租户 DELETE、无 OAuth、无自定义角色等。）

## Current Context

- **已交付**：**`/api/auth/**`**、**`/api/users/me/**`**（`ctx=system`）；RBAC seed 含 **`tenant.user.*`**（V7 将 rename）。
- **未实现**：`tenant` 表、**`/api/tenants/**`** HTTP。

## Requirements

1. **`tenant`** 实体：id、name、slug（唯一）、disabled_at、created_at；无 DELETE；状态用 PATCH。
2. **`POST /api/tenants`**（**`ctx=system`**）：创建租户 + 同一事务 **`POST` 语义**创建首条 **`tenant_user_invitation`**（目标角色 `TenantAdmin`）；响应不含令牌明文（**Q211**）。
3. **`PATCH /api/tenants/{tenantId}`**（**`ctx=system`**）：更新 name/status（含停用/启用）；停用后该租户用户 login/refresh 统一失败语义（**Q222**）。
4. **租户用户邀请**（`ctx=tenant` + **`tenant.user.invite`**）：
   - **`GET /api/tenants/user-invitations`** — 列 pending；
   - **`POST /api/tenants/user-invitations`** — 创建（body：email、roleCode：`TenantAdmin`|`TenantUser`）；
   - **`GET /api/tenants/user-invitations/{invitationId}`** — 单条 pending 详情（Admin）；
   - **`DELETE /api/tenants/user-invitations/{invitationId}`** — 撤销 pending。
5. **接受邀请**（匿名）：**`POST /api/auth/user-invitation-acceptances`**（body：token、newPassword）→ 创建 **`users(tenant_id=…)`** + **`user_role`**，邀请终态；失败语义统一（**Q213**）。
6. **租户用户管理**（`ctx=tenant` + **`tenant.user.manage`**）：
   - **`GET /api/tenants/users`** — 列本租户用户（含 role、disabled 等最小字段）；
   - **`GET /api/tenants/users/{userId}`** — 单用户（须属本租户；**PRD-002**）；
   - **`PATCH /api/tenants/users/{userId}`** — 更新 roleCode 和/或 disabled（停用）；**禁止**降级/停用最后一名有效 **`TenantAdmin`**（**Q219**）。
7. **当前租户用户（self）**（`ctx=tenant` + **`tenant.profile.*`**）：
   - **`GET /api/tenants/users/me`** — 当前用户 profile（**不**使用 `/api/tenants/me` 以避免与租户资源 id 混淆）；
   - **`PATCH /api/tenants/users/me/password`** — 改密（子资源 PATCH；改密仍撤销 refresh 族）。
8. **路径隔离**：**`ctx=system`** JWT → **`/api/users/me/**`**、**`/api/tenants`** 与 **`/api/tenants/{tenantId}`**（及系统侧发起的 **`/api/tenants/{tenantId}/user-invitations`**，Q220）；**不可** **`/api/tenants/users/**`**（租户上下文集合）。**`ctx=tenant`** JWT → **`/api/tenants/users/**`**、**`/api/tenants/user-invitations/**`**；**不可** **`POST /api/tenants`**、**不可** **`/api/users/me/**`**；共用 **`/api/auth/**`**。
9. 持有 **`TenantUser`** 角色者无 **`tenant.user.invite` / `tenant.user.manage`**；可有 **`tenant.profile.*`**。
10. （可选 **Q220**）系统侧对无有效 Admin 的租户：**`POST /api/tenants/{tenantId}/user-invitations`**（`ctx=system` + 强 permission）；可裁剪。

## Acceptance Criteria

- **AC-1**：`./mvnw -q verify` 通过。
- **AC-2**：Flyway 存在 `tenant`、`tenant_user_invitation`；**V7+** 含 **`tenant.user.*`** permission 绑定及自 **`tenant.user.*`** 的重命名（若库已 seed V5，升级路径可测）。
- **AC-3**：`POST /api/tenants` 后存在 tenant 行 + pending **`user-invitation`**；首管在接受前不能 login。
- **AC-4**：`POST /api/tenants` 仅 **`ctx=system`** + **`tenant.create`** → **201**。
- **AC-5**～**AC-6**：租户 PATCH 停用/启用与 login/refresh 语义（同前）。
- **AC-7**：**`POST /api/tenants/user-invitations`** + **`POST /api/auth/user-invitation-acceptances`** 闭环；**`GET /api/tenants/users/me`**；token 不可复用。
- **AC-8**：跨租户邮箱冲突统一失败（**Q215**）。
- **AC-9**：最后 **`TenantAdmin`** 保护（**Q219**）。
- **AC-10**：**`TenantUser`** 对 **`user-invitations` / `users/{id}`** 写操作 **403**。
- **AC-11**：租户用户 **`GET /api/tenants/users/me`** 成功；**`GET /api/users/me`** → 403。
- **AC-12**：系统用户 **`GET /api/users/me`** 成功；**`GET /api/tenants/users/me`** → 403。
- **AC-13**～**AC-15**：同前（refresh/azv、跨租户、README）。

## Constraints

（同前；匿名 **`POST /api/auth/user-invitation-acceptances`**。）

## Decisions

### 前缀

| 前缀 | 用途 |
| --- | --- |
| **`/api/auth/**`** | 会话 + **`user-invitation-acceptances`** |
| **`/api/users/me/**`** | 系统 profile（已实现） |
| **`/api/tenants/**`** | 租户 + 租户上下文 **users / user-invitations** |

### RESTful 资源表（固定）

**系统 · 租户**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/tenants` | 创建租户 + 首管 invitation |
| GET | `/api/tenants` | 分页列表 |
| GET | `/api/tenants/{tenantId}` | 详情 |
| PATCH | `/api/tenants/{tenantId}` | 更新 name / status |
| POST | `/api/tenants/{tenantId}/user-invitations` | 系统代发邀请（Q220 可选） |

**租户上下文 · 用户（JWT `tid`，无 `{tenantId}` 段）**

| 方法 | 路径 | Permission（V7+） | 说明 |
| --- | --- | --- | --- |
| GET | `/api/tenants/users/me` | `tenant.profile.read` | 当前租户用户 |
| PATCH | `/api/tenants/users/me/password` | `tenant.profile.password.write` | 改密 |
| GET | `/api/tenants/users` | `tenant.user.manage` | 列租户用户 |
| GET | `/api/tenants/users/{userId}` | `tenant.user.manage` | 单用户 |
| PATCH | `/api/tenants/users/{userId}` | `tenant.user.manage` | role / disabled |
| GET | `/api/tenants/user-invitations` | `tenant.user.invite` | 列 pending 邀请 |
| POST | `/api/tenants/user-invitations` | `tenant.user.invite` | 创建邀请 |
| GET | `/api/tenants/user-invitations/{invitationId}` | `tenant.user.invite` | 邀请详情 |
| DELETE | `/api/tenants/user-invitations/{invitationId}` | `tenant.user.invite` | 撤销 pending |

**认证 · 匿名**

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/auth/user-invitation-acceptances` | 接受邀请并设密 |

### REST 约定说明

- **`users/me`**：`me` 为集合下的**当前主体**别名（**Q377** 下不另传 userId）；与 **`/api/users/me`**（系统）对称，但处在 **`/api/tenants/users`** 命名空间。
- **邀请与用户的分离**：未接受邀请不是 `users` 资源，仅存在于 **`user-invitations`**；接受后 **`POST .../user-invitation-acceptances`** 创建 **`users`** 行（**201** 或 **204** 由 Plan 固定响应体）。
- **PATCH `users/{userId}`**：仅允许 Plan 列出的字段（如 `roleCode`、`disabled`）；**不**含密码（密码走 **`.../users/me/password`** 或 recovery）。
- **拒绝**：路径中出现 **`members`**、**`member-invitations`**；**拒绝** **`GET /api/tenants/me`**（避免与 `{tenantId}` 路由歧义，统一 **`users/me`**）。

### Permission（V7+）

| Key | 替代 V5 | TenantAdmin | TenantUser |
| --- | --- | --- | --- |
| `tenant.user.invite` | `tenant.user.invite` | ✓ | — |
| `tenant.user.manage` | `tenant.user.manage` | ✓ | — |
| `tenant.profile.read` | — | ✓ | ✓ |
| `tenant.profile.password.write` | — | ✓ | ✓ |

## Related ADRs / Specs

（同前。）

## Open Questions

无。

## Revision log

- 2026-10-08 | 初稿 | none | no — 待用户批准
- 2026-10-08 | `/api/tenants` + REST | none | no — 待用户批准
- 2026-10-08 | **`/api/auth`** + **`/api/users/me`** | none | no — 待用户批准
- 2026-10-08 | **member → user**；租户用户 REST 定为 **`/api/tenants/users/**`**、**`/api/tenants/user-invitations/**`**；**`users/me`**；permission **`tenant.user.*`**（V7 rename） | Req 1～10、AC、Decisions、附录 | no — 待用户批准
