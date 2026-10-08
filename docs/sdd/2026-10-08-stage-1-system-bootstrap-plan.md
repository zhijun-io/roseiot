# 阶段 1 · 系统引导 Plan

> 状态：已交付

1. 在 `identity.application` 实现 `BootstrapUserService`，由 `RoseiotApplication` 的 CLI runner 调用。
2. 使用环境变量接收凭证，校验并创建系统用户及 `SystemAdmin` 绑定。
3. 通过单元测试和 Testcontainers CLI 测试验证成功、重复引导和失败输出。

验证：`./mvnw -q verify`。
