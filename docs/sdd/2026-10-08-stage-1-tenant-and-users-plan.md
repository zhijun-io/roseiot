# 阶段 1 · 租户与租户用户 Plan

**Spec:** [`2026-10-08-stage-1-tenant-and-users-spec.md`](2026-10-08-stage-1-tenant-and-users-spec.md)

## Risks / Dependencies

- 项目采用可重建的单一 Flyway 基线。实现租户表时修改 `V1__baseline.sql`，现有开发数据库须清空重建。
- 租户边界不能只信任 JWT `tid`：每个租户服务入口同时验证 `ctx=tenant`、JWT `tid`、`users.tenant_id` 与目标资源租户一致。
- 首位 TenantAdmin 是 pending 邀请而非 User；邀请 token 只可经受控通知路径取得，响应、日志与审计不得包含 token 明文。
- 测试采用分层策略：应用服务规则先用 Mockito 单元测试；Controller 的请求映射与 HTTP 响应用 `@WebMvcTest`；Flyway、MyBatis 和跨表事务才用 Testcontainers。

## Slice 1: 系统管理员原子创建租户与首位管理员邀请

- Goal: SystemAdmin 可创建租户，并在同一事务中创建目标为 TenantAdmin 的 pending 邀请；可更新租户名称和状态。
- Acceptance: AC-1, AC-2.
- Depends on: None.
- Test or proof: 先写 `TenantServiceTests` 覆盖原子创建与冲突规则，再用 `@WebMvcTest` 覆盖请求映射、201 响应和认证拒绝；最后以 Testcontainers 覆盖租户与邀请的真实事务写入。
- Implementation outline: 扩展 `V1__baseline.sql` 创建 `tenant` 与 `tenant_user_invitation`、必要唯一约束和索引；新增 `tenant.persistence` 实体/Mapper、事务性 `TenantService` 与 `/api/tenants` Controller；复用 `AuthorizationService` 校验 `tenant.create`、`tenant.disable`、`tenant.enable`。
- Verification: `./mvnw -q test -Dtest=TenantLifecycleMockMvcTests`.
- Done: true（2026-10-09；额外以 `./mvnw -q verify` 全量 210 个测试通过，含 `TenantServiceTests` 与 `TenantLifecyclePostgresTest`）。

## Slice 2: 接受邀请并签发租户上下文会话

- Goal: 受邀者可一次性接受有效邀请，成为租户用户并获得 `ctx=tenant`、`tid` 正确的登录会话。
- Acceptance: AC-1, AC-2.
- Depends on: Slice 1.
- Test or proof: 先写 `TenantInvitationServiceTests` 覆盖邀请状态转换和 token 规则，再以 Testcontainers 覆盖首次接受、用户/角色写入和 JWT claim。
- Implementation outline: 在 `tenant.application` 实现邀请接受事务：校验 token 摘要与邀请状态、创建 `users` 和 `user_role`、标记邀请终态；在 `identity.web` 暴露 **`GET|POST /api/noauth/tenant-invitations`**（与 password-reset 同构）；在 `AuthController` 与 `UserRefreshSessions` 支持租户 JWT claim；邀请 token 使用独立实体和摘要，不复用 password reset token。
- Verification: `./mvnw -q test -Dtest=NoauthControllerWebMvcTest,TenantInvitationPostgresTest`.
- Done: true（2026-10-09；`GET|POST /api/noauth/tenant-invitations`、`TenantInvitationService.acceptInvitation`、Postgres 接受闭环）。

## Slice 3: 租户管理员邀请和用户管理

- Goal: TenantAdmin 能管理本租户邀请和用户，TenantUser 不能管理他人或跨越租户。
- Acceptance: AC-1, AC-2.
- Depends on: Slice 2.
- Test or proof: 先写 `TenantUserManagementServiceTests` 覆盖最后管理员与归属规则，再用 `@WebMvcTest` 和必要的 Testcontainers 测试覆盖 HTTP 与跨租户拒绝。
- Implementation outline: 在 `tenant.web` 暴露 **`/api/tenants/users/**`** 与 **`/api/tenants/invitations/**`**（REST：POST 创建、GET 列表/详情、DELETE 撤销）；在 `tenant.application` 集中实施租户归属、`tenant.user.invite`、`tenant.user.manage` 与最后管理员规则；错误响应复用现有能力内处理机制。
- Verification: `./mvnw -q test -Dtest=TenantInvitationsMockMvcTests`.
- Done: true（2026-10-09；`TenantInvitationController`、`TenantUserController`、`TenantUsersMockMvcTests`、`TenantUserManagementPostgresTest`）。

## Slice 4: 租户用户 `/api/users/me/**`、会话资格与交付证据

- Goal: 租户 JWT（`ctx=tenant`）可访问 **`/api/users/me/**`**（不新增 `/api/tenants/users/me`）；停用租户后 login/refresh 均失效；`ctx=system` 不得调用租户管理员路径（如 `/api/tenants/users`）。
- Acceptance: AC-1, AC-2, AC-3.
- Depends on: Slice 3.
- Test or proof: MockMvc 断言 tenant JWT 可读/改自身 profile；system JWT 访问 `/api/tenants/users/**` 拒绝；租户停用后 login/refresh 失败。
- Implementation outline: 扩展 `UserProfileController`（或等价入口）接受 `ctx=tenant` 并校验 JWT `tid` 与 `users.tenant_id`；复用 `UserProfileService` 改密与会话撤销；会话签发与 refresh 检查租户状态；同步 README、PRD 与 GLOSSARY。
- Verification: `./mvnw -q verify`.
- Done: true（2026-10-09；`UserProfileController` 租户 ctx、`UserSessionService` 租户停用拒绝 login/refresh、V1 RBAC 为 TenantAdmin/TenantUser 增加 profile permission）。
