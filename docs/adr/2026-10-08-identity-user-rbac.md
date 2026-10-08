# ADR：统一 User 身份与 RBAC 授权（RBAC-0）

- **状态**：已接受
- **日期**：2026-10-08
- **关联需求**：`IAM-001`～`IAM-012`、`PRD-001`、`PRD-002`
- **关联 Spec**：[系统用户认证](../sdd/2026-10-08-stage-1-system-user-auth-spec.md)、[Identity 与 RBAC](../sdd/2026-10-08-stage-1-identity-rbac-spec.md)、[租户与用户](../sdd/2026-10-08-stage-1-tenant-and-users-spec.md)

## 决策

- 所有人类登录主体均为一条 `users` 记录。`tenant_id IS NULL` 为系统用户；非空为租户用户。邮箱全局唯一，首版一个用户至多属于一个租户。
- 同一用户不能同时拥有系统和租户身份；需要两种身份时使用不同的用户与邮箱。
- 授权模型为 RBAC：`Permission`、`Role`、`RoleAssignment`。不使用 ThingsBoard `Authority` 枚举。
- 内置角色为 `SystemAdmin`（`SYSTEM`）、`TenantAdmin`（`TENANT`）和 `TenantUser`（`TENANT`）。首版不提供自定义角色或权限编辑器。
- 系统用户只分配 `SYSTEM` 角色；租户用户只分配所属租户内的 `TENANT` 角色。
- 系统管理 API 要求 `ctx=system` 与相应 permission。系统管理员不默认拥有租户设备数据或设备凭证读取权限。
- JWT 至少携带 `sub`、`ctx`、`azv`；租户上下文另携带 `tid`。请求边界按当前授权版本计算 effective permissions，不把完整权限集嵌入 JWT。
- 会话与恢复机制由系统、租户用户共用：短期 access JWT 与服务端可撤销、轮换的 opaque refresh token。
- 迁移采用空库基线。已有数据库不保证升级兼容，须清空并重建。

## 后果

- 身份、会话与授权使用单一模型，租户能力可复用认证基础设施。
- 每个租户资源入口必须校验 permission、主体租户与资源归属；无授权默认拒绝。
- 自定义角色、用户组、ABAC 与权限依赖属于后续独立 Spec。
