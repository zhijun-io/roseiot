# 阶段 1 · Spec #1：平台引导与首个平台管理员

## Goal

在空库上通过**一次性 CLI 引导**创建首个平台管理员；仓库与迁移中不含固定账号或密码，且引导不可重复执行。

## Scope

- **IAM-001**：显式、一次性安全引导；已存在平台管理员时拒绝再次引导。
- **CLI 入口**：Spring Boot 应用上的文档化引导命令（非 HTTP）；凭证来自运行环境（环境变量或交互 stdin），不得写入 Git。
- **持久化**：Flyway 迁移创建平台管理员所需最小表结构；通过 **MyBatis Plus** 读写（须在实现前验证与 Java 25、Spring Boot 4.1.1 的兼容性，不满足则在本 Spec Revision 中降级方案）。
- **平台管理员**：规范化电子邮箱（全平台唯一登录标识）、稳定内部 ID、密码仅存适合校验的摘要（算法与参数在 Constraints 中固定范围，实现可配置 cost）。
- **README**：记录引导前置条件、命令与验证步骤。

## Non-goals

- 租户创建/停用、用户登录、刷新会话、成员邀请（Spec #2～#3）。
- 产品、设备、设备凭证（Spec #4～#5）。
- HTTP 一次性引导端点、公开注册、套餐/计费。
- REST 路径前缀 `/api/v1`（阶段 1 后续 Spec 再引入；本 Spec 无业务 HTTP API）。
- 审计查询 UI、邮件服务商绑定（账户恢复留 Spec #2；本 Spec 不涉及）。

## Current Context

- 阶段 0 已完成：单模块 Spring Boot 4.1.1、Java 25、Flyway、PostgreSQL、Security 最小暴露、Testcontainers 测试、`compose.yaml` 本地库。
- 根包 `io.roseiot`；尚无 `identity` 等业务包（本 Spec 允许创建**当前切片所需**的最小包，不预建空模块）。
- 术语见 [`GLOSSARY.md`](../../GLOSSARY.md)（平台管理员、用户、租户等）。

## Requirements

1. 运维可在文档声明的环境中，对**空平台管理员表**执行引导，成功创建**恰好一名**平台管理员。
2. 引导成功后再次执行引导必须失败，且不创建第二名管理员、不覆盖已有管理员。
3. 迁移脚本与跟踪文件中不得包含真实密码、预置管理员邮箱或等价秘密。
4. 平台管理员内部 ID 对外不透明；业务上不使用可猜测顺序的整数主键作为对外标识（倾向 UUIDv7 或 Spec 批准时验证通过的等价方案）。
5. 引导失败时（校验失败、库不可达、已引导）进程以非零退出码结束，并输出**不泄露秘密**的错误说明。

## Acceptance Criteria

- **AC-1**：仓库根目录 `./mvnw -q verify` 退出码为 0（含引导相关自动化测试）。
- **AC-2**：在空 PostgreSQL 上按 README 执行引导命令，并提供文档约定的环境凭证，成功后库中存在**恰好一条**平台管理员记录，且该邮箱与输入一致（规范化后）。
- **AC-3**：在 AC-2 已成功的前提下再次执行同一引导命令，退出码非 0，平台管理员记录数仍为 1。
- **AC-4**：对仓库已跟踪文件执行审查（或 Spec 批准者在 PR 中说明已审查），迁移与配置模板中无真实 datasource 密码、无预置管理员凭证。
- **AC-5**：README 包含引导命令、所需环境变量（或交互说明）、与 AC-2/AC-3 等价的手动验证步骤。
- **AC-6**：自动化测试覆盖「空库引导成功」与「已存在管理员时拒绝」；测试不依赖作者机器上唯一的本地数据库（Testcontainers 或等价）。

## Constraints

- 模块化单体；构造器注入；Controller/适配层不承载业务规则（本 Spec 以 CLI 为主，若出现 HTTP 健康以外的入口仍遵守 AGENTS.md）。
- PostgreSQL + Flyway；普通应用账号与迁移账号仍可为同一账号（与阶段 0 一致），除非本切片证明必须分离。
- **MyBatis Plus**：作为阶段 1 持久化栈选型；引入前须在 Slice 1 内完成依赖解析与最小 CRUD 验证，并写入 README 技术基线或本 Spec Revision log。
- 密码存储使用成熟自适应哈希（如 bcrypt 或 Argon2id）；不得在领域模型中写死单一算法常量而阻碍未来升级，但数据库须能记录算法标识或版本。
- 电子邮箱规范化规则与最小密码复杂度在 Plan 中具体化，且须与 PRD `IAM-003`、`IAM-007` 方向一致（复杂度细则不得弱于「可演示的生产纪律」：至少长度 + 基本字符类，具体数值在 Plan 列出）。
- 默认拒绝授权：本 Spec 不暴露匿名业务 API；CLI 引导不是 Web 端点。

## Decisions

- **选定**：阶段 1 按访谈 **Q492** 拆 5 份 Spec；本文件为 **#1**，仅 IAM-001 闭环。
- **选定**：引导界面为 **CLI**（用户确认），不用一次性 HTTP。
- **选定**：阶段 1 持久化栈 **MyBatis Plus**（用户确认）；与访谈 Q125「阶段 1 再选」一致。
- **选定**：REST 全局 `/api/v1` 前缀推迟到后续 Spec；本 Spec 无对外 REST 业务面。
- **选定**：阶段 1 用户自助边界按 **Q106 窄化** + **Q208** 方向（恢复在 Spec #2，本 Spec 不做）。
- **拒绝**：迁移种子数据内置管理员。
- **拒绝**：为引导单独拆 Maven module。

## Related ADRs

- 无。若 MyBatis Plus 或 UUID 方案在验证后与 PRD 候选基线产生重大偏差，可在实现 PR 提议 `docs/adr/`。

## Open Questions

- 无（邮箱规范化与密码下限留 Plan 具体数值）。

## Revision log

- 2026-10-08 | 初稿：Grill 结论（5 Spec、CLI、MyBatis Plus、无 v1 前缀、Q106） | none | no — 待用户批准
- 2026-10-08 | 用户批准 Spec | none — clarification | no — 进入 Plan
- 2026-10-08 | Slice 1 验证 MyBatis Plus | none — 使用 boot4-starter 3.5.17 | no — Plan Build log
