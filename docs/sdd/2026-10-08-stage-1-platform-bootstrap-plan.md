# 阶段 1 · Spec #1：平台引导与首个平台管理员 Implementation Plan

**Spec:** [`docs/sdd/2026-10-08-stage-1-platform-bootstrap-spec.md`](2026-10-08-stage-1-platform-bootstrap-spec.md)

## Risks / Dependencies

- **MyBatis Plus × Spring Boot 4.1.1 / Java 25**：Slice 1 必须 `./mvnw verify` 证明依赖可解析；失败则暂停并回到 Spec Revision 选 Spring Data JDBC 等等价方案。
- Docker / Testcontainers（与阶段 0 相同）；引导集成测试使用 Testcontainers PostgreSQL。
- CLI 引导通过 **非 Web 模式** 或 **独立进程参数** 触发，不得与默认 `spring-boot:run` 长期服务混淆（README 区分「引导命令」与「正常运行」）。

## 本 Plan 固定的校验细则（Spec Constraints 落地）

- **邮箱规范化**：trim + 全小写 Unicode 邮箱本地段规则按 Jakarta `InternetAddress` 或等价校验；拒绝空与无效格式。
- **密码复杂度（引导输入）**：至少 12 字符，且同时包含字母与数字；不满足则非零退出、不写库。

## Slice 1：MyBatis Plus、Flyway 表结构与空库可构建

- Goal：`pom.xml` 引入并验证 MyBatis Plus；Flyway `V2__platform_admin.sql` 创建平台管理员表；应用上下文在 Testcontainers 下可启动且迁移成功。
- Acceptance: AC-1（基础）, AC-4（迁移无种子凭证）
- Depends on: None
- Test or proof：`RoseiotApplicationTests` 仍通过；可选断言 `platform_admin` 表存在且行数为 0。
- Implementation outline：
  - 依赖：`mybatis-plus-spring-boot3-starter`（或验证通过的 Boot 4 兼容 artifact）及 BOM 版本锁定。
  - 包 `io.roseiot.platform`（或等价单能力包）：实体/Mapper 仅本切片所需。
  - Flyway：`id`（UUID PK）、`email`（唯一）、`password_hash`、`password_algorithm`、`created_at` 等最小列；无 INSERT 种子。
  - MyBatis `@MapperScan` 配置类。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 2：引导用例与 CLI 成功路径

- Goal：在空 `platform_admin` 表上，执行文档化 CLI 引导命令，创建恰好一名平台管理员（密码 bcrypt/Argon2id 摘要入库）。
- Acceptance: AC-2, AC-6（成功路径）, AC-1
- Depends on: Slice 1
- Test or proof：集成测试 — Testcontainers 空库 → 触发引导（`ApplicationContextRunner` / 带 `--bootstrap` 的 `SpringApplication` 或 documented 等价）→ 断言 count=1、邮箱规范化、hash 非空且不含明文。
- Implementation outline：
  - 用例服务：`BootstrapPlatformAdminUseCase`（构造器注入）；检查 count=0、校验邮箱/密码、写库。
  - CLI：`CommandLineRunner` 或 `ApplicationRunner`，仅当显式参数/环境（如 `--bootstrap` + `BOOTSTRAP_ADMIN_EMAIL` / `BOOTSTRAP_ADMIN_PASSWORD`）时运行并 `SpringApplication.exit`。
  - 默认 `RoseiotApplication` 正常启动不受影响（无 bootstrap 参数时不执行引导）。
- Verification：`./mvnw -q verify`
- Done: false

## Slice 3：重复引导拒绝

- Goal：已存在平台管理员时再次执行引导，非零退出且不新增/不覆盖记录。
- Acceptance: AC-3, AC-6（拒绝路径）, AC-1
- Depends on: Slice 2
- Test or proof：集成测试先 seed 一名管理员（测试内 API 或 Mapper），再跑引导 → exit ≠ 0，count 仍为 1。
- Implementation outline：用例层 `AlreadyBootstrappedException` 或等价；CLI 映射为非零 exit，stderr 不含密码。
- Verification：`./mvnw -q verify`
- Done: false

## Slice 4：README 与密钥卫生终验

- Goal：新人可按 README 完成 verify、引导与重复引导验证；跟踪文件无预置管理员凭证。
- Acceptance: AC-4（终验）, AC-5, AC-1（终验）
- Depends on: Slice 3
- Test or proof：按 README 步骤人工或脚本复核；`rg`/审查确认迁移与示例无真实 secret。
- Implementation outline：README「平台引导」小节；`.env.example` 增加 `BOOTSTRAP_ADMIN_*` 占位（可选）；技术基线补充 MyBatis Plus 版本。
- Verification：`./mvnw -q verify`；README 引导命令本地跑通一次（PR 说明）
- Done: false

## AC 覆盖矩阵

| AC | 切片 |
| --- | --- |
| AC-1 | 1→4（终验） |
| AC-2 | 2 |
| AC-3 | 3 |
| AC-4 | 1, 4 |
| AC-5 | 4 |
| AC-6 | 2, 3 |

## Plan revision log

- 2026-10-08 | Spec 已批准，初版 Plan | none | no — 待用户批准 Plan
- 2026-10-08 | 用户批准 Plan | none — clarification | no — 进入 sdd-build Slice 1

## Build log

- 2026-10-08 Slice 1：`./mvnw -q verify` 通过。MyBatis Plus **3.5.17** `mybatis-plus-spring-boot4-starter`（Boot 3 starter 在 Boot 4.1.1 下不注册 SqlSessionFactory）。Flyway `V2__platform_admin`；`PlatformAdminSchemaIntegrationTest` 断言空表。
