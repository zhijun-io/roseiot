# 阶段 1 · 身份迁移 Spec：统一 User、RBAC-0 与 platform_admin 收敛

> 状态：已批准  
> 更新日期：2026-10-08  
> 批准日期：2026-10-08  
> 位置：Spec #2（平台管理员认证）与 Spec #3（租户与用户业务）之间  
> **实现说明**：对外 HTTP 认证与 **`/me/**`** 共用前缀 **`/api/identity`**（无中间 **`auth`** 段）；包布局见 migration-plan（`identity.application` / `identity.web` / `identity.infra.security` 等）。JWT **`ctx` / `azv`** 与 RBAC 守门为 Slice 3 目标；当前 access JWT 仍为 **`sub` + `sid`**（与 Spec #2 一致）。

## Goal

在 **不削弱 Spec #2 已对外承诺的 HTTP 行为** 的前提下，将持久化与认证实现收敛到 ADR 定义的 **单一 User 模型 + RBAC-0**，并为 Spec #3 复用同一会话与授权基础设施。

## Scope

- **持久化（Flyway）**：`users`、RBAC 四表（permission / role / role_permission / user_role）、会话与恢复表改为引用 `user_id`；自 `platform_admin` **一次性数据迁移** 后移除 `platform_admin` 表。
- **RBAC-0 seed**：内置角色 `PlatformOperator`、`TenantAdmin`、`TenantMember` 与 Spec 附录中的 **permission 键**；本 Spec **仅对平台作用域 API 启用 permission 校验**（租户侧 permission 仅 seed，无 HTTP 消费）。
- **Bootstrap（Spec #1）**：CLI 引导创建 `users(tenant_id NULL)` + `PlatformOperator` assignment，不再写入 `platform_admin`。
- **认证与会话**：login / refresh / logout / logout-all / recovery / change-password / `GET /api/identity/me` 行为与 Spec #2 状态码与失败语义保持一致；实现基于 `user_id`（见 Decisions）。
- **JWT access（目标）**：claims 增加 **`ctx=platform`**、**`azv`**；保留 **`sub`=User ID**、**`sid`=refresh 会话 ID**；TTL 与 Spec #2 一致（15 分钟 access，7 天 refresh）。
- **授权（目标）**：`/api/identity/me/**` 要求 `ctx=platform` 且具备对应 permission；`AuthorizationService` 在请求边界加载 effective permissions。
- **代码结构**：**`identity` + `shared`**；**取消 `io.roseiot.platform.*`**；IAM 在 **`identity.application` / `identity.web` / `identity.domain` / `identity.infra` / `identity.persistence`**；横切 API 与限速在 **`shared.api` / `shared.ratelimit`**。
- **测试**：现有 Spec #2 相关 MockMvc / IT **全部通过**（回归）；新增 RBAC 与 JWT claims 的针对性测试。
- **README**：若端点或 JWT 文档化字段有变，同步更新；否则注明「对外契约未变」。

## Non-goals

- 租户 CRUD、成员邀请、租户用户登录（Spec #3）。
- 租户业务 API 与 `device.*` permission 的运行时校验（permission 可 seed，不强制挂到 Controller）。
- 自定义角色 UI、用户组、ABAC 引擎、ThingsBoard PE 权限依赖图（ADR 演进项）。
- 多平台管理员自助创建 UI；首版仍仅 **Bootstrap 创建首个** 平台用户（IAM-001），是否允许多个 `tenant_id IS NULL` 用户由应用层约束在 Spec #3 前 **仅 bootstrap 一条**（与现 Spec #1 一致）。
- 将历史 Git 中的 `platform_admin` 文档字符串全部重命名为 User（允许渐进；HTTP JSON 字段名不在本 Spec 变更范围内除非 AC 要求）。

## Current Context

- **历史（V2/V3）**：曾用 `platform_admin` 及会话/恢复专表；**V4/V5 已收敛**至 `users`、`user_refresh_session`、`user_recovery_request` 与 RBAC 表。
- **当前 JWT**：`sub` 为 `users.id`，`sid` 为会话 id；Resource Server 对 `/api/identity/me/**` 要求 authenticated（RBAC/`ctx`/`azv` 校验待 Slice 3）。
- **ADR**：[`docs/adr/2026-10-08-identity-user-rbac.md`](../adr/2026-10-08-identity-user-rbac.md) 为身份与 RBAC 语义来源。
- **PRD**：`IAM-003`、`IAM-004`、`IAM-011`、`IAM-012` 已更新；本 Spec 落实平台侧子集。

