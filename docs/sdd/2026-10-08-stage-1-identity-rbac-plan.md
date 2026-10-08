# 阶段 1 · Identity 与 RBAC Plan

> 状态：已交付

1. 在单一 Flyway 基线中创建 User、会话、恢复和 RBAC 表，并写入当前 permission 与内置角色 seed。
2. 将持久化置于 `identity.persistence`，服务置于 `identity.application`，安全实现置于 `identity.infra.security`。
3. 由 `AuthorizationService` 在系统 profile 边界求值 permission；JWT converter 校验 `ctx` 与 `azv`。
4. 使用空库集成测试、seed 测试和认证 MockMvc 测试验证。

验证：`./mvnw -q verify`。
