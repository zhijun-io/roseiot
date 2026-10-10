# Roseiot

Roseiot 是一个逐步建设的物联网平台后端，用于学习真实 IoT 场景、验证工程
取舍，并形成可运行、可测试、可解释的项目证据。

## 当前状态

阶段 0 已完成（健康检查、Compose、Flyway）。阶段 1 已交付：IAM 认证与 RBAC、固定多租户模型及全局系统设置（Flyway **V1–V7**
）。数据库迁移创建 `default` 平台租户、资源目录、平台角色和系统设置表；客户租户由 `TenantService` 在创建事务中初始化内置角色与授权。SQL
迁移不创建函数或触发器。当前没有首管理员创建入口，初始化页面与后端能力待后续 Spec。HTTP 路由按认证边界划分：需 access-token
登录态的接口统一位于 **`/api/**`**；无需 access-token 登录态的身份接口位于 **`/noauth/**`**
，包括登录、refresh、密码重置、用户激活和租户邀请；refresh 仍须提供有效 refresh token；未来内部接口使用 **`/internal/**`**
。Access JWT 含 **`sub`/`sid`/`tid`/`azv`**，refresh JWT 使用签名且有类型标识；授权只依据主体被授予的活动 **ACTION**
code（角色与资源多对多；资源目录含 MENU/ACTION 父子层级，可按树结构渲染）。用户密码和重置状态存于 IAM `UserCredential`；系统级
`GENERAL.setting.baseUrl` 用于生成邮件绝对链接。 **Spec #3**（租户生命周期、邀请、成员管理）已交付 —
见 [租户与用户 Spec](docs/sdd/2026-10-08-stage-1-tenant-and-users-spec.md)。

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

- **HTTP 契约（MockMvc 切片）**：`SessionAuthWebMvcTest`、`TenantLifecycleMockMvcTests` 等（401、错误体、权限分支）。
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

| 类型        | 示例类                                                                                         |
|-------------|------------------------------------------------------------------------------------------------|
| 单元        | `UserServiceTest`、`UserCredentialServiceTest`、`UserSessionServiceTest`、`TenantServiceTests` |
| WebMvc 切片 | `SessionAuthWebMvcTest`、`UserProfileControllerWebMvcTest`、`TenantLifecycleMockMvcTests`      |
| 真库集成    | `TenantLifecyclePostgresTest`                                                                  |

**原则**：能写单元测试的不要 `@SpringBootTest`；`@WebMvcTest` 只承担 HTTP 契约，Flyway 与持久化证据用真库集成测试。


## 文档

- [产品需求（PRD）](docs/product/prd.md)
- [产品访谈决策日志](docs/product/interview-decisions.md)
- [项目路线](docs/roadmap.md)
- [领域词汇](GLOSSARY.md)
- [Agent 规则](AGENTS.md)

### 架构决策（ADR）

- [IAM 身份认证与授权](docs/adr/2026-10-10-iam.md)
- [租户模型与生命周期](docs/adr/2026-10-10-tenant.md)
- [系统设置与全局配置](docs/adr/2026-10-10-system-settings.md)

### 阶段 1 规格（SDD）

| Spec                | 文档                                                                                    | 状态         |
|---------------------|-----------------------------------------------------------------------------------------|--------------|
| IAM 初始化简化      | [IAM 初始化与多租户简化 Spec](docs/sdd/2026-10-10-iam-bootstrap-simplification-spec.md) | 已批准并实施 |
| #2 系统用户认证     | [system-user-auth-spec.md](docs/sdd/2026-10-08-stage-1-system-user-auth-spec.md)        | 已交付       |
| 旧 Identity 与 RBAC | [Identity/RBAC Spec](docs/sdd/2026-10-08-stage-1-identity-rbac-spec.md)                 | 已替代       |
| #3 租户与用户       | [tenant-and-users-spec.md](docs/sdd/2026-10-08-stage-1-tenant-and-users-spec.md)        | 已交付       |