## Requirements

1. 数据库中存在符合 ADR 的 `users` 行：平台用户 `tenant_id IS NULL`；邮箱全平台唯一；密码字段与 Spec #1 相同语义（自适应哈希 + `password_algorithm`）；`authorization_version` 为正整数，首值 **1**；主体停用字段（如 `disabled_at` 或 `status`）在 Plan 中固定，本 Spec 至少支持「停用后无法登录」（与 Spec #2 失败语义一致，不区分原因）。
2. RBAC 表存在且 Flyway seed 填充内置 permission 与 role；每个自 bootstrap 或自 `platform_admin` 迁移的平台用户拥有 **唯一** `PlatformOperator` assignment（PLATFORM scope）。
3. 现有库中 `platform_admin` 数据在迁移后可在 `users` 中找到一一对应行（相同 id 或 Spec 固定映射策略），且会话/恢复行的 `user_id` 与 `users.id` 一致（**不在 DDL 中建数据库外键**）。
4. Spec #2 全部匿名与 authenticated 端点对外行为不变（见 Acceptance Criteria 回归节）。
5. 改密、recovery confirm、logout-all 等安全事件仍撤销 refresh 族；**改密、recovery 完成、或 role assignment 变更**时 `users.authorization_version`（或等价列）递增（`azv`），且 JWT 内 `azv` 须与库中值一致才视为有效平台/租户会话（Plan 固定校验点）。
6. `/api/identity/me` 与改密 endpoint 在 RBAC 层要求显式 permission（至少 `identity.profile.read` / `identity.profile.password.write`）（Slice 3）。
7. 持有 `ctx=tenant` 的 JWT（测试夹具签发）访问 `/api/identity/me/**` 被拒绝（403）（Slice 3）。
8. `PlatformOperator` **不**包含 `device.read`、`device.credentials.read` 等租户设备类 permission（Q38；seed 可验证）。

## Acceptance Criteria

### 迁移与 RBAC 基础

- **AC-1**：自空库 `./mvnw -q verify` 通过；Flyway 从 V1 顺序应用至最新，无 `platform_admin` 表。
- **AC-2**：Bootstrap 成功后，`users` 存在一条 `tenant_id IS NULL` 记录，且 `user_role` 将其绑定到 `PlatformOperator`。
- **AC-3**：提供 Flyway 迁移测试或集成测试：预置 V2/V3 结构并插入 `platform_admin` 行，迁移后数据在 `users` 中可登录，refresh/recovery 的 `user_id` 与对应 `users.id` 一致。

### Spec #2 行为回归（对外契约）

- **AC-4**：与 Spec #2 **AC-2** 等价：登录、`GET /api/identity/me` 成功；无 token / 无效 token 拒绝。
- **AC-5**：与 Spec #2 **AC-3** 等价：refresh 轮换与重放撤销语义不变。
- **AC-6**：与 Spec #2 **AC-4** 等价：`POST /api/identity/logout` 与 `POST /api/identity/logout-all` 后，相应 refresh 不能再换取 access。
- **AC-7**：与 Spec #2 **AC-5** 等价：登录失败统一语义。
- **AC-8**：与 Spec #2 **AC-6、AC-7** 等价：recovery 流程与统一响应。
- **AC-9**：与 Spec #2 **AC-8** 等价：Testcontainers + 测试 recovery notifier double。
- **AC-10**：登录限速相关测试（若启用）仍通过。
- **AC-15**：与 Spec #2 **AC-9** 等价：README 仍描述本 Spec 涉及端点与验证步骤；若 JWT claims 有文档化说明，须包含 `ctx` 与 `azv`。

### JWT 与授权

- **AC-11**：合法平台 access JWT 解码后含 `ctx=platform`、`azv` ≥ 1，且 `sub` 等于对应 `users.id`。
- **AC-12**：改密成功后 `azv` 递增，且旧 refresh 不能换取 access（全部族撤销）。
- **AC-13**：测试 JWT 带 `ctx=tenant` 访问 `/api/identity/me` 返回 403（或项目统一 access denied，与 Spec #2 handler 一致）。
- **AC-14**：数据库 seed 校验：`PlatformOperator` 的 permission 集合 **不包含** `device.read`、`device.credentials.read`（及 Plan 列出的其它 `device.*` 读类键，若有）。

## Constraints

