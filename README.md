# Roseiot

Roseiot 是一个逐步建设的物联网平台后端，用于学习真实 IoT 场景、验证工程
取舍，并形成可运行、可测试、可解释的项目证据。

## 当前状态

阶段 0 已完成（健康检查、Compose、Flyway）。阶段 1 已交付：**Spec #1** 引导、**Spec #2** 平台认证、**身份迁移**（`users` + RBAC-0，Flyway **V4～V6**）。HTTP：**`/api/identity/login|token|logout*|recovery/*|me/**`**（认证与平台 profile 同前缀，无 `/auth` 段）。平台 access JWT 含 **`ctx=platform`**、**`azv`**；`/api/identity/me/**` 需 **`identity.profile.*`**。**Spec #3**（租户、成员、租户用户 `ctx=tenant`）规格已编写、**尚未实现** — 见 [租户与成员 Spec](docs/sdd/2026-10-08-stage-1-tenant-and-members-spec.md)。

## 建设原则

- 以业务闭环和可观察行为为交付单位；
- 默认采用模块化单体，不提前拆分微服务；
- 优先使用 Java、Spring Boot 和 PostgreSQL 的已有能力；
- 依赖和基础设施必须由当前问题驱动；
- 用测试、演示脚本和可复现基准证明结果。

## 技术基线（已验证）

| 项 | 版本 |
| --- | --- |
| Java | 25 |
| Spring Boot | 4.1.1 |
| Maven | 3.10.0（Wrapper） |
| PostgreSQL | 18（`compose.yaml` 与 Testcontainers `postgres:18-alpine`） |
| MyBatis Plus | 3.5.17（`mybatis-plus-spring-boot4-starter`，阶段 1 起） |

与 PRD 候选基线一致；依赖来自 Maven Central 正式发布版本。

## 本地配置

敏感信息只放在未跟踪的 `.env` 或 `application-local.*` 中。可参考
[`.env.example`](.env.example) 中的变量名，不要提交真实凭证。

配置加载顺序（简）：

1. **`spring-boot:run` + `compose.yaml`**：`JdbcConnectionDetails` 优先于 `application.yml` 中的 `SPRING_DATASOURCE_*` 占位符。
2. **`.env`**：经 `spring.config.import=optional:file:.env[.properties]` 从进程工作目录（通常在仓库根）加载；Compose 另用同一文件做 `POSTGRES_PASSWORD` 等变量替换。
3. **集成测试**：`spring.docker.compose.skip.in-tests=true`，不启动 Compose；`@Import(TestcontainersConfig.class)` 经 `@ServiceConnection` 注入数据源，不依赖本机 `.env`。
4. **IDE 试跑**：运行测试源里的 `TestRoseiotApplication`（Testcontainers Postgres，等同 spring-blog 的 `TestBlogApplication`）。

## 测试策略

与访谈 **Q128** 一致：按风险选边界，不追求固定比例或「每表一个集成测试类」。

**命名**：

- `*MockMvcTests`：继承 `AbstractIt`，只使用 `mvc`，**不用** `restTestClient`。
- 其他集成 `*Tests`：继承 `AbstractIt`，**不用** `mvc`；需要真实 HTTP 时可选 `restTestClient`。
- `*ServiceTests` 等单元测试：不启 Spring。

### 单元测试

**边界**：单类/单用例，**不启动 Spring**（`@ExtendWith(MockitoExtension.class)` + `@InjectMocks` / `@Mock`）。

**写单元测试当**：

- 业务规则、分支、错误码映射在 **application 层 Service**（或纯函数）里，用 Mock 替代 Mapper、Encoder、时钟等即可断言。
- 同一逻辑已有集成测试覆盖 Happy path，但失败分支多、起容器成本高——用单元测试补分支。
- 无 I/O：UUID 生成、格式校验、小工具类。

**不写单元测试当**：

- 被测对象只是 Spring 配置拼装（`@Configuration`、Filter 链顺序）——测了也测不到真实行为，应写集成测试。
- 断言完全依赖 PostgreSQL 约束、Flyway 脚本或 HTTP 状态码——那是集成测试的职责。

### 集成测试

**边界**：`@SpringBootTest` + `TestcontainersConfig`（PostgreSQL `@ServiceConnection`），证明**真实接线**。

**写集成测试当**：

- **HTTP 契约（MockMvc）**：`*MockMvcTests`（401、错误体、登录失败分支等）。
- **HTTP 契约（可选真实端口）**：`*Tests` + `AbstractIt` 的 `restTestClient`（如 JWT 往返 happy path）。
- **持久化/迁移**：Flyway 脚本、表/索引/约束与 Mapper 在真库上可运行 → 继承 `AbstractIt`（不必调用 HTTP）。
- **模式开关**：bootstrap CLI、`roseiot.bootstrap.enabled` 等改变 Bean 集合 → 继承 `AbstractIt`（必要时子类再加 `@SpringBootTest(properties=…)`）。
- **Actuator / 健康检查**：有库场景继承 `AbstractIt`；**故意不可达数据源**（readiness DOWN / liveness UP）用独立 `@SpringBootTest`（**不** import `TestcontainersConfig`），见 `ActuatorHealthWhenDatabaseUnreachableTests`。

