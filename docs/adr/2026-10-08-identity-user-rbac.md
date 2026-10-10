# ADR：统一 User 身份与 RBAC 授权

- **状态**：已接受（2026-10-10 修订）
- **日期**：2026-10-08；最近修订 2026-10-10
- **关联需求**：`IAM-001`～`IAM-012`、`PRD-001`、`PRD-002`
- **关联 Spec**：[系统用户认证](../sdd/2026-10-08-stage-1-system-user-auth-spec.md)、[Identity 与 RBAC](../sdd/2026-10-08-stage-1-identity-rbac-spec.md)、[租户与用户](../sdd/2026-10-08-stage-1-tenant-and-users-spec.md)、[租户自定义角色（草案）](../sdd/2026-10-10-tenant-custom-roles-spec.md)

## 决策

- 所有人类登录主体均为一条 `users` 记录。`tenant_id IS NULL` 为系统用户；非空为租户用户。邮箱全局唯一，首版一个用户至多属于一个租户。
- 同一用户不能同时拥有系统和租户身份；需要两种身份时使用不同的用户与邮箱。
- 授权模型为 RBAC：`Permission`、`Role`、`RoleAssignment`。不使用 ThingsBoard `Authority` 枚举。
- 系统内置角色为 `SystemAdmin`（`SYSTEM`）、`TenantAdmin`（`TENANT`）和 `TenantUser`（`TENANT`）。租户可创建仅在本租户有效的角色，并从系统维护的 permission 目录中组合权限；租户不能创建或修改 permission。
- 系统用户只分配 `SYSTEM` 角色；租户用户只分配所属租户内的 `TENANT` 角色。
- 系统管理 API 要求 `ctx=system` 与相应 permission。系统管理员不默认拥有租户设备数据或设备凭证读取权限。
- JWT 至少携带 `sub`、`ctx`、`azv`；租户上下文另携带 `tid`。请求边界按当前授权版本计算 effective permissions，不把完整权限集嵌入 JWT。
- 会话与恢复机制由系统、租户用户共用：短期 access JWT 与服务端可撤销、轮换的 opaque refresh token。
- 迁移采用空库基线。已有数据库不保证升级兼容，须清空并重建。

## 后果

- 身份、会话与授权使用单一模型，租户能力可复用认证基础设施。
- 每个租户资源入口必须校验 permission、主体租户与资源归属；无授权默认拒绝。
- 租户角色存于现有 `role` 表，`tenant_id IS NULL` 表示系统内置角色，非空表示所属租户创建的角色；租户角色 code 在同一租户内唯一。`role_permission` 只引用系统 permission 目录。
- `user_role.scope_tenant_id` 表示角色分配的租户范围。租户角色只能分配给其所属租户成员；系统用户、其他租户以及客户端提供的租户 ID 均不能绕过该检查。
- `role` 和 `role_permission` 的访问必须显式限制在系统角色或当前租户角色。它们虽有租户归属语义，不得因 MyBatis-Plus 全局表忽略配置而省略应用层归属校验。
- 授权或角色权限变更必须通过现有授权修订版本与会话撤销机制立即生效。
- 租户必须始终保留至少一名启用且同时具备 `tenant.user.manage` 与 `tenant.user.invite` 的成员；分配、停用和角色权限修改均在事务内并发安全地检查此约束。
- 用户组、ABAC 与 permission 依赖属于后续独立 Spec。

## 修订记录

- 2026-10-10：依据产品决策允许租户从系统 permission 目录创建本租户角色；不开放 permission 编辑，并保留独立 Spec 与 Plan 门禁。
