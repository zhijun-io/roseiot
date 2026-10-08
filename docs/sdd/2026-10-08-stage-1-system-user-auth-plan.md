# 阶段 1 · 系统用户认证 Plan

> 状态：已交付

1. 在 `identity.application` 实现登录、refresh 轮换、注销、恢复和改密服务。
2. 在 `identity.web` 暴露 `/api/auth/**` 和 `/api/users/me/**`；统一映射稳定错误响应。
3. 在 `identity.infra.security` 配置 JWT 签发、Resource Server 校验、`ctx`/`azv` 与权限检查。
4. 使用 MockMvc、Testcontainers 和纯业务单元测试覆盖成功、认证失败、令牌重放、权限拒绝和限速。

验证：`./mvnw -q verify`。
