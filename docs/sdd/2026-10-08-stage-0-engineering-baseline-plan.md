# 阶段 0：工程启动基线 Implementation Plan

**Spec:** [`docs/sdd/2026-10-08-stage-0-engineering-baseline-spec.md`](2026-10-08-stage-0-engineering-baseline-spec.md)

## Risks / Dependencies

- 本机需安装 **JDK**（版本以 Slice 1 验证结果为准）；Docker 用于 Testcontainers（Slice 2 起）。
- 若 Spring Boot 4.1.1 / Java 25 无法从 Maven Central 解析，Slice 1 内降级并写入 README（AC-7），不阻塞后续切片。
- Slice 3 依赖 Slice 2 的数据源与 Flyway 配置；Slice 4 依赖 Slice 3 的健康端点路径。

## Slice 1：可解析依赖并完成首次构建验证

- Goal：仓库存在可运行的 Maven Wrapper 工程，`pom.xml` 固定验证通过的 Java/Spring Boot 版本，应用能启动到空上下文（可无数据库），`./mvnw verify` 首次通过。
- Acceptance: AC-1（初版）, AC-7
- Depends on: None
- Test or proof：先写失败测试——例如 `RoseiotApplicationTests` 上下文加载失败（尚无应用类时）；或 `./mvnw verify` 在引入应用前失败。
- Implementation outline：
  - 添加 `pom.xml`、Maven Wrapper、`io.roseiot.RoseiotApplication`。
  - 引入 Spec 要求的 starter（web、security、validation、actuator、postgresql、flyway、testcontainers 测试 scope），暂不启用 Flyway 迁移逻辑或仅 `spring.flyway.enabled=false` 直至 Slice 2。
  - 验证 Java 25 + Boot 4.1.1；不可用时选稳定组合并记录。
  - README 增加「技术基线」小节（版本号）。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 2：标准数据源配置、凭证外置与 Flyway 空库迁移

- Goal：使用 Spring Boot 标准 `spring.datasource.*` 与环境变量；凭证不入库；对空 PostgreSQL 自动执行 Flyway 占位迁移且可重复。
- Acceptance: AC-4, AC-5（配置路径部分）
- Depends on: Slice 1
- Test or proof：`RoseiotApplicationTests`（Testcontainers）加载完整上下文即覆盖 Flyway 与数据源装配；无 `.env` 时由测试动态属性注入。
- Implementation outline：
  - `application.yml` 默认值 + `SPRING_DATASOURCE_*` / `SERVER_PORT`。
  - `db/migration/V1__baseline.sql` 最小占位（`SELECT 1`）。
  - `.env.example` 与 README 配置说明。
- Verification：`./mvnw -q verify`
- Done: true

## Slice 3：存活/就绪健康检查 — 暂缓

用户决定不在阶段 0 做 liveness/readiness 专项；Spec AC-2、AC-3 延后。

## Slice 4：README 与仓库密钥卫生

- Goal：新人可按 README 完成 verify 与启动；跟踪文件无真实密钥。
- Acceptance: AC-1（终验）, AC-5（终验）；AC-6 不含 liveness/readiness curl（随 Slice 3 延后）
- Depends on: Slice 2
- Test or proof：按 README 执行 `./mvnw verify`；`rg` 或审查确认无密钥入库。
- Implementation outline：
  - README 补充 JDK、verify、启动与聚合健康检查说明（`/actuator/health`）。
  - 确认 `.gitignore` 覆盖 `.env`、`application-local.*`。
- Verification：`./mvnw -q verify`
- Done: false

## AC 覆盖矩阵

| AC | 切片 |
| --- | --- |
| AC-1 | 1（初验）, 4（终验） |
| AC-2 | 3 |
| AC-3 | 3 |
| AC-4 | 2 |
| AC-5 | 2, 4 |
| AC-6 | 4 |
| AC-7 | 1 |

## Plan revision log

- 2026-10-08 | 用户批准 Plan | none | 进入 sdd-build Slice 1

## Build log

- 2026-10-08 Slice 1：`./mvnw -q verify` 通过（Java 25.0.2，Spring Boot 4.1.1，Maven Wrapper 3.9.9）。Testcontainers 依赖留待 Slice 2 加入。
- 2026-10-08 Slice 2：`./mvnw -q verify` 通过；`RoseiotProperties`、Flyway `V1__baseline`、`FlywayMigrationIntegrationTest`（Testcontainers PostgreSQL 16）。
- 2026-10-08 精简 Slice 2：改用标准 `spring.datasource.*` / `SPRING_DATASOURCE_*`，删除自定义 `@ConfigurationProperties` 与独立 Flyway 测试类；Slice 3/4 暂缓。
