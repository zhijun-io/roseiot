# Identity 安全加固 Spec

> 状态：已批准（2026-10-10）

## Problem Statement

当前认证实现存在几处安全边界不完整：缺少环境变量时会使用仓库内固定 JWT 密钥；默认密码重置和成员邀请 notifier 会将可直接使用的 token 链接写入日志；refresh 与密码重置 token 的单次消费依赖先查后改，无法防止并发重复消费；登出只撤销 refresh session，已签发的 access token 仍可使用至过期。

## Solution

外部化 JWT 签名密钥，阻止生产配置回退到已知密钥；限制日志 notifier 只用于明确的本地开发环境；以数据库原子状态转换保护一次性 token；认证每个 access JWT 时确认对应服务端 session 仍有效，使单会话登出、全局登出、refresh 轮换和密码重置后的 access token 立即失效。清理过期的本地限速状态，避免旧 key 永久滞留。

## User Stories

1. 作为平台运维者，我希望应用缺少有效 JWT 密钥时拒绝启动，以免部署意外使用公开的固定密钥。
2. 作为平台用户，我希望密码重置和成员邀请 token 不进入生产日志，避免日志访问者取得账户或租户访问权。
3. 作为用户，我希望登出当前会话后，同一个 access token 立即不能再访问受保护资源。
4. 作为平台用户，我希望 logout-all、密码修改、密码重置和 refresh 轮换撤销的 session，其旧 access token 立即失效。
5. 作为账户恢复用户，我希望一个有效重置 token 在并发请求下也只能成功使用一次。
6. 作为登录用户，我希望一个 refresh token 在并发请求下也只能成功轮换一次，重放仍按现有策略撤销该 token family。

## Implementation Decisions

- JWT 密钥只从外部配置读取；缺失或不足 256 位时，Web 应用启动失败。仓库不提供可用于签名的固定默认值。
- 日志 notifier 只在显式本地开发配置中提供，以保留当前手动验证方式；生产启动必须提供真实通知实现，不得回退到会记录 token 的实现。
- JWT 资源服务器验签后，认证转换还须校验 `sid` 对应的 session 存在、未撤销、未过期且属于 `sub` 用户。session 不满足条件时按无效 bearer token 返回 401。
- refresh 轮换和密码重置采用数据库条件更新争取一次性消费权；更新未命中的请求不得签发后继 token 或修改密码。refresh token 重放继续撤销原 family。
- 本地滑动窗口限速器回收超过窗口的 key 状态；不新增共享缓存或分布式限速依赖。
- 公共 SecurityFilterChain 只允许当前明确需要匿名访问的路由；未知 `/api/noauth/**` 路径不自动获得匿名访问权。
- 不改变现有密码规则、token 有效期、API 请求和成功响应格式。

## Acceptance Criteria

- **AC-1**：未设置 JWT 密钥，或密钥不足 256 位时，Web 应用启动失败；配置有效密钥时可正常启动和签发、验证 JWT。
- **AC-2**：生产配置不能创建会输出密码重置或邀请明文 token 的 notifier；本地开发配置仍能完成当前手动验证流程。
- **AC-3**：同一 access token 在单会话登出前可访问获授权资源；登出后立即返回稳定的 401 错误。
- **AC-4**：logout-all、密码修改、密码重置和 refresh 轮换后，相关旧 access token 均立即失效。
- **AC-5**：服务端拒绝不存在、已撤销、已过期或用户归属不匹配的 `sid`；请求不会进入 Controller 业务逻辑。
- **AC-6**：并发提交同一 refresh token 时，至多一个请求可完成轮换；重放按现有策略撤销 token family，不能留下可继续使用的竞争分支。
- **AC-7**：并发提交同一密码重置 token 时，至多一个请求修改密码、递增授权版本并撤销 refresh sessions。
- **AC-8**：未知匿名路径不会因为匹配 `/api/noauth/**` 前缀而绕过认证默认拒绝规则。
- **AC-9**：限速窗口外的旧 key 状态可被回收；本地限速器不保留永久增长的过期记录。
- **AC-10**：现有登录、refresh、logout、logout-all、密码重置、邀请和权限错误响应契约继续通过对应测试。

## Testing Decisions

- 以 `SecurityConfigWebMvcTest` 或现有身份 Web MVC 测试作为最高层认证行为 seam：验证同一 access token 登出前成功、登出后 401，以及未知匿名路由被拒绝。
- 扩展 JWT converter 测试，覆盖缺失、撤销、过期、用户不匹配的 session；断言均映射为认证失败而非 500。
- 使用 PostgreSQL 集成测试并发验证 refresh 与密码重置 token 的单次消费，避免仅用 mock 证明数据库竞争语义。
- 测试启动配置拒绝缺少/过短密钥，并验证生产配置不会装配日志 notifier；本地手动验证配置仍可装配。
- 限速器测试覆盖窗口过期后的状态回收，同时保留已有按账号限速行为。

## Out of Scope

- 引入 Redis 或多实例共享限速器。
- 更换 JWT 算法、增加第三方身份提供商或改变 access/refresh token 格式和有效期。
- 实现邮件、短信或其他具体通知供应商；生产环境只要求显式提供非日志 notifier。
- 改变 tenant/user 授权规则、RBAC 数据模型、密码策略或公开 API 成功契约。

## Further Notes

- 每个受保护请求会增加一次按 session ID 查询有效 session 的校验。实现时应保持查询只取必要列，并通过现有请求级测试验证行为；不宣称未经测量的性能指标。
- 当前数据库 session 表是撤销状态的事实来源；实现需确保 session 状态更新与 refresh/reset token 消费处于同一事务。
- `/api/noauth/password-reset?resetToken=` 仍将 token 放在查询参数中。代理访问日志和浏览器历史的处理不在本 Spec 范围内，需另行决定是否调整外部契约。
