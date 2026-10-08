# 阶段 1 · 身份迁移：统一 User、RBAC-0 Implementation Plan

**Spec:** [`docs/sdd/2026-10-08-stage-1-identity-user-rbac-migration-spec.md`](2026-10-08-stage-1-identity-user-rbac-migration-spec.md)

## AC 覆盖矩阵

| AC | Slice |
| --- | --- |
| AC-1 | 1、2 |
| AC-2 | 2 |
| AC-3 | 1 |
| AC-4～AC-10 | 3、4 |
| AC-11～AC-13 | 3 |
| AC-14 | 1 |
| AC-15 | 5 |

## 代码包结构（多能力并列；本 Plan 仅 `shared` + `identity`）

Roseiot 长期布局为 **`io.roseiot.<能力>`**（`identity`、`device`、`tenant` …）+ **`io.roseiot.shared`**。**本迁移不创建** 其它能力包。

业务代码 **不再使用** `io.roseiot.platform.*` 与 **`io.roseiot.config.*`**。Slice 1 **整体搬迁** 并修正 import、`@MapperScan`、`RoseiotApplication` 引用。

```text
io.roseiot
├── RoseiotApplication.java
├── BootstrapApplicationRunner.java   # CLI --bootstrap
├── shared/
│   ├── api/                 # ApiErrorResponse、全局异常处理
│   ├── util/                # UuidV7
│   └── ratelimit/           # RateLimitFilter（规则见 application.yml）
└── identity/
    ├── application/         # UserSessionService、BootstrapUserService、AuthorizationService
    ├── domain/              # 异常、LoginEmails、PasswordPolicy
    ├── web/                 # AuthController、IdentityMeController
    ├── infra/               # security、mybatis、recovery
    └── persistence/         # User、会话、RBAC 表 Mapper
```

| 包 | 职责 |
| --- | --- |
| `io.roseiot.identity.infra.security` | `SecurityConfig`、`JsonSecurityHandlers`、`IdentityAuthConfig` |
| `io.roseiot.shared.api` / `util` / `ratelimit` | 错误体、UuidV7、声明式限速（`roseiot.rate-limit.rules`） |
| `io.roseiot.identity.web` | 认证 HTTP；`AuthController`、`IdentityMeController` |
| `io.roseiot.identity.application` | 会话、引导、RBAC 求值（`AuthorizationService`） |
| `io.roseiot.identity.persistence.*` | 全部当前 Flyway 表；删除 `PlatformAdmin*` |

**搬迁对照（Slice 1 一次性完成）**

| 原包 | 新包 |
| --- | --- |
| `io.roseiot.config.*` | 删除；Security → `io.roseiot.identity.infra.security` |
| `io.roseiot.platform.auth.*` | `io.roseiot.identity.web` + `io.roseiot.identity.application` |
| `io.roseiot.platform.bootstrap.*` | `io.roseiot.identity.application`（`BootstrapUserService`）+ 根包 `BootstrapApplicationRunner` |
| `io.roseiot.platform.persistence.*` | `io.roseiot.identity.persistence.*` |
| `io.roseiot.platform.config.MybatisPlusConfig` | `io.roseiot.identity.infra.mybatis.MybatisPlusConfig` |
| `io.roseiot.platform.PlatformPasswordPolicy` | `io.roseiot.identity.domain.PasswordPolicy` |
| `io.roseiot.platform.support.UuidV7` | `io.roseiot.shared.util.UuidV7` |

**HTTP 路径（当前实现）**：会话 **`/api/auth/**`**；平台 profile **`/api/users/me/**`**（见 README）。

## Risks / Dependencies

- **V4 删除 `platform_admin`**：合并 Slice 1 的 PR 必须 **同批** 完成 Java 持久化与 auth 对 `users` 的引用，否则无法编译或 IT 无法跑。
- **工作区未提交 auth 重构**：实施前建议先提交或整理当前 auth 分层，避免与迁移 PR 冲突。
- **包扁平化**：Slice 1 同 PR 删除 `io.roseiot.platform` 与 **`io.roseiot.config`** 源码目录；grep 确保无残留 import。
- **JWT 密钥**：仍用 `ROSEIOT_AUTH_JWT_SECRET` / `roseiot.auth.jwt.hmac-secret`；测试固定 secret。
- **升级库**：AC-3 测试在 Testcontainers 上先应用 V1–V3 并插入行，再应用 V4；CI 主路径为空库 V1→最新。
- **access TTL 内 azv 变更**：改密/recovery 后旧 access 可能仍有效至过期 — 与 ADR 一致；AC-12 断言 **refresh** 不可用 + **azv** 递增。

## 本 Plan 固定的契约（继承 Spec #2 Plan，仅 JWT 增量）

### HTTP 路径

与 platform-admin-auth-plan **语义相同**；当前路径 **`/api/auth/**`** + **`/api/users/me/**`**（见 README）。

### 访问令牌 JWT（相对 Spec #2 的变更）

