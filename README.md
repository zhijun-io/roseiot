# Roseiot

Roseiot 是一个逐步建设的物联网平台后端，用于学习真实 IoT 场景、验证工程
取舍，并形成可运行、可测试、可解释的项目证据。

## 当前状态

阶段 0 已完成（健康检查、Compose、Flyway）。阶段 1 已交付：系统引导、系统用户认证和 Identity/RBAC 基线（Flyway **V1**）。HTTP：会话 **`/api/login`**、**`/api/token`**、**`/api/logout`**、**`/api/logout-all`**；匿名 **`/api/noauth/**`**；系统当前用户 **`/api/users/me/**`**。系统用户 access JWT 含 **`ctx=system`**、**`azv`**；`/api/users/me/**` 需 **`user.profile.*`**。**Spec #3**（租户生命周期、邀请、租户用户管理、租户 **`ctx=tenant`** 会话与 **`/api/users/me/**`**）已交付 — 见 [租户与用户 Spec](docs/sdd/2026-10-08-stage-1-tenant-and-users-spec.md)。

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

- `*WebMvcTest`、`*MockMvcTests`：`@WebMvcTest` + MockMvc，只断言 HTTP 契约（Controller / Security 切片）。
- `*PostgresTest` 等真库集成：`@SpringBootTest` + `@Import(TestcontainersConfig.class)`，验证 Flyway、Mapper、事务。
- `*Test` / `*ServiceTests` 等单元测试：不启 Spring。

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

**边界**：HTTP 契约用 `@WebMvcTest` 切片；真库接线用 `@SpringBootTest` + `TestcontainersConfig`（PostgreSQL `@ServiceConnection`）。

**写集成测试当**：

- **HTTP 契约（MockMvc 切片）**：`AuthControllerWebMvcTest`、`TenantLifecycleMockMvcTests` 等（401、错误体、权限分支）。
- **Security + Controller 组合**：`SecurityConfigWebMvcTest`（匿名 recovery、JWT 入口等与过滤器链一致的行为）。
- **持久化 / Flyway / 事务**：如 `TenantLifecyclePostgresTest`——在 PostgreSQL 上验证表、Mapper 与跨表原子性（不必走 HTTP）。

**不写集成测试当**：

- 仅验证 Service 在「密码错误 / 用户不存在」时抛何种异常——Mock 即可，不要为每个分支起容器。
- 与已有 API 集成测试重复的 profile 字段断言（集成测试已证明 JWT → `/api/users/me` 即可）。

### 公共夹具

| 类型 | 用途 |
| --- | --- |
| `TestcontainersConfig` | 唯一 Postgres 容器 + `@ServiceConnection`（真库 `@SpringBootTest` 通过 `@Import` 引入） |

### 示例

| 类型 | 示例类 |
| --- | --- |
| 单元 | `UserProfileServiceTest`、`UserSessionServiceTest`、`TenantServiceTests` |
| WebMvc 切片 | `AuthControllerWebMvcTest`、`UserProfileControllerWebMvcTest`、`TenantLifecycleMockMvcTests` |
| 真库集成 | `TenantLifecyclePostgresTest` |

**原则**：能写单元测试的不要 `@SpringBootTest`；`@WebMvcTest` 只承担 HTTP 契约，Flyway 与持久化证据用真库集成测试。


## 文档

- [产品需求（PRD）](docs/product/prd.md)
- [产品访谈决策日志](docs/product/interview-decisions.md)
- [项目路线](docs/roadmap.md)
- [领域词汇](GLOSSARY.md)
- [Agent 规则](AGENTS.md)

### 阶段 1 规格（SDD）

| Spec | 文档 | 状态 |
| --- | --- | --- |
| #1 系统引导 | [system-bootstrap-spec.md](docs/sdd/2026-10-08-stage-1-system-bootstrap-spec.md) | 已交付 |
| #2 系统用户认证 | [system-user-auth-spec.md](docs/sdd/2026-10-08-stage-1-system-user-auth-spec.md) | 已交付 |
| Identity 与 RBAC | [identity-rbac-spec.md](docs/sdd/2026-10-08-stage-1-identity-rbac-spec.md) | 已交付 |
| #3 租户与用户 | [tenant-and-users-spec.md](docs/sdd/2026-10-08-stage-1-tenant-and-users-spec.md) | **待批准 / 未实现** |

## 验证与运行

前置：**JDK 25**（与 `pom.xml` 一致）、**Docker**（Testcontainers 跑 `./mvnw verify`；本地启动需 Docker 以运行 `compose.yaml` 中的 PostgreSQL）。

```bash
./mvnw -q verify
cp .env.example .env   # 可选：覆盖 POSTGRES_PASSWORD
./mvnw spring-boot:run # 自动 docker compose up，并注入数据源连接
```

`spring-boot-docker-compose` 为 optional 依赖，打生产包时默认不包含。若已手动 `docker compose up` 同一文件，Boot 只会复用容器并建立 service connection，不会重复 up/stop。

**不用 Compose**：在 `.env` 中设 `SPRING_DOCKER_COMPOSE_ENABLED=false` 并填写 `SPRING_DATASOURCE_*`，再 `./mvnw spring-boot:run`（仓库根存在 `compose.yaml` 时默认仍会 compose up，须显式关闭）。

健康检查（未认证，不返回连接串或凭证详情）：