- PostgreSQL + Flyway；**仅追加**新迁移版本（**V4** 结构/数据迁移，**V5** RBAC-0 目录，**V6** 权限键 `platform.*` → `identity.*` 重命名），不修改已发布迁移文件内容。
- 新迁移脚本在 **CI 空库** 上可重复跑全套 Flyway（V1→最新）；**已执行 V4 的生产/开发库** 不假设再次应用 V4。自 V2/V3 升级路径由 AC-3 覆盖。
- 模块化单体；构造器注入；事务边界在 application `*Service`。
- 不引入 Redis / OPA；permission 缓存允许 Caffeine 或请求内单次加载。
- JWT 仍由 Spring OAuth2 Resource Server 验证；claim 校验在 `JwtAuthenticationConverter` 或等价组件中集中处理。
- 秘密与令牌不进 INFO 日志（延续 Spec #2）。

## Decisions

- **选定（迁移策略）**：单版本 Flyway **创建新表 → 拷贝 `platform_admin` → 重绑 session/recovery → 删除旧表**；不做双写期（数据量与部署模型不支持多实例并行迁移）。
- **选定（ID 策略）**：迁移时 **`users.id` = 原 `platform_admin.id`**，避免 JWT 历史在迁移窗口无效（若环境已发 token，仍会因 refresh 表重建而需重新登录 — README 迁移说明一句即可）。
- **选定（包布局）**：
  - **`io.roseiot.shared`**：`api`、`util`（`UuidV7`）、`ratelimit`（`RateLimitFilter` + `roseiot.rate-limit.rules`）；
  - **`io.roseiot.identity.application`**：`*Service`、引导、RBAC 求值（`AuthorizationService`）；
  - **`io.roseiot.identity.web`**：HTTP 与能力内 `@RestControllerAdvice`；
  - **`io.roseiot.identity.domain`**：异常、`LoginEmails`、`PasswordPolicy`；
  - **`io.roseiot.identity.infra.security`**：`SecurityConfig`、`IdentityAuthConfig`、`JsonSecurityHandlers`；
  - **`io.roseiot.identity.persistence`**：实体与 Mapper；**`identity.infra.mybatis`**：`MybatisPlusConfig`、`UuidTypeHandler`。
- **选定（包迁移）**：身份迁移 Slice 1 **整体搬迁** `io.roseiot.platform.*` 与 **`io.roseiot.config.*`** → 上表（与 V4 同批）；**不**保留旧包名。
- **选定（会话表名）**：Flyway 将 `platform_admin_refresh_session` / `platform_admin_recovery_request` **重命名**为 `user_refresh_session` / `user_recovery_request`（列 `user_id`），或等价命名；Plan 写死一种，避免双名并存。
- **选定（命名）**：应用服务 `UserSessionService`、`UserRefreshSessions`（或 `AuthRefreshSessions`）替代 `PlatformAdminSessionService` / `PlatformAdminRefreshSessions`；Bootstrap 改为 `BootstrapPlatformUserService` 或扩展现有 bootstrap 服务写 `users`。
- **选定（表名）**：持久化表 `users`（非 SQL 保留字 `user`）；列 `authorization_version` 映射 JWT `azv`。
- **拒绝**：保留 `platform_admin` 兼容视图长期双轨。
- **拒绝**：在 access JWT 内嵌完整 permission 列表（IAM-012）。

## 附录 A：RBAC-0 permission 键（首版 seed）

命名约定：`{能力域}.{资源}.{动作}`，小写；平台作用域权限使用 **`identity.*`**（与 `identity` 能力包一致）；本 Spec **运行时校验**仅 **identity.*** 中标记为「本 Spec 使用」者。

| Permission key | 用途 | PlatformOperator | TenantAdmin | TenantMember | 本 Spec HTTP |
| --- | --- | --- | --- | --- | --- |
| `identity.profile.read` | 当前平台用户 profile | ✓ | — | — | ✓ `/api/identity/me` |
| `identity.profile.password.write` | 平台用户改密 | ✓ | — | — | ✓ |
| `identity.tenant.create` | 创建租户 | ✓ | — | — | —（Spec #3+） |
| `identity.tenant.disable` | 停用租户 | ✓ | — | — | — |
| `identity.tenant.enable` | 恢复租户 | ✓ | — | — | — |
| `tenant.member.invite` | 邀请成员 | — | ✓ | — | — |
| `tenant.member.manage` | 成员停用/角色 | — | ✓ | — | — |
| `device.read` | 读设备与遥测 | — | ✓ | ✓ | —（Q38：平台无） |
| `device.credentials.read` | 读设备凭证元数据 | — | ✓ | — | —（Q38：平台无） |
| `device.command.issue` | 下发命令 | — | ✓ | ✓ | — |
| `alarm.ack` | 确认告警 | — | ✓ | ✓ | — |