| Claim | 值 |
| --- | --- |
| `sub` | `users.id`（UUID 字符串） |
| `sid` | refresh 会话 id |
| `ctx` | 系统用户固定 **`system`** |
| `azv` | `users.authorization_version`（整数） |
| `exp` / `iat` | 不变（15 分钟） |

Resource Server 在认证转换器中校验：**JWT `azv` == 库中 `authorization_version`**，且 **`ctx=system`** 方可访问 **`/api/users/me/**`。不匹配 → **401**（视为无效令牌）。

### RBAC（本迁移 Spec 运行时）

| 路径 | Permission |
| --- | --- |
| `GET /api/users/me` | `user.profile.read` |
| `PATCH /api/users/me/password` | `user.password.write` |

`/api/auth/login`、`/token`、`/recovery/*` 等 **不** 做 RBAC permission 检查（仍用凭证校验 + 限速）。

### 用户停用

- 列名固定：**`disabled_at TIMESTAMPTZ NULL`**（非空即不可登录）；登录失败语义并入 **AC-7** 统一 401，不区分停用/密码错误。

### Flyway V4 表（固定命名）

**`users`**

- `id UUID PK`
- `tenant_id UUID NULL`（首版无 `tenant` 表，不加 FK）
- `email VARCHAR(320) NOT NULL`
- `password_hash`, `password_algorithm`（同 Spec #1）
- `authorization_version INT NOT NULL DEFAULT 1`
- `disabled_at TIMESTAMPTZ NULL`
- `created_at TIMESTAMPTZ NOT NULL DEFAULT now()`
- `UNIQUE (email)`

**RBAC**

- `permission(id UUID PK, code VARCHAR(128) UNIQUE NOT NULL)`
- `role(id UUID PK, code VARCHAR(64) UNIQUE NOT NULL, scope VARCHAR(16) NOT NULL)` — SQL 保留字，DDL 使用 `"role"` — `SYSTEM` \| `TENANT`
- `role_permission(role_id, permission_id, PK(role_id, permission_id))`
- `user_role(id UUID PK, user_id → users, role_id → role, scope_tenant_id UUID NULL)` — 平台绑定的 `scope_tenant_id IS NULL`

**会话 / 恢复**（自 V3 演进）

- 重命名 `platform_admin_refresh_session` → **`user_refresh_session`**，`platform_admin_id` → **`user_id`** → `users(id)`
- 重命名 `platform_admin_recovery_request` → **`user_recovery_request`**，`platform_admin_id` → **`user_id`**

**数据迁移步骤（单事务脚本内顺序）**

1. **V4**：建 `users` / RBAC 空表；`INSERT INTO users SELECT … FROM platform_admin`；会话/恢复表重命名；`DROP platform_admin`（**不含** RBAC 目录 seed）。
2. **V5**：**INSERT RBAC-0 目录 seed**（附录 A；`gen_random_uuid()`，按 `code` 关联）；为已迁移平台用户补 `user_role` → `SystemAdmin`。

Seed 后 **`SystemAdmin`** 含 `user.profile.*`、`tenant.*`；**不含** `device.read`、`device.credentials.read`（AC-14）。

---

## Slice 1：包扁平化、Flyway V4、RBAC seed 与升级路径

- **Goal**：**`io.roseiot.platform.*` 与 `io.roseiot.config.*` 清零**；包布局落到 **`shared` + `identity`**；空库与自 V3 升级库 schema 就绪；**无** `platform_admin` 表。
- **Acceptance**: AC-1（schema）、AC-3、AC-14
- **Depends on**: None
- **Test or proof**:
  - `IdentityUserRbacMigrationIntegrationTest`：空库 Flyway 后断言表存在、`platform_admin` 不存在。
  - `V3UserMigrationIntegrationTest`：仅 V1–V3 插入 1 行 `platform_admin` + 会话行 → 应用 V4 → 能 `SELECT` 对应 `users` 行与 `user_refresh_session.user_id`。
  - `SystemAdminSeedIntegrationTest`：SQL/Mapper 断言 Operator 无 `device.read` / `device.credentials.read`。
- **Implementation outline**:
  - **包搬迁**：按上表移动 `src/main/java` 与 `src/test/java`；更新 `RoseiotApplication`、`MybatisPlusConfig`、全部 import；删除空 `platform` 目录。
  - `V4__identity_user_rbac.sql`、`V5__rbac_0_seed.sql`、`V6__permission_code_identity_prefix.sql`（见上）。
  - MyBatis 实体/Mapper 置于 `io.roseiot.identity.persistence`；删除 `PlatformAdmin*`。
  - `io.roseiot.identity.application.AuthorizationService`（`hasPermission(userId, azv, key)`）。
  - **auth 编译切换**：`platformAdminId` → `userId`，Mapper 指向新表（**JWT 暂仍仅 sub/sid**；全量 IT 绿在 Slice 2）。
