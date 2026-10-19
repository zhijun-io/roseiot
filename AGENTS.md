# Roseiot Agent 工作规范

## 当前状态与文档

当前仓库已建立阶段 0 后端工程基线，尚未实现业务模块功能。开始工作前先阅读 [README.md](README.md)，并按任务读取：

- 业务背景：[访谈决策记录](docs/product/interview-decisions.md)
- 长期架构选择：[ADR](docs/adr/)
- 领域术语：[GLOSSARY.md](GLOSSARY.md)
- 建设方向：[路线图](docs/roadmap.md)，不替代当前需求或实现证据

文档记录产品方向与决策，不代表仓库已有实现。发现文档冲突时，先说明冲突及影响，再确认应更新的需求或决策。

## 后端重新开发

- 后端统一使用 Spring Boot；涉及设计、实现、调试、测试或部署时，必须先阅读本机全局 `spring-boot-developer` 技能（`~/.codex/skills/spring-boot-developer/SKILL.md`）并按任务加载专题指南。
- Roseiot 的目标平台包括管理、通知、IoT 和 AI 模块。管理模块负责用户、角色、资源和系统设置等平台管理能力；各模块随已确认需求逐步实现。
- 默认采用模块化单体。优先按功能保持内聚，不为未来模块预建空包或占位接口；跨功能通过有真实调用方的 Service API 协作，不直接互调 Mapper 或泄漏持久化实体。只有存在真实解耦需求时才用应用事件。
- 根据 Roseiot 的需求和后端功能规划逐步开发 Nextboard 页面与菜单。Nextboard 是独立的 Next.js Admin Dashboard，只使用本地演示数据，不调用 Roseiot API，也不依赖后端运行。
- Nextboard 源码位于同级项目，仓库中的 `nextboard` 软链接只用于查看；修改 Nextboard 时应在其实际项目目录内工作。
- 持久化沿用 MyBatis-Plus；新增或修改数据访问使用 MyBatis-Plus Mapper，不使用 `JdbcTemplate` 或 `NamedParameterJdbcTemplate`。
- Spring Boot 4 的 HTTP 集成测试使用 `RestTestClient`（基于 Spring `RestClient`）；不要用 JDK 自带 HTTP 客户端或 `TestRestTemplate` 测试本项目自己的 HTTP 端点。它与生产代码中的出站 HTTP 调用是不同场景，出站同步调用使用 Spring `RestClient` 或 HTTP Service Client。
- 优先保持功能内聚；不为形式统一引入空包、横向分层或没有调用方的抽象。无需套用严格 DDD 或六边形架构。
- Controller 负责协议映射；业务规则和事务边界放在合适的 Service 入口。只在存在真实解耦需求时使用应用事件；通知事件在相关事务提交后处理。
- API 使用资源路径及已确认的 HTTP 方法、状态码约束；服务端必须校验认证、授权、租户归属和状态转换，不信任客户端传入的租户标识或前端可见性。
- 若采用关系型数据库，schema 变更通过版本化迁移表达；迁移脚本不创建数据库函数或触发器。

## 工作流程

1. 修改前运行 `git status --short --branch`，识别并保护已有的暂存、未暂存和未跟踪修改。
2. 阅读与任务直接相关的文档、代码和测试；发现需求、文档与实现冲突时先说明。
3. 完成范围内最小且完整的改动，不顺带重构无关内容。
4. 运行与改动风险相称的验证，并如实报告命令和结果。

## Git 与安全

- 将工作区中的已有变更视为用户资产；不覆盖、回退或清理与当前任务无关的修改。
- 不使用破坏性 Git 操作，不通过 stash 或临时提交隐藏工作区状态。
- 未经明确要求，不创建提交、分支、发布或推送。
- 提交标题沿用仓库历史格式：`<type>: <中文说明>`。
- 不提交或输出 `.env`、密钥、Token、私钥或真实数据。

## Agent skills

### Issue tracker

Issues and specs live in GitHub Issues and use the `gh` CLI. Configure this repository's GitHub remote before using tracker commands. See `docs/agents/issue-tracker.md`.

### Triage labels

Use `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, and `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

This is a single-context repo with a root `GLOSSARY.md` and `docs/adr/`. See `docs/agents/domain.md`.
