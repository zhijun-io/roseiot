# Roseiot

Roseiot 是一个面向跨行业 IoT 平台运营方与租户的多租户平台，计划逐步提供平台管理、IoT、通知和 AI 能力。后端使用 Spring Boot 开发，项目仓库位于 [GitHub](https://github.com/zhijun-io/roseiot)。项目采用 [Apache License 2.0](LICENSE)；公开分发前，请将 LICENSE 中的版权持有人占位符替换为实际权利人。

## 项目状态

阶段 0「环境与工程基线」已完成。当前仓库提供可启动、可测试的后端工程骨架，尚未实现阶段 1 管理模块或后续业务功能。规划中的产品能力不代表已经交付。

仓库根目录的 `renovate.json` 由 Renovate 根据依赖清单与工具文件自动识别技术栈，不自定义宽泛的依赖分组。稳定依赖的 patch/minor/pin 更新通过检查后可自动合并；major 和 0.x 更新需人工审核。自动合并前需在 GitHub 设置必需状态检查，并启用 Renovate GitHub App 或自托管服务。

当前主要技术：

- Java 25、Spring Boot 4.1.1、Maven Wrapper
- PostgreSQL 18、Flyway、MyBatis-Plus 3.5.17
- Docker Compose 用于本地 PostgreSQL

## 需求与开发流程

产品需求使用 [PRD 生成提示词](docs/prd-generation-prompt.md)整理并确认，结果记录在 [产品需求文档](docs/prd.md)。后续以已确认的 PRD 为依据创建模块或垂直切片 Spec；Spec 细化用户流程、业务规则、API、领域模型和验收标准，并发布到 GitHub Issues 跟踪。确认 Spec 后再制定实现计划并开发。

```text
PRD 生成提示词 → PRD → Spec（GitHub Issue）→ Plan → 实现与验证
```

Spec、PRD 与实现不一致时，先确认产品需求并更新相应文档，不从技术设计或规划内容推断未确认的产品规则。开发 Agent 的项目约定见 [AGENTS.md](AGENTS.md)。

## 本地运行

需要 JDK 25 和 Docker。可使用 [mise](https://mise.jdx.dev/) 按 `mise.toml` 安装 Java；Maven 通过仓库自带的 Wrapper 运行。

1. 准备本地环境变量文件：

   ```bash
   cp .env.sample .env
   ```

   按需修改本地数据库配置。`.env` 仅供本机使用，不要提交真实凭证。

2. 运行测试或完整验证：

   ```bash
   ./mvnw test
   ./mvnw verify
   ```

   `verify` 会运行 PostgreSQL/Testcontainers 集成测试，需要 Docker 可用。

3. 启动应用：

   ```bash
   ./mvnw spring-boot:run
   ```

   Spring Boot Docker Compose 支持会按 `compose.yaml` 启动本地 PostgreSQL。也可以单独管理数据库：

   ```bash
   mise run compose:up
   mise run compose:stop
   ```

## 运行状态检查

应用默认运行在 `http://localhost:8080`。Actuator 健康检查和探针：

- `GET /actuator/health`
- `GET /actuator/health/liveness`
- `GET /actuator/health/readiness`

阶段 1.1 的首位系统管理员初始化已通过 Flyway 提供身份与 RBAC 基础 schema，并创建默认租户和受保护的系统管理员角色。尚无系统权限目录，当前内置角色不关联权限；首位管理员需使用[受控初始化流程](docs/operations/bootstrap-system-admin.md)，尚未提供登录或管理 API。

## 文档

- [产品需求文档（PRD）](docs/prd.md)
- [PRD 生成提示词](docs/prd-generation-prompt.md)
- [架构决策记录（ADR）](docs/adr/README.md)
- [领域词汇表](GLOSSARY.md)
- [开发与 Agent 工作规范](AGENTS.md)
- [变更历史](CHANGELOG.md)

Nextboard 是独立的 Next.js Admin Dashboard 模板项目，随 Roseiot 产品需求逐步补充页面，使用演示数据，不调用 Roseiot 后端 API。仓库中的 `nextboard` 是指向同级项目的本地软链接。
