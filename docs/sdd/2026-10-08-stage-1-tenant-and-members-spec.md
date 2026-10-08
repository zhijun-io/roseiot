# 阶段 1 · Spec #3：租户生命周期、成员邀请与租户用户会话

> 状态：待批准  
> 更新日期：2026-10-08  
> 位置：身份迁移 Spec 交付之后；产品/设备（Spec #4～#5）之前  

## Goal

平台管理员可创建、停用与恢复租户；租户管理员可邀请与管理成员；租户用户通过**与平台共用**的登录与会话机制获得 `ctx=tenant` 访问令牌，并在**独立路径前缀**下访问「当前用户」与成员管理 API，且满足租户隔离与 RBAC-0 默认拒绝。

## Scope

- **IAM-002**：仅平台作用域用户（`PlatformOperator` + `identity.tenant.*`）可创建、停用、恢复租户；不开放自助注册与计费。
- **IAM-003 / IAM-004**：租户用户为 `users.tenant_id` 非空行；`user_role` 绑定 `TenantAdmin` 或 `TenantMember`，且 `scope_tenant_id` 等于 `users.tenant_id`；平台与租户用户互斥（不同邮箱）。
- **IAM-005**：成员邀请（创建、接受、过期、撤销、在合法前提下重新邀请）；邀请与接受路径的**统一失败语义**（不泄露邮箱是否已注册、是否属其他租户等）。
- **IAM-006**：成员「移除」为停用（`users.disabled_at`），不物理删除 User；禁止停用或降级**最后一名有效** `TenantAdmin`（**Q61、Q219**）。
- **IAM-007 / IAM-012（租户子集）**：租户用户复用 Spec #2 的 login / refresh / logout / recovery / 改密语义与表 `user_refresh_session`；access JWT 含 **`ctx=tenant`**、**`tid`**（租户 UUID）、**`azv`**；角色变更、成员停用、改密递增 `authorization_version` 并撤销 refresh 族（**Q218**）。
- **IAM-008（本子集）**：登录、邀请接受、邀请创建等敏感入口可配置限速（`shared.ratelimit` + `roseiot.rate-limit.rules`）；阈值在 Plan 固定。
- **IAM-009（本子集）**：**租户停用**后，该租户下用户不得登录、不得 refresh、不得访问租户 API；**不**在本 Spec 实现设备 MQTT/命令/通知边界的停用检查（留 Spec #4+）。
- **IAM-011**：平台租户 API（**`/api/identity/tenants/**`**）与租户内 API（**`/api/tenant/**`**）**路径分离**；租户内 API **不要求**客户端提交 `tenantId` 决定授权（**Q377**）；平台 API 路径含 `{tenantId}`，且须 `identity.tenant.*` permission。
- **REST**：业务 API 前缀 **`/api`**，无 URL 主版本号；本 Spec 固定端点见 **Decisions · HTTP 路径**。
- **持久化**：Flyway 追加 `tenant`、成员邀请等最小表；MyBatis Plus；UUID v7（`io.roseiot.shared.util.UuidV7`）。
- **包布局**：**`io.roseiot.tenant.*`**（`web` / `application` / `persistence` / 按需 `domain`）；平台租户生命周期 HTTP 映射 **`/api/identity/tenants/**`**（Controller 归属 **`tenant.web`** 或 **`identity.web`** 由 Plan 写死其一）；认证入口仍 **`io.roseiot.identity.web.AuthController`**，会话与 JWT 签发扩展在 **`identity.application` / `identity.infra`**。
- **RBAC**：运行时启用 migration Spec 已 seed 的 `identity.tenant.*`、`tenant.member.*`；本 Spec **追加** tenant 侧 profile permission（见附录 A）并通过**新 Flyway 版本**绑定到 `TenantAdmin` / `TenantMember`（不修改已发布 V5 内容）。
- **测试**：Testcontainers；邀请通知使用与 recovery 同类的**可替换测试 double**（**Q211**）；覆盖跨租户拒绝、最后管理员保护、停用租户登录拒绝。
- **README**：平台创建租户、接受邀请、租户用户登录与 `/api/tenant/me` 的手动步骤。