（Plan 可增删键，但 **AC-14** 与 Q38 约束不可删。）

## 附录 B：建议实施顺序（Plan 输入，非 AC）

每个 Slice 结束时应 **`./mvnw -q verify` 通过**（与 Spec #2 Plan 一致）。不得长期保留「仅 migration 绿、auth 测试全红」的中间态。

| Slice | 目标 | 主要交付 | AC | Depends on |
| --- | --- | --- | --- | --- |
| **1** | 数据模型、包扁平化与 RBAC 底座 | **`io.roseiot.platform.*` → `io.roseiot.<能力>` 整体搬迁**（含测试镜像）；Flyway `V4` 与 `identity`/`rbac`/`persistence`；**AC-3 升级路径测试** | AC-1、AC-3、AC-14 | — |
| **2** | 引导与测试夹具 | Spec #1 Bootstrap 改为写入 `users(tenant_id NULL)` + `PlatformOperator` assignment；**所有**集成/MockMvc 种子从 `platform_admin` 改为 users+assignment；移除对 `PlatformAdminMapper` 的业务依赖 | AC-2 | Slice 1 |
| **3** | Auth 运行时收敛 | 会话/恢复/login/me/改密/recovery 全面 `user_id`；JWT 增加 `ctx=platform`、`azv` 并与库校验；`/api/identity/me/**` RBAC + `ctx` 守门 | AC-11、AC-12、AC-13 | Slice 2 |
| **4** | Spec #2 行为回归 | 对照 Spec #2 Plan 端点逐条断言；限速测试若存在一并回归 | AC-4～AC-10 | Slice 3 |
| **5** | 文档 | README（AC-15）；必要时 Spec #1 Bootstrap Spec **Revision**（引导目标表改为 `users`，不改正文历史 AC） | AC-15 | Slice 4 |

**顺序要点（相对旧附录 B 的修正）：**

- **Bootstrap 不得晚于 Auth 切换**（原步骤 4 过晚）：空库 CI 路径为 V1→V4→**bootstrap→login**；Slice 2 必须在 Slice 3 之前完成。
- **identity/rbac 与 V4 同属 Slice 1**，避免「有表无 seed / 无 AuthorizationService」。
- Slice 3 与 Slice 4 可拆 PR，但 **Slice 3 合并前 verify 须已绿**（即 Slice 3 完成时 AC-4～10 应已能跑通，Slice 4 以完整回归与补测为主）。

## Related ADRs

- [`docs/adr/2026-10-08-identity-user-rbac.md`](../adr/2026-10-08-identity-user-rbac.md)

## Related Specs

- Spec #1 Bootstrap — 引导写入目标改为 `users`  
- Spec #2 平台管理员认证 — 本 Spec 的回归基准  
- [Spec #3 租户与成员](2026-10-08-stage-1-tenant-and-members-spec.md) — 租户用户 `ctx=tenant` JWT、平台 **`/api/identity/tenants/**`**、租户内 **`/api/tenant/**`**；**待批准 / 未实现**  
- 路线图 **`device` / `product` 等** — 各自 **`io.roseiot.<能力>.*`**，本 Spec 不创建  

## Open Questions

无。

## Revision log

- 2026-10-08 | 用户批准 Spec（对话确认） | none — clarification | no
- 2026-10-08 | 文档审查：AC-6/15、azv、会话表名、Open Questions 等 | AC-6、AC-14、AC-15 等 | no — 批准前澄清
- 2026-10-08 | 修订附录 B：Bootstrap 与 V4 顺序、每 Slice verify | none — clarification | yes — Plan 须按新 Slice 表分解
- 2026-10-08 | 编写 Implementation Plan | none | [`2026-10-08-stage-1-identity-user-rbac-migration-plan.md`](2026-10-08-stage-1-identity-user-rbac-migration-plan.md)
- 2026-10-08 | 取消 `platform` Java 父包，能力直挂 `io.roseiot.*` | none — clarification | yes — Plan Slice 1 含包搬迁
- 2026-10-08 | 根包仅 `identity`+`shared`；横切 config → `shared.config` | none — clarification | yes — Plan 包结构表
- 2026-10-08 | 明确多能力并列（identity/device/…）；本 Spec 仅 identity | none — clarification | no
- 2026-10-08 | Related Specs：链接 Spec #3 文档 | none — clarification | no
