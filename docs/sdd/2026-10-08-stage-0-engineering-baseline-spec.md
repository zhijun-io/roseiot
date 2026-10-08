# 阶段 0：工程启动基线

## Goal

在全新环境中，任何人都可以按文档完成依赖解析、构建验证、启动应用、区分存活/就绪健康检查、对空 PostgreSQL 执行可重复迁移，且仓库中不出现凭证泄漏。

## Scope

- 验证并固定 **Java、Spring Boot、Maven** 的实际可用版本组合（以候选 Java 25、Spring Boot 4.1.1 为起点，验证失败时选用最近稳定组合并记录）。
- **单个 Maven 工程**、单个可运行 Spring Boot 应用（不拆 Maven module）。
- 根包 `io.roseiot`，仅建立 `RoseiotApplication` 与阶段 0 所需的最小支撑包（配置、健康、迁移）；不创建 identity、device、telemetry 等业务空包。
- **类型化配置**（`@ConfigurationProperties`）加载服务端口、数据源等；凭证与密钥仅来自环境变量或本地未跟踪配置（见 README / `.env.example`）。
- **Spring Boot Actuator**：对外暴露最小健康能力，**区分 liveness 与 readiness**；就绪检查包含当前阶段必需的 PostgreSQL 连通性。
- **Flyway**：对空库可重复执行迁移；迁移身份与应用运行时身份分离（配置层面区分，即使阶段 0 仅一条占位迁移）。
- **测试入口**：`./mvnw verify` 包含至少一个不需要完整业务的单元测试，以及验证迁移 + 应用上下文/就绪行为的自动化测试（允许 Testcontainers 提供 PostgreSQL）。
- **README**：记录经执行验证的启动、健康检查、测试命令及 JDK 要求。

## Non-goals

- 用户/租户/设备/物模型/MQTT/规则/告警等业务 API 与领域模块。
- 多 Maven module、微服务、消息队列、缓存、gRPC、独立 Starter。
- 生产级 CI/CD、Kubernetes、分布式追踪、完整监控栈。
- Docker Compose 或部署脚本（可在 README 中文字说明本地 PostgreSQL 前提，但不作为本 Spec 交付物）。
- 统一 HTTP 错误信封、API 版本前缀（阶段 1 起再引入）。

## Current Context

- 仓库处于规划阶段：**无** `pom.xml`、无可运行应用；已有 PRD、路线图、`GLOSSARY.md`、`AGENTS.md`。
- PRD 阶段 0 状态为「待编写 Spec」；访谈 **Q491** 确认首份 Spec 仅覆盖工程基线（Q491）。
- 访谈 **Q119** 建议单 Maven 工程；**Q120** 建议顶层包按能力而非全局 `controller/service/repository` 分层。
- 访谈 **Q118** 要求验证 Actuator、迁移、PostgreSQL 驱动、Validation、Security 等依赖可解析且可启动。
- 访谈 **Q246–Q247、Q145–Q146** 要求存活/就绪分离，公开健康接口不泄露依赖地址、配置与堆栈。
- 术语见根目录 [`GLOSSARY.md`](../../GLOSSARY.md)；本阶段不引入新的领域概念。

## Requirements

1. 工程使用 Maven Wrapper，克隆后在文档声明的 JDK 上可 `./mvnw verify`。
2. 应用可本地启动并监听可配置端口（默认 8080）。
3. 未认证调用方可访问**最小必要**的 liveness；readiness 反映 PostgreSQL 是否满足启动要求。
4. 空 PostgreSQL 上迁移可成功；再次执行不产生破坏性重复应用。
5. 跟踪文件中不得含真实凭证；配置加载方式与 `.env.example` 一致。
6. README 中的命令与版本号与 `pom.xml` 及实际验证结果一致。
7.  classpath 上包含阶段后续切片将依赖的基础 starter（Security、Validation、Actuator、PostgreSQL、Flyway），且默认安全策略不会阻止健康检查所需匿名访问。

## Acceptance Criteria

- **AC-1**：在仅安装文档声明的 JDK、Maven 使用 Wrapper 的前提下，于仓库根目录执行 `./mvnw -q verify` 退出码为 0（含编译与测试）。
- **AC-2**：按 README 启动应用后，对 liveness 端点发起未认证 GET 请求，HTTP 状态为 2xx，响应体不包含数据库 URL、用户名、密码、堆栈跟踪或完整环境配置。
- **AC-3**：在 PostgreSQL 可用且配置正确时，readiness 端点 GET 返回 2xx；在 PostgreSQL 故意不可达时，readiness 返回非 2xx（或 Actuator 等价语义），且 liveness 仍可为 2xx。
- **AC-4**：对空 PostgreSQL 实例执行迁移后，Flyway 历史表存在且状态为成功；在同一库上再次启动应用或重复迁移流程不会导致迁移失败。
- **AC-5**：对仓库已跟踪文件执行静态检查（如 `rg` 搜索常见密钥模式或人工审查 `.env` 未入库），无真实 datasource 密码、API 密钥或私钥；`application-local.*` 与 `.env` 均在 `.gitignore` 内。
- **AC-6**：README 包含 `./mvnw verify`、启动应用、curl liveness/readiness 的完整命令；Spec 批准者在合并前已按 README 跑通一次并可在 PR 中说明。
- **AC-7**：README「技术基线」小节列出 **实际** 写入 `pom.xml` 的 Java 与 Spring Boot 版本；若与 PRD 候选版本不同，用一句话说明验证结论（兼容/降级原因）。

## Constraints

- 默认 **模块化单体**：单应用、单模块；包结构为后续 `identity` 等能力预留子包，阶段 0 不创建无代码包。
- 主应用类位于 `io.roseiot` 根包；禁止 default package。
- 构造器注入；不在阶段 0 引入字段注入或 `ApplicationContext` 查找业务依赖。
- PostgreSQL 为唯一目标数据库；迁移工具 Flyway；不在阶段 0 引入 ORM 以外的第二套 schema 管理。
- 健康与管理端点遵循最小暴露：仅启用当前需要的 Actuator 端点；敏感 actuator 不对匿名公网开放。
- 若 Java 25 或 Spring Boot 4.1.1 在公共仓库不可用，**不得** 使用快照/里程碑依赖硬撑；改用验证通过的最近稳定组合，并满足 AC-7。
- 测试优先最小 Spring 上下文：纯逻辑单元测试不启动容器；验证迁移/就绪的测试可使用 Testcontainers 或文档等价的本地 PostgreSQL，但 AC-1 不得依赖「仅作者机器上手动存在的数据库」而无自动化覆盖。

## Decisions

- **选定**：首版工程基线 Spec 范围与访谈 Q491 一致，不提前编写阶段 1 行为。
- **选定**：Flyway + PostgreSQL 驱动 + Testcontainers（或等价自动化 DB）作为迁移与就绪验证路径。
- **拒绝**：阶段 0 拆多 Maven module（Q119）。
- **拒绝**：阶段 0 创建业务占位 Controller/Service/Repository 分层目录。

## Related ADRs

- 无。若实现中 Java/Spring 版本与 PRD 候选值不一致，可在实现 PR 中提议追加 `docs/adr/` 记录版本决策；非本 Spec 阻塞项。

## Open Questions

- 无。

## Revision log

- 2026-10-08 | 用户批准 Spec | none — clarification | no — 进入 Plan
