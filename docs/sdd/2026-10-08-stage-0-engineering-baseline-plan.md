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

## Slice 2：类型化配置、凭证外置与 Flyway 空库迁移

- Goal：数据源与端口通过 `@ConfigurationProperties` 绑定；凭证仅来自环境变量/本地配置；对空 PostgreSQL 自动执行 Flyway 占位迁移且可重复。
- Acceptance: AC-4, AC-5（配置路径部分）
- Depends on: Slice 1
- Test or proof：集成测试（Testcontainers PostgreSQL）断言 Flyway `schema_history` 存在且 `success`；无 `.env` 时测试通过环境属性注入。
- Implementation outline：
  - `io.roseiot.config`（或等价）下定义 `RoseiotProperties` / 数据源配置。
  - `application.yml` 仅默认值；敏感项用 `SPRING_DATASOURCE_*` 或文档约定变量。
  - `db/migration/V1__baseline.sql` 最小占位（如 `SELECT 1` 或注释性 baseline 表，避免业务表）。
  - 更新 `.env.example` 与 README 配置说明（若变量名有变）。
- Verification：`./mvnw -q verify`（含 Flyway 集成测试）
- Done: false

## Slice 3：存活/就绪健康检查与安全最小暴露

- Goal：匿名可访问 liveness；readiness 反映 PostgreSQL；响应不泄露 URL/凭证/堆栈；Security 仅放行必要健康路径。
- Acceptance: AC-2, AC-3
- Depends on: Slice 2
- Test or proof：
  - MockMvc 或 WebTestClient：liveness GET → 2xx，body 不含 `jdbc:`、password 等模式。
  - Testcontainers：DB up → readiness 2xx；停止容器或错误 URL → readiness 非 2xx，liveness 仍 2xx。
- Implementation outline：
  - 配置 Actuator `livenessState` / `readinessState`（或项目等价端点路径，写入 README）。
  - `SecurityFilterChain`：`/actuator/health/**`（或实际路径）`permitAll`，其余默认拒绝。
  - 禁用或保护敏感 actuator 端点。
- Verification：`./mvnw -q verify` 及 README 中的 curl 示例（本地手动一次）
- Done: false

## Slice 4：README 跑通声明与仓库密钥卫生

- Goal：新人按 README 从零完成 verify、启动、curl；跟踪文件无真实密钥。
- Acceptance: AC-1（终验）, AC-5（终验）, AC-6
- Depends on: Slice 3
- Test or proof：按 README 逐步执行并记录；`rg` 或审查确认无密钥入库。
- Implementation outline：
  - README 补充：JDK 版本、Docker（Testcontainers）、`./mvnw verify`、启动命令、liveness/readiness curl。
  - 确认 `.gitignore` 覆盖 `.env`、`application-local.*`。
  - PR 说明中注明已按 README 跑通（满足 AC-6 批准者责任）。
- Verification：`./mvnw -q verify`；README 命令人工跑通一次
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
