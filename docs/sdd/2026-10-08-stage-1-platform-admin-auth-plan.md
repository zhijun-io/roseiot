# 阶段 1 · Spec #2：平台管理员登录、会话与账户恢复 Implementation Plan

**Spec:** [`docs/sdd/2026-10-08-stage-1-platform-admin-auth-spec.md`](2026-10-08-stage-1-platform-admin-auth-spec.md)

## 代码包结构（与访谈 Q120 / Q124 一致）

| 包 | 职责 |
| --- | --- |
| `io.roseiot.config` | 应用壳：Security 过滤器链、JSON 401/403 等横切装配 |
| `io.roseiot.shared.api` | 跨能力 HTTP 错误契约（`ApiErrorResponse`、全局 `@RestControllerAdvice`、Servlet 错误写入） |
| `io.roseiot.platform.auth` | 平台管理员认证用例、JWT 签发、领域异常 |
| `io.roseiot.platform.auth.api` | 认证 REST 适配（Controller、请求/响应 DTO）；能力内 `api`，非 shared |
| `io.roseiot.platform.persistence` | 平台阶段表与 MyBatis Mapper（后续能力拆分前临时归 `platform`） |

`shared` 仅放无业务所有权、稳定复用的最小内容；业务成功体仍用各能力 DTO，不放入 shared。

## Risks / Dependencies

- **Spring Security 6 + Boot 4.1.1**：Slice 1 须 `./mvnw verify` 证明 JWT/无状态资源过滤器与现有 `SecurityConfig`（引导模式条件装配）共存。
- **Spec #1 数据**：测试通过 Testcontainers + 引导用例或 Mapper 插入平台管理员，不依赖仓库凭证。
- **令牌密钥**：访问令牌签名密钥来自环境/配置（如 `ROSEIOT_AUTH_JWT_SECRET`），`.env.example` 占位，禁止默认硬编码生产密钥；测试使用固定 test secret。

## 本 Plan 固定的契约（Spec Open Questions 落地）

### HTTP 路径（均相对于应用根，端口默认 8080）

| 方法 | 路径 | 认证 |
| --- | --- | --- |
| POST | `/api/auth/login` | 匿名 |
| POST | `/api/auth/token` | 匿名（body 含 refreshToken） |
| POST | `/api/auth/logout` | Bearer（撤销当前 refresh 会话） |
| POST | `/api/auth/logout-all` | Bearer（撤销该管理员全部 refresh 会话） |
| GET | `/api/platform/me` | Bearer |
| PATCH | `/api/platform/me/password` | Bearer |
| POST | `/api/auth/recovery/request` | 匿名 |
| POST | `/api/auth/recovery/confirm` | 匿名 |

Actuator `/actuator/**` 保持 Spec #1 行为，不在 `/api` 下。

### API 前缀与版本

- 业务 REST 统一 **`/api`** 前缀；URL 中**不含** `/v1` 等主版本段。
- 引入 v1/v2 时通过 Spring Boot API 版本化（header/media type 等，实现阶段选定并文档化），而非 `/api/v1/...`。

### 令牌与寿命

- **访问令牌**：HS256 自包含 JWT（**Q196** 方向），claims 含 `sub`（platform_admin id）、`sid`（会话 id）、`exp`；**15 分钟**。
- **刷新令牌**：256-bit 随机 opaque 字符串；库中只存 **SHA-256 摘要**；**7 天**绝对过期；**单次轮换**（**Q195**）：每次 `POST /api/auth/token` 成功则旧 refresh 失效并签发新 refresh；若已失效 refresh 再次被提交，则撤销同 `family_id` 下全部会话。
- **恢复令牌**：256-bit 随机 opaque；库中只存摘要；**1 小时**有效；单次使用。

### 统一失败语义（登录）

- 邮箱不存在、密码错误、账户不可用：均 **401** + 同一稳定 JSON（如 `{"code":"invalid_credentials","message":"Invalid email or password"}`）；禁止 `user not found` 等字段。

