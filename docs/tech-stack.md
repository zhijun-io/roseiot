# Roseiot 技术栈

本文记录项目已确定的技术边界和阶段 0 已纳入工程基线的版本。实际组合通过 Maven 构建及 PostgreSQL/Testcontainers 集成验证后锁定。

## 已确定的边界

| 范围 | 技术与约束 | 状态 |
| --- | --- | --- |
| 后端框架 | Spring Boot 4.1.1，按 `spring-boot-developer` 技能开发 | 阶段 0 基线 |
| 应用架构 | 模块化单体；按管理、IoT、通知、AI 的路线逐步交付，不预建空模块 | 已确定 |
| 持久化 | MyBatis-Plus Mapper；不使用 `JdbcTemplate` 或 `NamedParameterJdbcTemplate` | 项目约束 |
| 数据库变更 | 使用 Flyway SQL 迁移；迁移脚本不创建数据库函数或触发器 | 项目约束 |
| 管理后台模板 | 独立 Next.js Admin Dashboard，使用本地演示数据，不调用 Roseiot 后端 API | 已确定，源码由 Nextboard 项目维护 |

## 阶段 0 锁定的基线

| 项目 | 当前方向 | 锁定方式 |
| --- | --- | --- |
| Java/JDK | Temurin JDK 25 | Maven Wrapper 构建验证 |
| Spring Boot | 4.1.1 | 与 MyBatis-Plus 及 PostgreSQL 驱动共同构建验证 |
| 构建工具 | Maven Wrapper，Maven 3.10.0 | `./mvnw test` / `./mvnw verify` |
| 关系数据库 | PostgreSQL 18（本地 Docker Compose 与集成测试） | 以真实 PostgreSQL 验证基线启动和健康检查；业务 Mapper/事务在对应模块验证 |
| 测试基础设施 | JUnit、Spring Boot Test、Testcontainers 2.x | `./mvnw verify`，需要可用 Docker |
| HTTP 层 | Spring MVC | 最小应用与 Actuator 健康检查 |
| 数据迁移 | Flyway（PostgreSQL 数据库支持） | 已启用；阶段 0 不含业务迁移脚本 |

具体版本和依赖以阶段 0 实际验证结果及仓库构建配置为准。Spring Boot 管理的依赖尽量使用其 BOM 版本，不重复覆盖版本。

## 随模块启用的技术

以下技术只在对应业务切片进入实施、并完成最小验证后加入，不属于当前环境基线：

- MQTT：阶段 2 的设备接入切片。
- WebSocket：阶段 2 的命令反馈与实时状态切片。
- 消息队列、缓存及其他基础设施：仅在可靠性或性能验证显示现有方案不足时选择。
- AI 模型及运行方式：阶段 4 有明确场景、数据条件和验收指标后再决定。

## 技术栈边界

Nextboard 与 Roseiot 后端独立构建和运行。Nextboard 页面可随产品模块规划演进，但演示数据不代表后端状态，也不构成 API 契约。其 Node.js、Next.js 和组件依赖版本由 Nextboard 自身仓库管理，不在本文锁定。

本文件与 [路线图](roadmap.md) 和 [后端与 Nextboard 架构决策](adr/2026-10-19-backend-and-nextboard.md) 配合阅读；如阶段 0 验证改变了技术选择，应同步更新本文及必要的 ADR。