- **Verification**: `./mvnw -q test -Dtest=IdentityUserRbacMigrationIntegrationTest,V3UserMigrationIntegrationTest,SystemAdminSeedIntegrationTest` 与 `./mvnw -q compile`
- **Done**: true

> **说明**：Slice 1 不强制 `./mvnw verify` 全绿（auth IT 仍可能插旧表名）。**首次全量 `./mvnw -q verify` 在 Slice 2 完成时达成**（与 Spec 附录 B 一致）。

---

## Slice 2：Bootstrap 与测试夹具切到 User

- **Goal**：CLI 引导写入 `users` + `SystemAdmin`；所有测试不再依赖 `platform_admin`；**全量 verify 绿**。
- **Acceptance**: AC-1、AC-2
- **Depends on**: Slice 1
- **Test or proof**:
  - 更新 `BootstrapCliTests` / `BootstrapDuplicateCliTests` / `BootstrapUserServiceTests` → 断言 `users` + assignment。
  - `AbstractIt` 及 MockMvc 测试：helper `insertPlatformUser(email, password)` 写 users + assignment + 可选 disabled_at。
- **Implementation outline**:
  - `io.roseiot.identity.application.BootstrapUserService`：`users` insert + assignment；仍 **count (users where tenant_id is null) == 0** 才允许引导。
  - 删除旧 `BootstrapPlatformAdminService` 与 `PlatformAdminMapper` 引用。
  - 批量改 IT/MockMvc：Spec #2 套件在 **未改 JWT 前** 应已可 login/me（AC-4 预检）。
- **Verification**: `./mvnw -q verify`
- **Done**: true

---

## Slice 3：JWT ctx/azv、RBAC 守门与 azv  bump

- **Goal**：平台 JWT 与授权符合 IAM-012；错误 ctx 拒绝；改密 bump `azv` 并撤 refresh。
- **Acceptance**: AC-11、AC-12、AC-13；并为 AC-4～AC-10 提供实现基础
- **Depends on**: Slice 2
- **Test or proof**:
  - 解码 login JWT：含 `ctx`、`azv`（AC-11）。
  - 改密后 refresh 401 + DB `authorization_version` 增 1（AC-12）。
  - `IdentityAuthSecurityTests`（或 MockMvc）：用测试 `JwtEncoder` 签 `ctx=tenant` 访问 `/api/identity/me` → 403（AC-13）。
  - `UserRefreshSessions.issueAccessToken` 写入 claims；`JwtAuthenticationConverter` 校验 azv/ctx。
  - `IdentityMeController` / 改密：`AuthorizationService` 检查 permission。
  - recovery confirm：成功后 `authorization_version++` + revoke all refresh（Req 5）。
- **Implementation outline**:
  - 重命名 Session/Account/Recovery 服务为 `User*`（若 Slice 2 未做完）。
  - `io.roseiot.identity.infra.security`：`JwtAuthenticationConverter`（或等价组件，校验 `ctx` / `azv`）。
  - 可选：`@RequiresPermission` AOP 或 Controller 内显式调用（保持最小）。
- **Verification**: `./mvnw -q verify`
- **Done**: true

---

## Slice 4：Spec #2 行为回归与缺口补测

- **Goal**：Explicit 对照 Spec #2 Plan 端点与 AC-4～AC-10；补 **logout-all** MockMvc（若仍缺）。
- **Acceptance**: AC-4、AC-5、AC-6、AC-7、AC-8、AC-9、AC-10
- **Depends on**: Slice 3
- **Test or proof**: 现有 `IdentityAuthMockMvcTests`、`IdentityAuthTests`、`IdentityAuthRateLimitMockMvcTests` 全绿。
- **Implementation outline**: 仅修复回归失败；不新增业务端点。
- **Verification**: `./mvnw -q verify`
- **Done**: true

---

## Slice 5：README 与 Spec #1 修订

- **Goal**：文档与 AC-15；Bootstrap Spec 修订 log 指向 `users`。
- **Acceptance**: AC-15
- **Depends on**: Slice 4
- **Test or proof**: 人工检查 README JWT 段含 `ctx`、`azv`；迁移说明「V4 后需重新登录」一句。
- **Implementation outline**:
  - `README.md` 更新。
  - `docs/sdd/2026-10-08-stage-1-platform-bootstrap-spec.md` **Revision log** 追加：引导持久化目标为 `users`（实现以本 Plan 为准，不重写 Spec #1 AC）。
- **Verification**: `./mvnw -q verify`（无代码变更时仍跑一遍）
- **Done**: true

---

## Build log

- 2026-10-08 | Slice 1～3 | `./mvnw -q verify` | 通过（V4/V5、identity 包、JWT ctx/azv、`IdentityAuthJwtMockMvcTests` AC-11～13）
- 2026-10-08 | 权限键前缀 | `./mvnw -q verify` | `platform.*` → `identity.*`（V5/V6、`IdentityJwtClaims`）
- 2026-10-08 | Slice 5 | `./mvnw -q verify` | README 迁移说明、Bootstrap Spec revision、AC-15