### 账户恢复请求响应

- 无论邮箱是否注册：**202 Accepted** + 同一 JSON body（如 `{"message":"If an account exists, recovery instructions were sent"}`）；处理时间差在测试中不做严格 timing 断言。

### 限速（生产默认；测试可调高）

- 登录：同一邮箱（规范化后）**10 次 / 15 分钟** → **429**。
- 恢复请求：同一邮箱 **5 次 / 15 分钟** → **429**。
- 集成测试 `@TestPropertySource` 或 profile `test` 放宽/关闭限速 bean。

### 密码修改（Req 5）

- 新密码规则与 Spec #1 引导一致：≥12 字符、字母与数字；成功后 **撤销全部 refresh 会话**。

### 恢复通知（Q208）

- 端口 `RecoveryNotificationPort.sendRecoveryEmail(email, plainToken)`；默认实现 **LoggingRecoveryNotificationAdapter**（WARN 不含 token）；测试 **`InMemoryRecoveryNotificationAdapter`** 捕获 `(email, token)`。

## Slice 1：迁移、安全边界与统一错误格式

- Goal：Flyway `V3__platform_admin_auth.sql` 创建 `platform_admin_refresh_session`、`platform_admin_recovery_request` 最小表；全局 API 异常映射为稳定 JSON；`SecurityConfig` 放行匿名 `/api/auth/*` 入口，其余 `/api/platform/**` 与需认证的 `/api/auth/**` 需 Bearer。
- Acceptance: AC-1（基础）, AC-7（结构预留）
- Depends on: None
- Test or proof：迁移后上下文启动；匿名 `GET /api/platform/me` → 401 稳定 body；`./mvnw verify` 仍绿。
- Implementation outline：
  - 表：session（id UUID v7、admin_id、family_id、token_hash、expires_at、revoked_at、created_at）；recovery（id、admin_id、token_hash、expires_at、used_at、created_at）。
  - `@RestControllerAdvice` 或等价：不泄漏堆栈/SQL。
  - JWT 依赖：Spring Security OAuth2 Resource Server JWT 或 `jjwt`（Plan 实现时选 **Spring 官方 JWT 支持** 优先，避免多余依赖时再用 Nimbus + 手工过滤器）。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 2：登录与 profile（AC-2、AC-5）

- Goal：`POST /api/auth/login` 成功返回 access + refresh；`GET /api/platform/me` 返回当前管理员；错误登录统一 401 语义。
- Acceptance: AC-2, AC-5, AC-1
- Depends on: Slice 1
- Test or proof：Testcontainers → seed 管理员 → login → me；错误密码与不存在邮箱断言同 body 结构。
- Implementation outline：`PlatformAdminLoginUseCase`；refresh 会话入库；JWT 签发。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 3：刷新轮换与重放撤销（AC-3）

- Goal：`POST /api/auth/token` 轮换 refresh；提交已用过的 refresh → 401 且 family 全部撤销，新 refresh 亦不可用。
- Acceptance: AC-3, AC-1
- Depends on: Slice 2
- Test or proof：集成测试 login → refresh → 用旧 refresh 再请求 → 失败；新 refresh 亦失败。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 4：注销与改密（AC-4、Req 5）

- Goal：`POST /api/auth/logout` 撤销当前会话；`POST /api/auth/logout-all` 撤销全部；`PATCH /api/platform/me/password` 改密并撤销全部 refresh。
- Acceptance: AC-4, AC-1
- Depends on: Slice 3
- Test or proof：login → logout → refresh 401；改密后旧 refresh 无效。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 5：账户恢复（AC-6、AC-7、AC-8）

- Goal：recovery request/confirm + 内存通知适配器；重置后撤销全部会话；二次 confirm 失败。
- Acceptance: AC-6, AC-7, AC-8, AC-1
- Depends on: Slice 2（需已有 admin 与 session 表）
- Test or proof：捕获通知 token → confirm → 新密码 login；未注册邮箱 request 与已注册 response 一致；token 重用失败。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 6：README 与限速终验（AC-9）