## 验证与运行

前置：**JDK 25**（与 `pom.xml` 一致）、**Docker**（Testcontainers 跑 `./mvnw verify`；本地启动需 Docker 以运行 `compose.yaml` 中的 PostgreSQL）。

```bash
./mvnw -q verify
cp .env.example .env   # 填入至少 32 字节的随机 ROSEIOT_AUTH_JWT_SECRET；本地手动通知时启用 local profile
./mvnw spring-boot:run # 自动 docker compose up，并注入数据源连接
```

`spring-boot-docker-compose` 为 optional 依赖，打生产包时默认不包含。若已手动 `docker compose up` 同一文件，Boot 只会复用容器并建立 service connection，不会重复 up/stop。

**不用 Compose**：在 `.env` 中设 `SPRING_DOCKER_COMPOSE_ENABLED=false` 并填写 `SPRING_DATASOURCE_*`，再 `./mvnw spring-boot:run`（仓库根存在 `compose.yaml` 时默认仍会 compose up，须显式关闭）。

健康检查（未认证，不返回连接串或凭证详情）：

```bash
curl -sS "http://localhost:8080/actuator/health/liveness"
curl -sS "http://localhost:8080/actuator/health/readiness"
```

## 首个管理员初始化

Flyway 会创建保留 `default` 平台租户、资源目录和平台内置角色。应用目前不创建管理员用户，也不提供 CLI bootstrap 或匿名初始化
API；首管理员初始化页面及其后端能力待后续 Spec。空库可以正常启动，但在管理员创建前无法登录。

## 系统用户认证（Spec #2）

前置：数据库迁移已执行。必须在环境中设置 **`ROSEIOT_AUTH_JWT_SECRET`**（HS256，至少 32 字节）；应用没有内置密钥，缺失或过短时会拒绝启动。仅本地手动验证时，在
`.env` 设置 `SPRING_PROFILES_ACTIVE=local`，日志 notifier 会输出可用的重置/邀请链接；其它环境必须接入不记录明文 token
的通知实现，否则应用无法启动。

业务 API 前缀为 **`/api`**（URL 中无 `/v1`）。错误体为稳定 JSON：`{"code":"...","message":"..."}`。

| 方法     | 路径                                       | 认证                                             |
|----------|--------------------------------------------|--------------------------------------------------|
| POST     | `/login`                                   | 匿名                                             |
| POST     | `/token`                                   | 匿名（body 含 refresh）                          |
| DELETE   | `/logout`                                  | Bearer（退出当前会话）                           |
| DELETE   | `/logout?all=true`                         | Bearer（退出全部会话）                           |
| POST     | `/noauth/user-credentials/password-resets` | 匿名                                             |
| GET      | `/noauth/user-credentials/password-resets` | 匿名（校验 token，**200** + `{ "status": "valid" |"expired"|"invalid" }`） |
| PUT      | `/noauth/user-credentials/password-resets` | 匿名（成功 **204**）                             |
| GET/POST | `/noauth/users/activation`                 | 匿名（激活流程 **501** 占位）                    |
| GET      | `/api/users/me`                            | Bearer                                           |
| PATCH    | `/api/user-credentials/me/password`        | Bearer                                           |

Access JWT（HS256）除 `sub`（用户 id）、`sid`（会话 id）外，包含 **`tid`**（用户所属租户）与 **`azv`**（整数，对应
`users.authorization_version`）。改密或密码重置成功后 `azv` 递增并撤销全部 refresh；旧 access/refresh 不可用。

Flyway 从 **V1–V7** 创建当前 schema、平台资源/角色 seed 和 `system_setting`。V7 将 `users` 中的密码字段迁入一对一
`user_credential`，并把每用户最新的重置状态迁入凭证行。客户租户内置角色在应用创建租户时由同一事务初始化。其它 schema
变更仍须清空重建或按迁移脚本升级。

