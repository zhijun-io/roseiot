# Roseiot Agent 工作规范

## 项目上下文

- Roseiot 是逐步建设的物联网平台后端，用于学习真实 IoT 场景、验证工程取舍，并形成可运行、可测试、可解释的项目证据。
- 阶段 0/1 已具备 `./mvnw`、Flyway 与 Testcontainers 集成测试；CI 以仓库实际配置为准。
- 当前只建设后端；前端若启动，统一放在 `ui/`。
- 默认采用模块化单体，按可独立验证的业务闭环逐步交付。
- Java 25、Spring Boot 4.1.1、Maven 和 PostgreSQL 目前只是候选基线，首次使用前必须验证可获得性、兼容性和实际收益。
- 先阅读 [`README.md`](README.md) 了解当前状态；路线方向见 [`docs/roadmap.md`](docs/roadmap.md)。

## 工作原则

- 优先级：性能 > 成本 > 复杂度 > 形式感。
- 性能优先只适用于存在明确约束或测量证据的场景；没有证据时，优先选择简单、直接、依赖少的实现。
- 坚持最小改动、最小依赖、最小抽象，遵循 Unix 哲学，避免过度设计。
- 默认从第一性原理出发并进行对抗式审核；发现错误、冲突和风险时直接指出。
- 保护用户已有修改，不扩大任务范围，不为假设中的未来需求提前建设。
- 不为了形式统一而复制错误、无效抽象或历史包袱。
- 技术名称不是交付成果；用可观察行为、测试和可复现数据证明结论。

## 仓库结构

| 路径 | 用途 |
| --- | --- |
| `README.md` | 项目定位、当前状态以及真实可用的启动和验证入口 |
| `docs/roadmap.md` | 阶段方向和待验证假设，不是已批准的实现规格 |
| `docs/product/prd.md` | 当前有效的产品需求总览和稳定需求 ID |
| `docs/product/interview-decisions.md` | 已确认的访谈建议和历史决策，只追加不改写 |
| `docs/agents/` | Issue tracker、triage 标签和领域文档规则 |
| `docs/sdd/` | 需要正式门禁的 Spec、Plan 和交付证据，按需创建 |
| `docs/adr/` | 重要且长期有效的架构决策，按需创建 |
| `GLOSSARY.md` | 已确认的领域词汇，按需创建 |
| `ui/` | 未来前端目录；当前任务未要求时不要创建 |

当前是单一上下文仓库，使用一个根目录 `AGENTS.md`。只有子目录形成不同的技术栈、验证流程或安全边界后，才增加嵌套 `AGENTS.md`。

不要为了匹配上述结构提前创建空目录或占位文件。

## 开始工作

开始修改前：

1. 运行 `git status --short --branch`，识别用户已有修改。
2. 阅读与任务直接相关的代码和文档。
3. 涉及产品功能、范围或业务规则时读取 `docs/product/prd.md`；有对应 Issue、Spec、Plan、ADR 或 `GLOSSARY.md` 时一并读取。
4. 检查仓库中的实际构建清单、Wrapper 和 CI，再决定可用命令。
5. 发现需求、文档和实现相互冲突时，先指出冲突，不要自行选择对用户影响较大的解释。

只查阅与当前任务有关的内容，不要求每次遍历全部文档或参考项目。

## 交付流程

- 默认按可独立验证的垂直切片交付。
- 每次改动都说明范围，并运行与风险相称的验证。
- 涉及以下内容时，先在 `docs/sdd/` 编写 Spec：
  - 公开行为或外部契约；
  - 数据模型或持久化语义；
  - 身份、权限、安全或租户边界；
  - HTTP、MQTT、WebSocket、gRPC 或消息协议；
  - 跨模块依赖或模块边界；
  - 重大基础设施和架构变化。
- Spec 经用户明确批准后再编写 Plan；Plan 经用户明确批准后再实现。
- 小型缺陷修复、文档调整和低风险维护可以直接执行，但不得借机扩大范围。
- 当变更已有 Spec 或 Plan 时，以对应文档定义的行为和验收标准为准。
- 提议与现有 ADR 冲突时，必须明确指出，不得静默覆盖。
- 不把单个任务的临时要求写入长期规范；将其保留在对应 Issue、Spec 或 Plan 中。
- 用户确认新的访谈建议后，追加到 `docs/product/interview-decisions.md`；若改变当前有效需求，同步更新 `docs/product/prd.md`。
- 新增或改变领域术语时同步更新 `GLOSSARY.md`；历史决策、当前需求、可执行 Spec 和 ADR 不相互混写。

