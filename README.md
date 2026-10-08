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

与 PRD 候选基线一致；依赖来自 Maven Central 正式发布版本。

## 本地配置

敏感信息只放在未跟踪的 `.env` 或 `application-local.*` 中。可参考
[`.env.example`](.env.example) 中的变量名，不要提交真实凭证。

## 文档

- [产品需求（PRD）](docs/product/prd.md)
- [产品访谈决策日志](docs/product/interview-decisions.md)
- [项目路线](docs/roadmap.md)
- [领域词汇](GLOSSARY.md)
- [Agent 规则](AGENTS.md)

## 验证与运行

集成测试需要 **Docker**（Testcontainers）。本地运行需要 **PostgreSQL** 与 `.env`。

```bash
cp .env.example .env
./mvnw -q verify
./mvnw spring-boot:run
```
