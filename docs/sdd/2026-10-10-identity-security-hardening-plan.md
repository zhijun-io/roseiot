# Identity 安全加固 Plan

**Spec:** [`2026-10-10-identity-security-hardening-spec.md`](2026-10-10-identity-security-hardening-spec.md)

## Risks / Dependencies

- access token 即时失效依赖数据库中的 refresh session 状态；每个受保护请求都会增加一次按 `sid` 查询。查询只读必要字段，不增加缓存或基础设施。
- refresh 与密码重置的“争取消费权、后续状态变更”必须处于同一数据库事务；并发语义需要 PostgreSQL 集成测试证明。
- 日志 notifier 只允许出现在显式本地配置中。非本地环境若缺少真实 notifier，应启动失败，不能回退到记录 token 的实现。
- 限速器仍是单进程实现；此 Plan 只回收过期 key，不改变限速语义，也不解决多实例共享状态。
- 已有工作区修改涉及 `SecurityConfig`、限速器和身份 Web 测试。实施时保留这些改动，并在其基础上合并本 Spec 的变更。

## Slice 1: 外部化 JWT 密钥并限制日志 notifier

- Goal: 应用只使用外部提供的有效 JWT 密钥；明文 token 日志 notifier 仅用于显式本地开发配置。
- Acceptance: AC-1, AC-2.
- Depends on: None.
- Test or proof: 覆盖密钥缺失、短于 256 位时启动失败，有效密钥时签名验证可用；验证生产配置在缺少真实 notifier 时失败，本地配置仍可装配当前手动验证 notifier。
- Implementation outline: 移除 `application.yml` 中固定 JWT 密钥默认值，保留现有配置属性长度校验；检查 notifier Bean 的条件与配置边界，只有本地 profile 创建 logging notifier；同步 README 的本地配置说明及生产所需配置。
- Verification: 运行相关身份配置测试；实现完成后运行 `./mvnw -q verify`。

## Slice 2: 让 access token 随 session 撤销立即失效，并收紧匿名路由

- Goal: 每次 bearer 认证同时确认 `sid` 对应 session 有效；匿名访问仅限明确列出的现有公开路由。
- Acceptance: AC-3, AC-4, AC-5, AC-8.
- Depends on: Slice 1.
- Test or proof: MockMvc 使用同一个 access token 验证登出前可访问、登出后返回 401；覆盖 logout-all、refresh 轮换、密码修改和重置后的旧 token；converter 覆盖 session 不存在、撤销、过期及用户不匹配；未知 `/api/noauth/**` 路径被拒绝。
- Implementation outline: 在 JWT 转换过程中读取 `sid` 对应的 session 状态，拒绝无效或不属于 `sub` 的 session；将 `/api/noauth/**` 前缀放行改成当前公开 endpoint 的显式匹配（包括 user-activation 的 GET/POST 占位路由），保持现有权限注解与响应处理方式。
- Verification: 运行相关 converter 与 MockMvc 测试，并运行 `./mvnw -q verify`。

## Slice 3: 原子消费 refresh 与密码重置 token

- Goal: 并发请求只能有一个请求消费同一 refresh/reset token；refresh 重放继续按现有规则撤销整个 token family。
- Acceptance: AC-6, AC-7.
- Depends on: Slice 2.
- Test or proof: 使用 PostgreSQL 集成测试并发提交同一 refresh token 和同一 reset token，分别断言最多一次成功，且失败竞争分支不会留下可用的后继 session 或重复密码变更。
- Implementation outline: 为 refresh session 和 reset token 增加以当前状态、有效期为条件的单条更新/消费操作；在同一事务内执行后继 token 签发或密码、授权版本、session 更新；保留现有 refresh 重放 family 撤销语义和异常提交策略。
- Verification: 运行相关 PostgreSQL 集成测试，并运行 `./mvnw -q verify`。

## Slice 4: 回收滑动窗口限速器中过期 key

- Goal: 超出限速窗口的 key 状态可被清理，避免不同账号持续请求造成过期记录永久堆积。
- Acceptance: AC-9, AC-10.
- Depends on: None.
- Test or proof: 扩展限速器单元测试，使用可控时间覆盖过期 key 清理和仍在窗口内的计数；保留既有账号限速行为。
- Implementation outline: 在现有 `SlidingWindowRateLimiter` 内以最小实现清除已无有效请求的 key，不增加调度器、依赖或新的通用清理框架。
- Verification: 运行 `SlidingWindowRateLimiterTest` 与 `RateLimitFilterTest`。

## Completion Evidence

- 所有 Slice 的针对性测试通过，完整 `./mvnw -q verify` 通过。
- README 准确记录 JWT 密钥、非本地 notifier 配置和本地手动验证步骤；未改变现有 API 请求及成功响应格式。
- Spec 的 AC-1 至 AC-10 均有对应测试或配置启动证据；不以未测量的性能数据作结论。
