# Roseiot

Roseiot 是一个面向物联网场景的多租户后端项目。目前处于建设阶段，重点是逐步交付可运行、可测试的业务闭环；当前代码尚未包含产品、设备、遥测或命令等 IoT 领域功能。

## 当前实现

当前代码主要包含：

- 多租户基础及租户管理；
- 用户登录、个人资料、密码修改和密码重置；
- 基于角色与资源 ACTION 的授权；
- PostgreSQL 持久化、Flyway 基线迁移和健康检查；
- 登录与密码重置接口的限流。

当前还没有首位管理员创建入口，因此全新数据库尚不能通过应用 API 创建第一个登录用户。用户邀请功能已移除。密码重置通知目前使用日志实现，不是邮件投递服务；重置链接依赖 `system_setting` 中的通用 `baseUrl` 设置。

## 技术栈

- Java 25
- Spring Boot 4.1.1
- Maven Wrapper
- PostgreSQL 18
- MyBatis-Plus 3.5.17
- Flyway

## 本地运行

需要 JDK 25 和 Docker。默认启动方式由 Spring Boot Docker Compose 支持启动仓库中的 PostgreSQL 服务。

```bash
cp .env.example .env
```

编辑 `.env`，为 `ROSEIOT_AUTH_JWT_SECRET` 设置至少 32 字节的随机密钥。建议同时为本地 PostgreSQL 设置独立密码。

```bash
./mvnw spring-boot:run
```

应用启动后，可检查健康状态：

```bash
curl -fsS http://localhost:8080/actuator/health/liveness
curl -fsS http://localhost:8080/actuator/health/readiness
```

如果使用已有 PostgreSQL 而不启动 Compose，在 `.env` 中设置 `SPRING_DOCKER_COMPOSE_ENABLED=false`，并提供 `SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME` 和 `SPRING_DATASOURCE_PASSWORD`，然后运行相同的启动命令。

当前 Flyway 只保留从空数据库创建结构的 V1 基线，不包含旧开发版本的升级迁移。使用已有数据库前，请先确认其状态与当前基线兼容。

## 验证

完整验证需要 Docker，以便 Testcontainers 启动 PostgreSQL：

```bash
./mvnw -q verify
```

## 代码组织

代码按业务能力和功能组织，主要包括：

- `iam.user`、`iam.role`、`iam.resource`、`iam.security`：用户、授权资源与认证；
- `tenant`：租户管理和租户状态；
- `system`：系统设置；
- `shared`：跨功能使用的安全、MyBatis 和 HTTP 支持代码。

项目采用模块化单体，不为尚未实现的 IoT 功能预建模块或通用框架。

## 文档

- [产品需求](docs/product/prd.md)
- [项目路线图](docs/roadmap.md)
- [架构决策记录](docs/adr/)
- [领域词汇](GLOSSARY.md)
- [开发与 Agent 工作规范](AGENTS.md)
