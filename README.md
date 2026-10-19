# Roseiot

Roseiot 计划建设为面向 IoT 场景的多租户平台，逐步包含管理、通知、IoT 和 AI 等模块。管理能力涵盖用户、角色、资源和系统设置。后端采用 Spring Boot，并按 `spring-boot-developer` 技能开发。当前阶段建立可重复运行和验证的工程基线，不包含业务模块功能。

## 当前状态

- 阶段 0 后端基线已建立，尚未交付阶段 1 管理业务功能。
- Java 25、Spring Boot 4.1.1、Maven Wrapper、PostgreSQL 18、MyBatis-Plus 3.5.17。
- 构建与测试：`./mvnw test` 运行快速单元测试阶段；当前暂无业务单元测试。`./mvnw verify` 还会运行 PostgreSQL/Testcontainers 集成冒烟测试（需要 Docker）。
- 应用通过 Spring Boot Docker Compose 支持自动启动本地 PostgreSQL。首次运行先复制 `.env.sample` 为 `.env`，按需修改本地数据库密码；`.env` 不得提交。
- 本地启动：`./mvnw spring-boot:run`。健康检查：`http://localhost:8080/actuator/health`；探针为 `/actuator/health/liveness` 和 `/actuator/health/readiness`。
- Flyway 已启用，阶段 0 不包含业务迁移；业务 schema 从阶段 1 的首个真实用例开始维护。
- 产品访谈记录见 [interview-decisions.md](docs/product/interview-decisions.md)，建设方向见 [路线图](docs/roadmap.md)。
- 领域术语见 [GLOSSARY.md](GLOSSARY.md)，后端与管理后台边界见 [架构决策](docs/adr/2026-10-19-backend-and-nextboard.md)。
- Nextboard 是独立的 Next.js Admin Dashboard。随 Roseiot 功能规划逐步补充菜单与页面，使用本地演示数据，不调用 Roseiot 后端 API；`nextboard` 是指向同级项目的本地软链接。

## 文档

- [产品访谈决策](docs/product/interview-decisions.md)
- [路线图](docs/roadmap.md)
- [技术栈](docs/tech-stack.md)
- [架构决策](docs/adr/2026-10-19-backend-and-nextboard.md)
- [领域词汇](GLOSSARY.md)
- [开发与 Agent 工作规范](AGENTS.md)
- [变更历史](CHANGELOG.md)

## 本地环境

需要 JDK 25 和 Docker。可使用 mise 根据 `mise.toml` 安装 JDK；也可以自行安装兼容的 JDK 25。Maven 通过仓库 Wrapper 下载和运行，无需单独安装。

```bash
cp .env.sample .env
./mvnw test
./mvnw verify
./mvnw spring-boot:run
```

应用运行时，Spring Boot 会按 `compose.yaml` 启停 PostgreSQL。也可使用 `mise run compose:up` 和 `mise run compose:stop` 手动管理数据库。不要把真实凭证放入 `.env.sample` 或提交 `.env`。