- Goal：README 认证章节、`.env.example` 的 JWT secret 占位；可选手验 curl；审查无令牌入日志。
- Acceptance: AC-9, AC-1（终验）
- Depends on: Slice 5
- Test or proof：README 步骤与 AC-2/4/6 对齐；`rg` 审查跟踪文件无真实 secret。
- Verification：`./mvnw -q verify`
- Done: true

## AC 覆盖矩阵

| AC | 切片 |
| --- | --- |
| AC-1 | 1→6（终验） |
| AC-2 | 2 |
| AC-3 | 3 |
| AC-4 | 4 |
| AC-5 | 2 |
| AC-6 | 5 |
| AC-7 | 5 |
| AC-8 | 5 |
| AC-9 | 6 |

## Plan revision log

- 2026-10-08 | Spec 已批准，初版 Plan | none | no — 待用户批准 Plan
- 2026-10-08 | 用户批准 Plan | none — clarification | no — 进入实现
- 2026-10-08 | 路径去掉 `/api/v1`，API 版本留 Spring Boot 版本化 | none — clarification | no — 与 Spec revision 一致
- 2026-10-08 | REST 前缀固定为 `/api`（无 URL 版本号） | none — clarification | no — 与 Spec revision 一致

## Build log

- **Slice 1**（2026-10-08）：`V3__platform_admin_auth.sql`；`SecurityConfig` + `JsonSecurityHandlers`；OAuth2 Resource Server + `PlatformAuthJwtConfig`（`roseiot.auth.jwt.hmac-secret` / `ROSEIOT_AUTH_JWT_SECRET`）；`PlatformMeController` 占位 `GET /api/platform/me`；集成测试匿名 401 JSON + 表存在；REST 前缀 `/api`（无 URL 版本号）；共享 `io.roseiot.shared.api` 错误体（`ApiErrorResponse` + `ApiExceptionHandler` + `ApiErrorBodyWriter`）。验证：`./mvnw -q verify` 通过。
- **Slice 2**（2026-10-08）：`PlatformAdminLoginUseCase` / `PlatformAdminProfileUseCase`；`POST /api/auth/login`、`GET /api/platform/me`；refresh 会话入库 + JWT 签发；`PlatformAdminAuthMockMvcTests` / `PlatformAdminAuthTests`。验证：`./mvnw -q verify` 通过。
- **Slice 3**（2026-10-08）：`POST /api/auth/token` 轮换 refresh；重放已撤销 refresh 时撤销整 family（`noRollbackFor` 保证撤销提交）；`PlatformAdminRefreshUseCaseTests` + MockMvc 集成测试。验证：`./mvnw -q verify` 通过。
- **Slice 4**（2026-10-08）：`POST /api/auth/logout`、`/logout-all`；`PATCH /api/platform/me/password` 改密并撤销全部 refresh；MockMvc + 用例单元测试。验证：`./mvnw -q verify` 通过。
- **Slice 5**（2026-10-08）：`POST /api/auth/recovery/request`（202 统一 body）、`/confirm`（204）；`RecoveryNotificationPort` + 默认 logging 适配器；测试 `InMemoryRecoveryNotificationAdapter`；confirm 改密、标记 `used_at`、撤销全部 refresh；`OpaqueTokens` 复用于 login/refresh/recovery；MockMvc AC-6/7/8。验证：`./mvnw -q verify` 通过。
- **Slice 6**（2026-10-08）：README Spec #2 端点与 AC-2/4/6 curl；`.env.example` JWT/限速占位；登录与恢复内存限速（429 `rate_limited`，测试默认关闭）；`PlatformAdminAuthRateLimitMockMvcTests`。验证：`./mvnw -q verify` 通过。
