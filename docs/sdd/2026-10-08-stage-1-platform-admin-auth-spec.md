# 阶段 1 · Spec #2：平台管理员登录、会话与账户恢复

## Goal

已引导的平台管理员可通过 **HTTP API** 安全登录、刷新与注销会话；在忘记密码时可通过**可测试的恢复通道**完成一次性重置，且不扩大 Spec #1 的 CLI 引导面。

## Scope

- **IAM-007（平台管理员子集）**：邮箱 + 密码认证；短期访问令牌与服务端可撤销的刷新会话；密码修改与相关会话撤销。
- **IAM-008（本切片子集）**：登录与恢复路径的限速与**统一失败语义**（不暴露邮箱是否存在、成员/租户状态等区分信息）。
- **REST**：业务 HTTP API 统一 **`/api`** 前缀，路径中**不含主版本号**（无 `/api/v1`）；本 Spec 仅暴露认证与会话相关端点（见 Plan 固定路径）。主版本后续由 **Spring Boot API 版本化** 绑定，而非 URL 段。
- **主体**：平台侧用户（当前持久化为 **`users`（`tenant_id IS NULL`）** + `PlatformOperator`；自身份迁移起不再使用 `platform_admin` 表）；租户用户与成员登录留 Spec #3。
- **HTTP 路径（当前实现）**：认证 **`/api/identity/login`**、**`/token`**、**`/logout*`**、**`/recovery/*`**（与 **`/me/**`** 同前缀 **`/api/identity`**）；当前用户 **`/api/identity/me/**`**（见 README、`io.roseiot.identity.infra.security.SecurityConfig`）。
- **账户恢复**：单次使用、短期有效的恢复令牌；**Q208** 通知适配器（自动化测试用内存/本地捕获，不绑定商业 ESP）。
- **持久化**：Flyway 扩展会话与恢复请求所需最小表；MyBatis Plus 读写。
- **README**：记录认证端点、本地验证步骤与恢复流程（含测试适配器用法）。

## Non-goals

- 租户、成员、邀请、角色（Spec #3）；产品、设备、凭证（Spec #4～#5）。
- 公开注册、邮箱验证、用户自助改邮箱（**Q106** 窄化）。
- OAuth、短信、Passkey、企业 SSO、MFA（**Q63**、**Q198** 首版仅密码重认证留待后续）。
- 会话列表 UI、审计查询 UI、生产级邮件服务商绑定（恢复通道须可测试，但不要求上线 ESP）。
- 平台管理员 CLI 引导（Spec #1）；本 Spec 不新增引导入口。
- 为租户用户预建空用户表或「占位登录」。

## Current Context

- **Spec #1 已完成**：CLI `--bootstrap`（`BOOTSTRAP_USER_EMAIL` / `BOOTSTRAP_USER_PASSWORD`）写入 `users` + 角色绑定；bcrypt 摘要、`UuidV7`（`io.roseiot.shared.util.UuidV7`）；`SecurityConfig` 在 `roseiot.bootstrap.enabled=true` 时不加载。
- 技术栈：Java 25、Spring Boot 4.1.1、MyBatis Plus 3.5.17、PostgreSQL 18、Testcontainers（见 README 技术基线）。
- 术语见 [`GLOSSARY.md`](../../GLOSSARY.md)；平台管理员与后续「用户/租户成员」边界见 PRD **IAM-003**、**IAM-004**。

## Requirements

1. 平台管理员使用规范化邮箱与密码登录，成功则返回短期访问令牌与刷新会话凭证（刷新凭证不得出现在 URL）。
2. 使用有效刷新会话可获得新的访问令牌，并按规定**轮换**刷新会话；检测到刷新令牌重放时撤销会话族并要求重新登录（**Q195** 方向）。
3. 平台管理员可注销当前会话；可撤销自身全部刷新会话（「退出所有会话」）。
4. 登录失败、账户不存在、密码错误、账户不可用等情况对外返回**相同语义**的失败响应；具体原因仅进入服务端安全日志/审计（**Q192**）。
5. 对已认证平台管理员，可修改密码；修改成功后撤销该主体全部刷新会话（**Q194**）。
6. 账户恢复：提交邮箱后返回统一响应（**Q193**）；若存在对应平台管理员则生成单次、短期恢复令牌并通过通知适配器投递；令牌只存摘要；完成重置后令牌失效并撤销全部会话。
7. 访问令牌仅通过 `Authorization: Bearer` 传输（**Q200**）；本 Spec 的错误响应稳定、可测试，不泄漏堆栈、SQL 或令牌明文。
8. 新内部 ID 与 Spec #1 一致采用 **`UuidV7.generate()`**。

## Acceptance Criteria