## 构建与验证

- 只使用构建清单、Wrapper、CI 或项目文档中真实存在的命令。
- 默认可验证命令：`./mvnw -q verify`（见 `README.md`）；未运行测试时不得声称已验证。
- 不得编造 `mvn`、启动、测试、格式化或部署命令。
- 引入构建系统后，同步在 `README.md` 中记录经过实际执行的主要启动和验证命令。
- 优先运行覆盖本次风险的最小验证集；公共契约或共享模块变化再扩大验证范围。
- 如实报告已运行的命令和结果。未运行、跳过、失败或受环境限制的检查必须明确说明。
- 未经可复现测量，不得声称高性能、高并发、生产可用或达到特定规模。
- 测试应覆盖用户可观察行为和关键失败场景，不以测试数量作为质量指标。

## 架构约束

- 默认采用模块化单体，不提前拆分微服务。
- **能力分包 + 技术分层**（模块化单体内的默认布局）：
  - **多能力根包**：Roseiot 在 **`io.roseiot.<能力名>`** 下按业务闭环分包（如 **`iam`**、**`device`**、**`tenant`**、**`product`** 等，以 Spec 为准 **并列** 于同一模块化单体，**不**再用 `io.roseiot.platform` 中间层）。当前身份与访问管理能力位于 `io.roseiot.iam`；其它能力在 Spec 批准前 **不建空包**。
  - **主应用类**：`io.roseiot.RoseiotApplication` 留在根包；**禁止**在 `io.roseiot` 下全局铺 `controller` / `service` / `repository`，也 **禁止** 根级 `io.roseiot.config`。
  - **能力内分层**（按需创建子包，无调用方不建空包）：
    - `web` — HTTP：`@RestController`、请求/响应 DTO、能力内 `@RestControllerAdvice`；
    - `application` — 应用服务：`@Service`（命名 `*Service`，**不用** `*UseCase`）、事务边界、编排 Mapper 与 `infra`；
    - `domain` — 无 Spring 依赖优先：领域异常、值对象、纯规则工具；
    - `infra` — 技术实现：JWT、MQTT 适配、限速等可替换集成；
    - `config` — 该能力（或能力内子域）专属的 `@Configuration` / `@ConfigurationProperties`；
    - `persistence` — 该能力拥有的表与 MyBatis Mapper（能力独占表默认在此；见下条扫描）。
  - **`iam` 能力**（人类身份与访问管理）：实现位于 `iam.application`（`*Service`）、`iam.web`（Controller / `@RestControllerAdvice`）、`iam.domain`、`iam.infra.*`（如 `security`、`mybatis`、`passwordreset`）和 `iam.persistence`；会话、RBAC、`users` 表归属 `iam`。
  - **`shared`**：跨能力、无单一业务所有权 — **`shared.api`**、 **`shared.util`**、 **`shared.ratelimit`** 等；Security 过滤器链在 **`iam.infra.security`**（`SecurityConfig`、`SecurityExceptionHandler`）。
  - **MyBatis `@MapperScan`**：`io.roseiot.iam.infra.mybatis.MybatisPlusConfig` 扫描 **`io.roseiot.iam.persistence`**；UUID 类型处理器在 `iam.infra.mybatis`（见 `application.yml` 的 `mybatis-plus.type-handlers-package`）。
  - **能力间调用**：允许 `device.application` 依赖 `iam` 提供的授权 API；禁止循环依赖；跨能力 HTTP 契约以外部/Spec 为准。
  - **测试**：路径镜像主代码（如 `iam.web.*Tests`、`iam.application.*Tests`）。