## Non-goals

- 产品、设备、凭证、审计查询（Spec #4～#5 及后续）。
- 租户物理删除、导出与保留策略（**IAM-010、Q224**）。
- 登录邮箱变更（**Q217**）、OAuth / SSO / MFA。
- 多平台管理员自助创建、最后一名平台管理员保护的生产级运维流程（**Q221** — 仍仅 Bootstrap 一条平台用户，除非访谈另批）。
- 租户自定义角色、用户组、ABAC 引擎。
- 平台管理员代设成员永久密码（**Q211** — 仅邀请 + 接受时设密）。
- 设备认证、异步任务、Webhook 在租户停用时的终止（**IAM-009** 全量 — 后续 Spec）。
- 在 access JWT 内嵌 permission 列表。

## Current Context

- **Spec #1**：CLI bootstrap → `users(tenant_id NULL)` + `PlatformOperator`。
- **Spec #2 / 身份迁移 Spec（已批准）**：`/api/identity/login|token|logout*|recovery/*`；`/api/identity/me/**` 仅 **`ctx=platform`** + `identity.profile.*`；JWT `sub`、`sid`、`ctx`、`azv`；RBAC 表与 V5 seed 已含 `identity.tenant.*`、`tenant.member.*`，**尚无 `tenant` 表与租户 HTTP**。
- **ADR**：[`docs/adr/2026-10-08-identity-user-rbac.md`](../adr/2026-10-08-identity-user-rbac.md)。
- **PRD**：`IAM-002`～`IAM-012` 子集见 Scope；`PRD-001` / `PRD-002` 适用于成员与租户资源 ID 的授权与存在性语义。
- **术语**：[`GLOSSARY.md`](../../GLOSSARY.md)。

## Requirements

1. 存在 **`tenant`** 持久化实体：稳定内部 ID、人类可读名称、可选稳定 **`slug`**（全平台唯一，Plan 固定是否必填）、`disabled_at`、`created_at`；停用与启用为显式动作，非 DELETE。
2. 平台用户调用 **`POST /api/identity/tenants`** 可创建租户，并**同时**为首个租户管理员邮箱创建**待接受邀请**（角色 `TenantAdmin`）；创建成功后租户处于启用状态；首个管理员在接受邀请前**不能**登录。
3. 平台用户可 **`POST /api/identity/tenants/{tenantId}/disable`** 与 **`.../enable`**；停用后该租户所有租户用户登录与 refresh 失败（与登录失败统一语义）；已签发 access 在 TTL 内仍可能有效，但 refresh 与租户 API 须拒绝（**Q222** 的用户/login 部分）。
4. 租户 **`TenantAdmin`** 可创建成员邀请（目标角色仅 `TenantAdmin` 或 `TenantMember`）；邀请记录绑定租户、邮箱、角色；令牌单次使用、短期有效、仅存摘要；**Q214、Q215** 约束重复邀请与全局邮箱冲突。
5. 匿名 **`POST /api/identity/invites/accept`**（或 Plan 与 recovery 对齐的等价路径）提交邀请令牌与新密码：成功则创建或激活 `users(tenant_id=…)`、写入 `user_role`、撤销该邀请；失败语义不区分「令牌无效」与「邀请已过期」对外字段（可测的稳定 code）。
6. 租户 **`TenantAdmin`** 可列出成员与待处理邀请、撤销待接受邀请、变更成员角色、停用成员；停用/降角色时遵守最后一名 `TenantAdmin` 保护（**Q219** — 并发安全由 Plan 选定约束或锁策略）。
7. 租户用户登录 **`POST /api/identity/login`** 成功后 access JWT 满足 **`ctx=tenant`**、**`tid`** 等于其 `users.tenant_id`、**`azv`** 与库一致；**`GET /api/tenant/me`**、**`PATCH /api/tenant/me/password`** 要求 `ctx=tenant` 与附录 A profile permission。
8. 平台 JWT 访问 **`/api/tenant/**`** 被拒绝；租户 JWT 访问 **`/api/identity/tenants/**`**、**`/api/identity/me/**`** 被拒绝（403 或项目统一 access denied）；租户 JWT **仍可使用** **`/api/identity/login|token|logout*`** 等同一会话端点。
9. **`TenantMember`** 不具备 `tenant.member.invite` / `tenant.member.manage`；具备附录 A 中只读 profile（及 Plan 固定的改密 permission 若赋予成员）。
10. （可选但建议本 Spec 交付）**Q220**：当租户无有效 `TenantAdmin` 时，平台用户可通过 **`POST /api/identity/tenants/{tenantId}/assign-admin`**（名称 Plan 固定）指定一名**已存在或经邀请流程**的管理员；须强 permission（如 `identity.tenant.enable` 或专用键 Plan 追加）且审计留痕字段在 Plan 列出 — 若裁剪出 Plan，须在 Revision log 说明 defer。