**不写集成测试当**：

- 仅验证 Service 在「密码错误 / 用户不存在」时抛何种异常——Mock 即可，不要为每个分支起容器。
- 与已有 API 集成测试重复的 profile 字段断言（集成测试已证明 JWT → `/api/identity/me` 即可）。

### 基类

| 类型 | 用途 |
| --- | --- |
| `TestcontainersConfig` | 唯一 Postgres 容器 + `@ServiceConnection` |
| `AbstractIt` | 集成测试基类：Testcontainers + `mvc` + `restTestClient`（按类名约定选用其一） |

### 示例

| 类型 | 示例类 |
| --- | --- |
| 单元 | `UserSessionServiceTests`、`UserAccountServiceTests` |
| MockMvc | `IdentityAuthMockMvcTests`、`ActuatorHealthMockMvcTests` |
| RestTestClient（可选） | `IdentityAuthTests` |
| 冒烟 + Flyway | `PlatformFlywaySchemaTests`（含 `contextLoads`） |

**原则**：能写 `*ServiceTests` 的不要写继承 `AbstractIt` 的类；集成测试只保留契约/接线证据。


## 文档

- [产品需求（PRD）](docs/product/prd.md)
- [产品访谈决策日志](docs/product/interview-decisions.md)
- [项目路线](docs/roadmap.md)
- [领域词汇](GLOSSARY.md)
- [Agent 规则](AGENTS.md)

### 阶段 1 规格（SDD）

| Spec | 文档 | 状态 |
| --- | --- | --- |
| #1 平台引导 | [stage-1-platform-bootstrap-spec.md](docs/sdd/2026-10-08-stage-1-platform-bootstrap-spec.md) | 已交付 |
| #2 平台认证 | [stage-1-platform-admin-auth-spec.md](docs/sdd/2026-10-08-stage-1-platform-admin-auth-spec.md) | 已交付 |
| 身份迁移 | [identity-user-rbac-migration-spec.md](docs/sdd/2026-10-08-stage-1-identity-user-rbac-migration-spec.md) | 已交付 |
| #3 租户与成员 | [tenant-and-members-spec.md](docs/sdd/2026-10-08-stage-1-tenant-and-members-spec.md) | **待批准 / 未实现** |

## 验证与运行

前置：**JDK 25**（与 `pom.xml` 一致）、**Docker**（Testcontainers 跑 `./mvnw verify`；本地启动需 Docker 以运行 `compose.yaml` 中的 PostgreSQL）。

```bash
./mvnw -q verify
cp .env.example .env   # 可选：覆盖 POSTGRES_PASSWORD
./mvnw spring-boot:run # 自动 docker compose up，并注入数据源连接
```

`spring-boot-docker-compose` 为 optional 依赖，打生产包时默认不包含。若已手动 `docker compose up` 同一文件，Boot 只会复用容器并建立 service connection，不会重复 up/stop。

**不用 Compose**：在 `.env` 中设 `SPRING_DOCKER_COMPOSE_ENABLED=false` 并填写 `SPRING_DATASOURCE_*`，再 `./mvnw spring-boot:run`（仓库根存在 `compose.yaml` 时默认仍会 compose up，须显式关闭）。

可选：`SERVER_PORT` 覆盖默认 8080。

健康检查（未认证，不返回连接串或凭证详情）：

```bash
curl -sS "http://localhost:8080/actuator/health/liveness"
curl -sS "http://localhost:8080/actuator/health/readiness"
```

## 平台引导（IAM-001）

在 **`users` 中尚无 `tenant_id IS NULL` 的平台用户** 的数据库上，通过 **CLI** 创建**恰好一名**平台操作员（`users` + `PlatformOperator` 角色绑定）。
凭证只来自环境变量，勿写入 Git。引导与长期运行的 Web 服务分开：使用 `--bootstrap` 启动后进程会退出，不会监听 HTTP。

前置：PostgreSQL 已就绪（Compose 或 `.env` 中的 `SPRING_DATASOURCE_*`），且 `./mvnw -q verify` 通过。

在 `.env` 中设置（参见 [`.env.example`](.env.example)）：

```bash
BOOTSTRAP_USER_EMAIL=admin@example.com
BOOTSTRAP_USER_PASSWORD=your-password-at-least-12-chars
```

密码至少 12 位且同时包含字母与数字。邮箱会 trim 并转为小写入库。