```bash
curl -sS "http://localhost:8080/actuator/health/liveness"
curl -sS "http://localhost:8080/actuator/health/readiness"
```

## 系统引导（IAM-001）

在 **`users` 中尚无 `tenant_id IS NULL` 的系统用户** 的数据库上，通过 **CLI** 创建**恰好一名**系统管理员（`users` + `SystemAdmin` 角色绑定）。
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

## 系统用户认证（Spec #2）

前置：已完成 [系统引导（IAM-001）](#系统引导iam-001)，应用以 Web 模式运行（不带 `--bootstrap`）。在 `.env` 中设置 **`ROSEIOT_AUTH_JWT_SECRET`**（HS256，至少 32 字符；参见 [`.env.example`](.env.example)），勿使用仓库默认值上线。

业务 API 前缀为 **`/api`**（URL 中无 `/v1`）。错误体为稳定 JSON：`{"code":"...","message":"..."}`。

| 方法 | 路径 | 认证 |
| --- | --- | --- |
| POST | `/api/login` | 匿名 |
| POST | `/api/token` | 匿名（body 含 refresh） |
| POST | `/api/logout` | Bearer |
| POST | `/api/logout-all` | Bearer |
| POST | `/api/noauth/password-reset/email` | 匿名 |
| GET | `/api/noauth/password-reset` | 匿名（校验 token，**200** + `{ "status": "valid"|"expired"|"invalid" }`） |
| POST | `/api/noauth/password-reset` | 匿名（成功 **204**） |
| GET/POST | `/api/noauth/user-activation` | 匿名（激活流程 **501** 占位） |
| GET | `/api/users/me` | Bearer |
| PATCH | `/api/users/me/password` | Bearer |

Access JWT（HS256）除 `sub`（用户 id）、`sid`（会话 id）外，包含 **`ctx=system`**（系统用户）与 **`azv`**（整数，对应 `users.authorization_version`）。改密或密码重置成功后 `azv` 递增并撤销全部 refresh；旧 access/refresh 不可用。

Flyway 基线直接创建当前 schema 与 RBAC seed。新库从 **V1** 起即含 `password_reset_request`；曾用旧 V1（`user_recovery_request`）的库由 **V2** 重命名。其它 schema 变更仍须清空重建或按迁移脚本升级。

验证：`./mvnw -q verify`（含 Testcontainers 空库基线测试）。

生产默认限速（同一规范化邮箱，15 分钟窗口）：登录 **10** 次、`POST …/password-reset/email` **5** 次，超出返回 **429** + `rate_limited`（规则见 `application.yml` 的 `roseiot.rate-limit.rules`）。集成测试在 `src/test/resources/application.properties` 中关闭限速；本地调试可设 `ROSEIOT_RATE_LIMIT_ENABLED=false`。

### 手动验证（AC-2：登录与 profile）

```bash
BASE=http://localhost:8080

LOGIN=$(curl -sS -X POST "$BASE/api/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"your-password-at-least-12-chars"}')
ACCESS=$(echo "$LOGIN" | jq -r .accessToken)
REFRESH=$(echo "$LOGIN" | jq -r .refreshToken)

curl -sS "$BASE/api/users/me" -H "Authorization: Bearer $ACCESS" | jq .
```

### 手动验证（AC-4：注销后会话失效）

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/logout" \
  -H "Authorization: Bearer $ACCESS"

curl -sS -X POST "$BASE/api/token" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
# 期望 401，body 含 invalid_refresh_token
```

### 手动验证（AC-6：密码重置）

默认 Bean 为 `LoggingPasswordResetNotifier`，在 WARN 日志中打印 **`/api/noauth/password-reset?resetToken=…`** 完整链接；前端对该 URL 发 GET 读取 `status` 后自行路由。自动化断言见 `PasswordResetServiceTest` 与 `NoauthControllerWebMvcTest`。

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/noauth/password-reset/email" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com"}'
# 期望 200，无 body（与未注册邮箱请求相同）

# 校验令牌（邮件/日志里的链接应对 API，不是 /auth/…）：
curl -sS "$BASE/api/noauth/password-reset?resetToken=$RESET_TOKEN"
# 期望 200，例如 {"status":"valid"}

# 无前端时可直接 POST 改密：
curl -sS -o /dev/null -w '%{http_code}\n' -X POST "$BASE/api/noauth/password-reset" \
  -H 'Content-Type: application/json' \
  -d "{\"resetToken\":\"$RESET_TOKEN\",\"password\":\"newSecurePass9012\"}"
# 期望 204；随后用新密码 login，旧 refresh 不可再用
```

## 租户与用户（Spec #3）

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/tenants` | 系统：创建租户 |
| PATCH | `/api/tenants/{tenantId}` | 系统：更新租户（含停用/启用） |
| GET | `/api/tenants/users` | 租户 Admin：列用户 |
| PATCH | `/api/tenants/users/{userId}` | 租户 Admin：角色/停用 |
| GET/POST/DELETE | `/api/tenants/invitations`… | 租户 Admin：邀请 CRUD |
| GET/POST | `/api/noauth/tenant-invitations` | 匿名：校验/接受邀请 |
| GET/PATCH | `/api/users/me`… | 租户用户 profile（**`ctx=tenant`**） |

完整契约见 [Spec #3](docs/sdd/2026-10-08-stage-1-tenant-and-users-spec.md)。