## Acceptance Criteria

### 持久化与 RBAC

- **AC-1**：`./mvnw -q verify` 退出码为 0（含本 Spec 集成测试）。
- **AC-2**：Flyway 自空库应用至最新后存在 `tenant` 表及邀请表；V5 既有 seed 未被改写；新增 migration 仅追加 permission 与 role 绑定（附录 A）。
- **AC-3**：创建租户后，库中存在对应 `tenant` 行及一条指向首个管理员邮箱的**待接受**邀请；尚无该邮箱的 `users` 行或尚无 `tenant_id` 绑定（Plan 固定「先邀请后建 user」策略，与 Q211 一致）。

### 平台租户生命周期

- **AC-4**：持有 platform access 且具备 `identity.tenant.create` 的主体 `POST /api/identity/tenants` 成功；无 token 或 `ctx=tenant` token 被拒绝。
- **AC-5**：`POST /api/identity/tenants/{tenantId}/disable` 后，该租户用户 login 与 refresh 对外失败语义与错误密码一致（不暴露「租户已停用」区分字段给未授权探测方 — 对已认证平台管理员查询详情可返回状态，Plan 固定是否提供 `GET /api/identity/tenants/{tenantId}`）。
- **AC-6**：停用后 `POST .../enable` 恢复；恢复**不**自动 resurrect 已撤销 refresh（**Q223**）；用户须重新 login。

### 邀请与成员

- **AC-7**：`TenantAdmin` 创建邀请后，测试 double 捕获通知；`POST /api/identity/invites/accept` 设密成功；该用户 login 获得 `ctx=tenant` 且 `tid` 正确；同一邀请令牌二次接受失败。
- **AC-8**：对已是其他租户用户的邮箱发起邀请，创建邀请 API 返回统一失败语义（**Q215**）；不返回对方租户标识。
- **AC-9**：仅一名 `TenantAdmin` 时，对其停用或降为 `TenantMember` 的请求失败且仍至少一名有效 `TenantAdmin`（并发用例至少一条自动化或 Plan 声明的集成测试策略）。
- **AC-10**：`TenantMember` 调用成员管理写 API 返回 403。

### 会话与路径隔离

- **AC-11**：租户用户 login 后 `GET /api/tenant/me` 成功；`GET /api/identity/me` 返回 403（或统一 access denied）。
- **AC-12**：平台用户 login 后 `GET /api/identity/me` 仍成功（Spec #2 回归）；`GET /api/tenant/me` 返回 403。
- **AC-13**：成员被停用后 refresh 不能换取 access；改密后 `azv` 递增且旧 refresh 失效（与 migration Spec **AC-12** 等价，对象换为租户用户）。
- **AC-14**：租户 A 的 admin token 不能访问租户 B 的成员 API（本 Spec 仅成员列表等等价端点；无 B 的租户内资源时，用伪造 `userId` 或跨租户邀请接受场景断言 **403/404 不泄露** 策略，Plan 固定一种）。

### 文档

- **AC-15**：README 列出本 Spec 新增端点、JWT `ctx`/`tid` 说明（租户用户）、邀请 accept 与平台创建租户的 curl 示例。

## Constraints