- **AC-1**：`./mvnw -q verify` 退出码为 0（含本 Spec 相关集成测试）。
- **AC-2**：在已有平台管理员的前提下，按 README 调用登录 API，成功响应含访问令牌；使用该令牌访问受保护的「当前管理员 profile」只读端点成功，无令牌或无效令牌被拒绝。
- **AC-3**：登录后使用刷新会话获得新访问令牌；旧刷新令牌按 Spec/Plan 固定语义不可无限复用（含重放检测或轮换，由 Plan 选定一种可测策略）。
- **AC-4**：注销当前会话后，原刷新会话不能再换取访问令牌。
- **AC-5**：对不存在邮箱与错误密码的登录尝试，对外 HTTP 状态与 body 语义一致；测试断言响应不含「user not found」等与枚举相关的区分字段。
- **AC-6**：账户恢复：对已注册邮箱发起恢复，测试适配器捕获到含令牌的通知；使用该令牌设置新密码成功；同一令牌二次使用失败；全部刷新会话已撤销。
- **AC-7**：账户恢复：对未注册邮箱发起恢复，对外响应与 AC-6 请求阶段不可区分（状态码与稳定错误/成功语义由 Plan 固定）。
- **AC-8**：自动化测试使用 Testcontainers PostgreSQL，不依赖本机唯一数据库；恢复通知使用可编程测试 double（内存或本地 SMTP 测试容器），不依赖外部网络 ESP。
- **AC-9**：README 更新：列出本 Spec 端点路径、示例 curl、与 AC-2/AC-4/AC-6 等价的手动步骤。

## Constraints

- 模块化单体；构造器注入；Controller 只做协议映射与格式校验，业务规则在用例/领域边界（AGENTS.md）。
- PostgreSQL + Flyway；MyBatis Plus 与 Spec #1 一致。
- 密码存储延续 Spec #1 自适应哈希与 `password_algorithm` 列；参数升级路径保留。
- 登录与恢复限速的具体阈值在 Plan 列出；实现须可测试（如固定时钟或集成测试放宽阈值配置）。
- 默认拒绝：除 Spec #1 健康检查与本 Spec 声明的匿名认证/恢复入口外，不新增匿名业务 API。
- 刷新令牌与恢复令牌不得写入应用 INFO 日志；审计/日志规则与 **Q149** 方向一致。

## Decisions

- **选定**：Q492 第 2 切片；在租户能力（Spec #3）之前先闭合**平台管理员**认证与会话。
- **选定**：REST 业务面使用 **`/api/...`**，不在 URL 嵌入 `/v1` 等版本段；后续 Spec 沿用此前缀，主版本由 Spring Boot 版本化引入时再绑定控制器。
- **选定**：账户恢复按 **Q208** — 抽象通知端口 + 测试可替换实现；不将 SendGrid 等列为必选依赖。
- **选定**：**Q106** — 不做公开注册与租户成员自助找回；本 Spec 恢复对象仅为**已存在的平台管理员**。
- **拒绝**：长期不可撤销的无状态 JWT 作为唯一会话模型（**Q62**）。
- **拒绝**：在 URL 查询参数中传递访问令牌或刷新令牌（**Q200**）。

## Related ADRs

- [`docs/adr/2026-10-08-identity-user-rbac.md`](../../adr/2026-10-08-identity-user-rbac.md)：统一 User + RBAC-0 目标模型。
- [`docs/sdd/2026-10-08-stage-1-identity-user-rbac-migration-spec.md`](2026-10-08-stage-1-identity-user-rbac-migration-spec.md)（**已交付**）：自 `platform_admin` 收敛至 User/RBAC；对外 HTTP 以 README 当前路径为准（认证扁平化至 `/api/identity/login` 等）。
- [`docs/sdd/2026-10-08-stage-1-tenant-and-members-spec.md`](2026-10-08-stage-1-tenant-and-members-spec.md)（**Spec #3，待批准**）：租户生命周期与租户用户会话；不在本 Spec 范围。

## Open Questions

- 无（访问/刷新寿命、轮换细节、限速数值、profile 端点路径留 **Plan**）。

## Revision log

- 2026-10-08 | 初稿：Q492 第 2 切片、平台管理员范围、/api/v1、Q106/Q208 边界 | none | no — 待用户批准
- 2026-10-08 | 用户批准 Spec | none — clarification | no — 进入 Plan
- 2026-10-08 | 取消 URL `/api/v1` 前缀，改由后续 Spring Boot API 版本化 | none — clarification | no — Plan/实现同步
- 2026-10-08 | 固定 REST 前缀 `/api`（无 URL 版本号） | none — clarification | no — Plan/实现同步
- 2026-10-08 | 身份迁移后 HTTP 前缀为 `/api/identity/**`；持久化收敛至 `users` | 见 identity-user-rbac-migration-spec | no — README/Plan 同步
