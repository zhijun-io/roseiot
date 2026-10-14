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

当前还没有首位管理员创建入口，因此全新数据库尚不能通过应用 API 创建第一个登录用户。用户邀请功能已移除。密码重置邮件通知默认关闭；配置 SMTP 并设置 `ROSEIOT_PASSWORD_RESET_EMAIL_ENABLED=true` 与发件人 `ROSEIOT_PASSWORD_RESET_EMAIL_FROM` 后，系统会在重置请求事务提交后异步发送邮件。SMTP 主机、端口、用户名、密码、认证与 STARTTLS 通过 `ROSEIOT_MAIL_*` 环境变量配置。邮件中的重置页面链接依赖 `system_setting` 中的通用 `baseUrl` 设置；一次性 token 作为邮件正文文本提供，客户端应通过请求体调用重置验证与确认接口，token 不放入 URL。SMTP 未送达不会改变密码重置请求结果；队列饱和时调用线程会执行投递并可能延长请求时间，进程异常退出时尚未处理的内存任务可能丢失。

## 技术栈

- Java 25
- Spring Boot 4.1.1
- Maven Wrapper
- PostgreSQL 18
- MyBatis-Plus 3.5.17
- Flyway

应用运行在 Java 25 上并启用 Spring 虚拟线程。JDBC 并发仍受 HikariCP 连接池限制；默认最大连接数为 10，可通过 `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` 调整，并应依据数据库容量和实际负载评估。

## 本地运行

需要 JDK 25 和 Docker。默认启动方式由 Spring Boot Docker Compose 支持启动仓库中的 PostgreSQL 与 Mailpit 服务。Mailpit 接收本地邮件但不会向外部地址投递：SMTP 使用 `localhost:1025`，邮件查看界面为 <http://localhost:8025>。Spring Boot 默认在应用退出时停止 Compose 服务；再次启动应用时会重新启动容器。手动管理依赖服务时可禁用 Compose 集成并使用现有 PostgreSQL 与 SMTP 服务。

```bash
cp .env.example .env
```

编辑 `.env`，为 `ROSEIOT_AUTH_JWT_SECRET` 设置至少 32 字节的随机密钥。JWT 的 `issuer` 和 `audience` 默认分别为 `https://roseiot.local` 与 `roseiot-api`；多环境部署应为每个环境设置独立 issuer。建议同时为本地 PostgreSQL 设置独立密码。
本地测试邮件时，在 `.env` 中设置 `ROSEIOT_PASSWORD_RESET_EMAIL_ENABLED=true` 和 `ROSEIOT_PASSWORD_RESET_EMAIL_FROM=roseiot@localhost`，其它 SMTP 默认值已指向 Compose 中的 Mailpit（无需认证或 TLS）。通过 <http://localhost:8025> 查看邮件。真实环境应使用部署密钥管理注入 SMTP 凭证，并按服务要求设置认证和 STARTTLS。

```bash
./mvnw spring-boot:run
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