- PostgreSQL + Flyway：**仅追加** migration；不修改 V1～V6 已发布脚本内容。
- 模块化单体；构造器注入；事务边界在 `*Service`；Controller 不承载业务规则。
- 默认拒绝：`SecurityConfig`（或等价）显式放行匿名 invite accept；其余本 Spec 端点 authenticated + permission + `ctx` 守门。
- 邀请令牌、恢复令牌不得写入 INFO 日志；错误响应不泄漏堆栈、SQL、令牌明文。
- 限速：邀请创建/accept 规则进入 `application.yml` 的 `roseiot.rate-limit.rules`（与 identity login 同机制），不在 Service 内手写计数。
- 测试不依赖作者本机唯一 PostgreSQL（Testcontainers）。

## Decisions

- **选定（HTTP 路径）**：
  - **平台租户管理**：`/api/identity/tenants/**`（路径含 `{tenantId}`；permission 键为 `identity.tenant.*`；**不再使用** 历史示例 `/api/platform/**`，与 Q512 后 `/api/identity/me/**` 同属 **identity** 前缀下的平台运维面，**Q377**）。
  - **租户内业务（本 Spec）**：`/api/tenant/**`（**不含** `tenantId` 段；租户由 JWT `tid` + `users.tenant_id` 推导）。
  - **共用认证**：`/api/identity/login|token|logout*|recovery/*`（Spec #2 不变）；**匿名** **`POST /api/identity/invites/accept`**。
  - **平台 profile**：`/api/identity/me/**`（不变）；**租户 profile**：`/api/tenant/me/**`。
- **选定（创建租户）**：创建租户与首个 `TenantAdmin` 邀请**同一事务**；不在 API 响应中返回邀请明文令牌（**Q211**）。
- **选定（slug）**：租户 `slug` 全平台唯一、创建后不可变（Plan 可定格式 `[a-z0-9-]{3,32}`）；显示名称可改（若本 Spec 暴露 rename，否则 Non-goal 内省略 PATCH tenant）。
- **选定（列表）**：平台 **`GET /api/identity/tenants`** 提供有界分页（cursor 或 offset 由 Plan 固定）；租户 **`GET /api/tenant/members`** 列出 active 成员与 pending 邀请的最小字段集。
- **拒绝**：租户用户通过 Header/Query 提交 `tenantId` 切换租户（**Q59、Q377**）。
- **拒绝**：`/api/identity/me` 同时服务租户用户（与 migration Spec **AC-13** 一致）。

## 附录 A：本 Spec 新增 permission（示意）

| Permission key | 用途 | TenantAdmin | TenantMember | 本 Spec HTTP |
| --- | --- | --- | --- | --- |
| `tenant.profile.read` | 当前租户用户 profile | ✓ | ✓ | `GET /api/tenant/me` |
| `tenant.profile.password.write` | 租户用户改密 | ✓ | ✓ | `PATCH /api/tenant/me/password` |

（`identity.tenant.*`、`tenant.member.*` 已存在于 V5 seed；本表 permission 通过 **V7+** 追加并绑定角色。Plan 可增 `identity.tenant.read` 用于平台列表，若需要则写入 Plan 而非 silent 添加。）

## Related ADRs

- [`docs/adr/2026-10-08-identity-user-rbac.md`](../adr/2026-10-08-identity-user-rbac.md)

## Related Specs

- Spec #1 Bootstrap — 首个平台用户  
- Spec #2 平台管理员认证 — 会话与 recovery 语义基准  
- [`2026-10-08-stage-1-identity-user-rbac-migration-spec.md`](2026-10-08-stage-1-identity-user-rbac-migration-spec.md) — User/RBAC/JWT 基础  
- Spec #4+ — 产品、设备与 `device.*` permission 的运行时消费  

## Open Questions

无（Q220 平台指定新管理员：默认 **in scope** 为 **AC 可选** Requirement 10；若你方希望首版裁剪，批准 Spec 时在对话中说明即可，我会修订 Requirement 10 与对应 AC 并写 Revision log）。

## Revision log

- 2026-10-08 | 初稿：阶段 1 Spec #3 租户、邀请、租户会话与路径约定 | none | no — 待用户批准
- 2026-10-08 | 平台租户 HTTP 前缀 `/api/platform/tenants` → `/api/identity/tenants`（对齐 identity 能力与 Q512） | Req 8、AC-4/5、Decisions | no — 待用户批准
