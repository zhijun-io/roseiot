# Roseiot

Roseiot 是一个逐步建设的物联网平台后端，用于学习真实 IoT 场景、验证工程
取舍，并形成可运行、可测试、可解释的项目证据。

## 当前状态

项目处于阶段 0：已有最小 Spring Boot 工程与数据库迁移入口。当前不承诺生产规模、
性能数据或完整平台能力。

## 建设原则

- 以业务闭环和可观察行为为交付单位；
- 默认采用模块化单体，不提前拆分微服务；
- 优先使用 Java、Spring Boot 和 PostgreSQL 的已有能力；
- 依赖和基础设施必须由当前问题驱动；
- 用测试、演示脚本和可复现基准证明结果。

## 技术基线（阶段 0 Slice 1 已验证）

| 项 | 版本 |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.1 |
| Maven | 3.9.9（Wrapper） |
| MyBatis Plus | 3.5.17（`mybatis-plus-spring-boot4-starter`，阶段 1 起） |

与 PRD 候选基线一致；依赖来自 Maven Central 正式发布版本。

## 本地配置

敏感信息只放在未跟踪的 `.env` 或 `application-local.*` 中。可参考
[`.env.example`](.env.example) 中的变量名，不要提交真实凭证。

配置加载顺序（简）：

1. **`spring-boot:run` + `compose.yaml`**：`JdbcConnectionDetails` 优先于 `application.yml` 中的 `SPRING_DATASOURCE_*` 占位符。
2. **`.env`**：经 `spring.config.import=optional:file:.env[.properties]` 从进程工作目录（通常在仓库根）加载；Compose 另用同一文件做 `POSTGRES_PASSWORD` 等变量替换。
3. **集成测试**：`spring.docker.compose.skip.in-tests=true`，不启动 Compose；Testcontainers + `@DynamicPropertySource` 注入 `spring.datasource.*`，不依赖本机 `.env`。

## 文档

- [产品需求（PRD）](docs/product/prd.md)
- [产品访谈决策日志](docs/product/interview-decisions.md)
- [项目路线](docs/roadmap.md)
- [领域词汇](GLOSSARY.md)
- [Agent 规则](AGENTS.md)

## 验证与运行

前置：**JDK 25**（与 `pom.xml` 一致）、**Docker**（Testcontainers 跑 `./mvnw verify`；本地启动需 Docker 以运行 `compose.yaml` 中的 PostgreSQL）。

```bash
./mvnw -q verify
cp .env.example .env   # 可选：覆盖 POSTGRES_PASSWORD
./mvnw spring-boot:run # 自动 docker compose up，并注入数据源连接
```

`spring-boot-docker-compose` 为 optional 依赖，打生产包时默认不包含。若已手动 `docker compose up` 同一文件，Boot 只会复用容器并建立 service connection，不会重复 up/stop。

**不用 Compose**：在 `.env` 中设 `SPRING_DOCKER_COMPOSE_ENABLED=false` 并填写 `SPRING_DATASOURCE_*`，再 `./mvnw spring-boot:run`（仓库根存在 `compose.yaml` 时默认仍会 compose up，须显式关闭）。

可选：`SERVER_PORT` 覆盖默认 8080。

健康检查（未认证，不返回连接串或凭证详情）：

```bash
curl -sS "http://localhost:8080/actuator/health/liveness"
curl -sS "http://localhost:8080/actuator/health/readiness"
```
