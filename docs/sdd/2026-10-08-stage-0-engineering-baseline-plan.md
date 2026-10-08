# 阶段 0 · 工程启动基线 Plan

> 状态：已交付

## 当前基线

- Maven Wrapper、Java 25、Spring Boot、PostgreSQL、Flyway 和 Testcontainers 构成单体后端的工程基线。
- `V1__baseline.sql` 直接创建当前 Identity、会话和 RBAC schema；仅支持空数据库。
- 配置使用标准 `spring.datasource.*` 与环境变量；`.env` 不纳入版本控制。
- Actuator 公开 liveness 与 readiness；健康响应不泄露凭证或连接信息。
- `./mvnw -q verify` 是完整验证入口；本地应用可通过 `./mvnw spring-boot:run` 启动。

## 验证

1. 在空 PostgreSQL 上运行 `./mvnw -q verify`。
2. 启动应用后访问 `/actuator/health/liveness` 与 `/actuator/health/readiness`。
3. 审查已跟踪文件，确认不含真实凭证。
