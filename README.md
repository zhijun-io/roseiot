# Roseiot

Roseiot 是一个面向物联网场景的多租户后端项目。目前处于建设阶段，重点是逐步交付可运行、可测试的业务闭环；当前代码尚未包含产品、设备、遥测或命令等 IoT 领域功能。

## 当前实现

当前代码主要包含：

- 多租户基础及租户管理；
- 用户登录、个人资料、密码修改和密码重置；
- 基于角色与资源 ACTION 的授权；
- PostgreSQL 持久化、Flyway 基线迁移和健康检查；
- 登录与密码重置接口的限流；
- RFC 9457 `ProblemDetail` 错误响应，稳定错误码通过 `code` 扩展字段提供；所有 HTTP 响应附带 `X-Request-Id`，错误响应也包含 `requestId` 扩展字段。

当前限流使用进程内滑动窗口，只适用于单实例演示；应用重启会清空计数，多副本之间也不共享额度。部署多副本或用于生产时，应在网关或共享限流存储中执行跨实例策略。

HTTP 请求体有独立于限流开关的 1 MiB 默认上限，可通过 `roseiot.http.max-request-body-bytes` 调整；登录和密码重置路由另有独立的 16 KiB 限制，可通过 `roseiot.http.max-authentication-request-body-bytes` 调整。超过对应上限的请求返回 `413 Payload Too Large`。

当前还没有首位管理员创建入口，因此全新数据库尚不能通过应用 API 创建第一个登录用户。用户邀请功能已移除。密码重置通知目前没有邮件投递服务；只有显式激活 `local` profile 并设置 `ROSEIOT_PASSWORD_RESET_LOGGING_ENABLED=true` 时，应用才会把重置链接写入日志。该选项包含一次性凭证，只能用于本地演示，禁止在共享或生产环境启用。重置链接依赖 `system_setting` 中的通用 `baseUrl` 设置，并将 token 放在 URL fragment 中；客户端应从 fragment 读取令牌，再通过请求体调用重置验证与确认接口。

## 技术栈

- Java 25
- Spring Boot 4.1.1
- Maven Wrapper
- PostgreSQL 18
- MyBatis-Plus 3.5.17
- Flyway

应用运行在 Java 25 上并启用 Spring 虚拟线程。JDBC 并发仍受 HikariCP 连接池限制；默认最大连接数为 10，可通过 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` 调整，并应依据数据库容量和实际负载评估。

## 本地运行

需要 JDK 25 和 Docker。默认启动方式由 Spring Boot Docker Compose 支持启动仓库中的 PostgreSQL 服务。
Spring Boot 默认在应用退出时停止 Compose 服务；再次启动应用时会重新启动数据库容器。手动管理数据库时可禁用 Compose 集成并使用现有 PostgreSQL。

```bash
cp .env.example .env
```

编辑 `.env`，为 `ROSEIOT_AUTH_JWT_SECRET` 设置至少 32 字节的随机密钥。JWT 的 `issuer` 和 `audience` 默认分别为 `https://roseiot.local` 与 `roseiot-api`；多环境部署应为每个环境设置独立 issuer。建议同时为本地 PostgreSQL 设置独立密码。
如需本地演示密码重置，必须同时将 `ROSEIOT_PASSWORD_RESET_LOGGING_ENABLED` 设为 `true` 并激活 `local` profile；默认关闭。

```bash
./mvnw spring-boot:run
```

仅在本地环境演示密码重置时启动：

```bash
SPRING_PROFILES_ACTIVE=local ROSEIOT_PASSWORD_RESET_LOGGING_ENABLED=true ./mvnw spring-boot:run
```

应用启动后，可检查健康状态：

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

如果使用已有 PostgreSQL 而不启动 Compose，在 `.env` 中设置 `SPRING_DOCKER_COMPOSE_ENABLED=false`，并提供 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`，然后运行相同的启动命令。

当前 Flyway 保留从空数据库创建结构的 V1 基线，并使用后续版本化迁移添加管理权限。V6 将遗留复数表名 `users` 重命名为 PostgreSQL 保留字 `user`，DDL、SQL 和 MyBatis-Plus 映射均对表名加双引号。该项目不包含更早开发版本数据库的完整升级路径；使用已有数据库前，请确认其 Flyway 历史与当前迁移兼容。

V4 为每个租户用户最多分配一个角色添加唯一索引；V5 为租户、用户凭据、用户角色、角色资源和资源父子关系添加外键约束；V6 将 `users` 改名为单数 `"user"`。升级已有数据库前，先检查重复角色关系和孤立关联；迁移不会自动删除或合并数据。

```sql
SELECT tenant_id, user_id, COUNT(*)
FROM user_role
GROUP BY tenant_id, user_id
HAVING COUNT(*) > 1;
```

如果查询返回记录，应先按业务决策处理角色分配，再运行迁移。V5 还会拒绝租户或关联记录缺失、以及跨租户的用户角色/资源父子关联；应在升级前检查并修复这些数据。

## 验证

完整验证需要 Docker，以便 Testcontainers 启动 PostgreSQL：

```bash
./mvnw -q verify
```

GitHub Actions 在 push、pull request 和手动触发时运行相同的 Maven Wrapper 验证，并保留 Surefire 与 Failsafe 报告。

## 代码组织

代码按业务能力和功能组织，主要包括：

- `iam.user`、`iam.role`、`iam.resource`：IAM 下的用户、角色和授权资源功能；`UserService` 实现 Spring Security 的 `UserDetailsService`，并通过 `SecurityUserLoader` 为 JWT 转换器加载当前用户和权限；认证查询数据保持在用户功能包内；
- `tenant`：租户管理和租户状态；
- `system`：系统设置；
- `config`：全局 Spring Security、MyBatis-Plus、Jackson 严格 JSON 解析、JWT 配置属性、时间和 API 异常处理配置；
- `shared.security`、`shared.mybatis`、`shared.webmvc`、`shared.ratelimit`：跨功能使用的身份模型、安全适配、持久化和 HTTP 基础设施。

项目采用模块化单体，不为尚未实现的 IoT 功能预建模块或通用框架。

## 文档

- [产品需求](docs/product/prd.md)
- [项目路线图](docs/roadmap.md)
- [架构决策记录](docs/adr/)
- [领域词汇](GLOSSARY.md)
- [开发与 Agent 工作规范](AGENTS.md)