验证：`./mvnw -q verify`（含 Testcontainers 空库基线测试）。

生产默认限速（同一规范化邮箱，15 分钟窗口）：登录 **10** 次、`POST /noauth/user-credentials/password-resets` **5** 次，超出返回 **429** + `rate_limited`（规则见 `application.yml` 的 `roseiot.rate-limit.rules`）。集成测试在 `src/test/resources/application.properties` 中关闭限速；本地调试可设 `ROSEIOT_RATE_LIMIT_ENABLED=false`。

### 手动验证（AC-2：登录与 profile）

```bash
BASE=http://localhost:8080

LOGIN=$(curl -sS -X POST "$BASE/login" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com","password":"your-password-at-least-12-chars"}')
ACCESS=$(echo "$LOGIN" | jq -r .accessToken)
REFRESH=$(echo "$LOGIN" | jq -r .refreshToken)

curl -sS "$BASE/api/users/me" -H "Authorization: Bearer $ACCESS" | jq .
```

### 手动验证（AC-4：注销后会话失效）

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X DELETE "$BASE/logout" \
  -H "Authorization: Bearer $ACCESS"

curl -sS -X POST "$BASE/token" \
  -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}"
# 期望 401，body 含 invalid_refresh_token
```

### 手动验证（AC-6：密码重置）

启用 `local` profile 时，`LoggingPasswordResetNotifier` 在 WARN 日志中打印 **
`/noauth/user-credentials/password-resets?resetToken=…`** 完整链接；前端对该 URL 发 GET 读取 `status` 后自行路由。自动化断言见
`UserCredentialServiceTest` 与 `IdentityNoauthControllerWebMvcTest`。

```bash
curl -sS -o /dev/null -w '%{http_code}\n' -X POST "$BASE/noauth/user-credentials/password-resets" \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@example.com"}'
# 期望 200，无 body（与未注册邮箱请求相同）

# 校验令牌（邮件/日志里的链接应对 API，不是 /auth/…）：
curl -sS "$BASE/noauth/user-credentials/password-resets?resetToken=$RESET_TOKEN"
# 期望 200，例如 {"status":"valid"}

# 无前端时可直接 PUT 确认重置：
curl -sS -o /dev/null -w '%{http_code}\n' -X PUT "$BASE/noauth/user-credentials/password-resets" \
  -H 'Content-Type: application/json' \
  -d "{\"resetToken\":\"$RESET_TOKEN\",\"password\":\"newSecurePass9012\"}"
# 期望 204；随后用新密码 login，旧 refresh 不可再用
```

## 租户与用户（Spec #3）

| 方法            | 路径                                | 说明                                         |
|-----------------|-------------------------------------|----------------------------------------------|
| POST            | `/api/tenants`                      | 平台管理员：创建客户租户并发送首个管理员邀请 |
| PATCH           | `/api/tenants/{tenantId}`           | 平台管理员：更新客户租户名称或状态           |
| GET             | `/api/members`                      | 当前租户：列出成员                           |
| PATCH           | `/api/members/{userId}`             | 当前租户：修改成员角色或状态                 |
| GET/POST/DELETE | `/api/invitations`…                 | 当前租户：邀请管理                           |
| GET/POST        | `/noauth/tenants/invitations`       | 无需 access-token：校验/接受邀请             |
| GET             | `/api/users/me`                     | 当前认证用户：个人资料                       |
| PATCH           | `/api/user-credentials/me/password` | 当前认证用户：修改密码                       |
| GET             | `/api/tenants/users`                | 当前租户：列出成员                           |
| PATCH           | `/api/tenants/users/{userId}`       | 当前租户：修改成员                           |
| GET/POST/DELETE | `/api/tenants/invitations`…         | 当前租户：邀请管理                           |
完整契约见 [Spec #3](docs/sdd/2026-10-08-stage-1-tenant-and-users-spec.md)。