- **不采用六边形架构**：不用 Port/Adapter 命名与「入站/出站端口」目录；分层是 **技术职责**，不是第二套架构词汇。
- 只有真实用例证明数据所有权、稳定契约和独立测试边界后，才新增模块。
- 优先完成最小业务闭环，不创建空模块、占位接口或没有当前调用方的通用框架。
- 引入依赖前，先确认 JDK、Spring 或已有依赖不能合理解决当前问题。
- 数据库、缓存、消息队列、gRPC、分布式协调、时序数据库和独立 Starter 均由实际需求和测量结果驱动。
- 不为了展示技术栈而引入基础设施，不为未经证明的规模提前建设分布式方案。
- 具体架构以已批准的 Spec 和 `docs/adr/` 为准，不以路线图中的候选方案为准。

## Spring Boot 编程约束

- 按 **能力分包 + 技术分层**（见架构约束）组织代码；只创建当前切片需要的子包。
- 将主应用类放在根包，业务代码位于其子包；不要使用 default package。
- 优先使用构造器注入；禁止字段注入，不通过静态上下文或 `ApplicationContext` 查找普通业务依赖。
- Controller 和协议适配层只负责协议映射、输入格式校验、认证入口和响应转换，不承载业务规则或事务编排。
- 不直接将持久化实体作为 HTTP、MQTT 或其他外部协议的请求和响应模型。
- 将事务边界放在 `application` 层 `*Service` 入口；不要依赖 Controller 事务、私有方法事务或同类内部调用触发代理事务。
- 外部输入先做结构和格式校验；租户隔离、资源授权、唯一性和状态转换等业务约束必须在服务端业务边界校验，并由数据库约束提供最终防线。
- 结构化配置优先使用类型安全的 `@ConfigurationProperties`；凭证和密钥不得硬编码或写入默认配置。
- 对外错误响应必须稳定且可测试，不泄漏堆栈、SQL、凭证或内部实现细节。
- 默认拒绝未明确授权的访问；前端隐藏、URL 匹配或调用方传入的租户标识不能代替资源级授权。
- Spring、HTTP、MQTT 和持久化框架类型不要渗透到领域核心；只有当前用例证明直接使用更简单时，才在明确边界内例外。
- 测试使用与目标边界匹配的最小 Spring 上下文；纯业务规则不启动 Spring，只有验证完整装配时才加载完整应用上下文。
- 阶段 0 确定真实技术基线和目录后，再按需创建开发文档，记录实际包结构、错误格式、事务、迁移、测试和验证命令；不要提前写占位规范。

## Git 与安全

- 将工作区中的已有修改视为用户资产，即使文件尚未跟踪或处于删除状态。
- 不覆盖、删除、格式化或回退与当前任务无关的修改。
- 发现意外变化时先停止相关修改并说明情况。
- 未经用户明确要求，不执行：
  - `git reset --hard`
  - `git clean`
  - 强制检出或覆盖文件
  - rebase、提交历史改写或强制推送
  - 创建提交、推送或发布
- 不通过 stash、临时提交或其他方式隐藏用户修改，除非用户明确要求。
- 不提交或回显凭证、Token、`.env`、私钥、生产数据、内部配置及其他敏感信息。
- 发现疑似凭证或机密内容时停止传播，并明确指出风险。
- 每个提交只包含一个逻辑变更；提交标题简洁、准确，并沿用仓库现有的 `type: 中文说明` 风格。
- PR 说明应包含改了什么、为什么修改、破坏性变化以及实际验证结果。

## 参考项目

- 仅在当前任务相关时查阅 ThingsBoard、Roseboard 或 Jingwei，不要求每次检查全部项目。
- 不修改参考项目。
- 不直接复制大段源码、凭证、私有配置或未经确认的业务规则。
- Jingwei 默认按保密资料处理；输出只保留可公开的抽象结论和风险。
- 采用参考设计时，说明来源、适用原因、所做简化以及兼容性、许可证或知识产权风险。

## Agent skills

### Issue tracker

Issues 位于 GitHub 仓库 `zhijunio/roseiot`，使用 `gh` 操作。具体规则见 [`docs/agents/issue-tracker.md`](docs/agents/issue-tracker.md)。

### Triage labels

使用本仓库定义的五个标准 triage 标签。映射见 [`docs/agents/triage-labels.md`](docs/agents/triage-labels.md)。

### Domain docs

本仓库采用单上下文布局，按需使用根目录 `GLOSSARY.md` 和 `docs/adr/`。具体规则见 [`docs/agents/domain.md`](docs/agents/domain.md)。