**首次引导（空库，期望退出码 0）：**

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--bootstrap
echo $?   # 应为 0
```

**验证库中有一条记录**（示例：用 `psql` 或任意 SQL 客户端，勿在日志中打印密码）：

```sql
SELECT email FROM users WHERE tenant_id IS NULL;
-- 期望一行，email 与规范化后的 BOOTSTRAP_USER_EMAIL 一致
```

**重复引导（期望非零退出、仍为 1 条记录）：**

```bash
./mvnw -q spring-boot:run -Dspring-boot.run.arguments=--bootstrap
echo $?   # 应非 0；stderr 含 already bootstrapped，不含密码
```

再次查询 `users`（`tenant_id IS NULL`）行数应仍为 1。正常运行应用时不要带 `--bootstrap`：

```bash
./mvnw spring-boot:run
```

## 平台管理员认证（Spec #2）

前置：已完成 [平台引导（IAM-001）](#平台引导iam-001)，应用以 Web 模式运行（不带 `--bootstrap`）。在 `.env` 中设置 **`ROSEIOT_AUTH_JWT_SECRET`**（HS256，至少 32 字符；参见 [`.env.example`](.env.example)），勿使用仓库默认值上线。

业务 API 前缀为 **`/api`**（URL 中无 `/v1`）。错误体为稳定 JSON：`{"code":"...","message":"..."}`。

| 方法 | 路径 | 认证 |
| --- | --- | --- |
| POST | `/api/identity/login` | 匿名 |
| POST | `/api/identity/token` | 匿名（body 含 refresh） |
| POST | `/api/identity/logout` | Bearer |
| POST | `/api/identity/logout-all` | Bearer |
| POST | `/api/identity/recovery/request` | 匿名 |
| POST | `/api/identity/recovery/confirm` | 匿名 |
| GET | `/api/identity/me` | Bearer |
| PATCH | `/api/identity/me/password` | Bearer |

Access JWT（HS256）除 `sub`（用户 id）、`sid`（会话 id）外，包含 **`ctx=platform`**（平台用户）与 **`azv`**（整数，对应 `users.authorization_version`）。改密或 recovery 成功后 `azv` 递增并撤销全部 refresh；旧 access/refresh 不可用。

**自 V3 升级**：Flyway **V4** 将会话/恢复表改为 `user_*` 并迁移 `platform_admin` → `users`；**V5** 写入 RBAC-0 目录；**V6** 将历史 `platform.*` 权限键重命名为 **`identity.*`**。升级后既有 refresh 会话语义变化，**请重新登录**（旧 refresh 不可用）。

验证：`./mvnw -q verify`（含 Testcontainers 自空库 V1→最新与 V3→V4 迁移测试）。

生产默认限速（同一规范化邮箱，15 分钟窗口）：登录 **10** 次、恢复请求 **5** 次，超出返回 **429** + `rate_limited`（规则见 `application.yml` 的 `roseiot.rate-limit.rules`）。集成测试在 `src/test/resources/application.properties` 中关闭限速；本地调试可设 `ROSEIOT_RATE_LIMIT_ENABLED=false`。

### 手动验证（AC-2：登录与 profile）

```bash
BASE=http://localhost:8080

LOGIN=$(curl -sS -X POST "$BASE/api/identity/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"YOUR_BOOTSTRAP_PASSWORD"}')
ACCESS=$(echo "$LOGIN" | jq -r .accessToken)
REFRESH=$(echo "$LOGIN" | jq -r .refreshToken)

curl -sS "$BASE/api/identity/me" -H "Authorization: Bearer $ACCESS" | jq .
```

### 手动验证（AC-4：注销后会话失效）

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/identity/logout" \
  -H "Authorization: Bearer $ACCESS"

curl -sS -X POST "$BASE/api/identity/token" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
# 期望 401，body 含 invalid_refresh_token
```

### 手动验证（AC-6：账户恢复）

默认实现仅 **WARN 日志**（不含令牌明文）；本地需自行从测试替身或后续邮件实现取得令牌。自动化路径见 `InMemoryRecoveryNotifier` 与 `IdentityAuthMockMvcTests`。

```bash
curl -sS -X POST "$BASE/api/identity/recovery/request" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com"}'
# 期望 202，body.message 与未注册邮箱请求相同

# 取得 RECOVERY_TOKEN 后：
curl -sS -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/identity/recovery/confirm" \
  -H 'Content-Type: application/json' \
  -d "{\"token\":\"$RECOVERY_TOKEN\",\"newPassword\":\"newSecurePass9012\"}"
# 期望 204；随后用新密码 login，旧 refresh 不可再用
```

## 规划中：租户与成员（Spec #3，未实现）

以下路径为 **已编写 Spec 的目标契约**，当前代码**不存在**对应 Controller；批准 Spec 并交付 Plan 后再实现。

| 域 | 示例路径 | 说明 |
| --- | --- | --- |
| 平台租户 | `POST /api/identity/tenants`、`POST .../tenants/{id}/disable` | `identity.tenant.*`；**非** `/api/platform/**` |
| 租户内 | `GET /api/tenant/me`、`GET /api/tenant/members` | 无 URL 内 `tenantId`；JWT `ctx=tenant` + `tid` |
| 邀请 | `POST /api/identity/invites/accept`（匿名） | 与 login 同属 `/api/identity` 前缀 |

租户用户仍计划共用 **`POST /api/identity/login`** 等会话端点；详情见 Spec #3 与访谈 **Q513**。
