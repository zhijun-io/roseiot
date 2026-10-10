# 租户自定义角色 Spec（草案）

> 状态：Draft — 待评审与批准
> 关联需求：`IAM-004`、`IAM-006`、`IAM-011`、`IAM-012`
> 决策来源：[访谈 Q519](../product/interview-decisions.md)、[统一 User 身份与 RBAC ADR](../adr/2026-10-08-identity-user-rbac.md)

## 目标

允许租户管理员从系统维护的 permission 目录组合权限并管理本租户角色，以满足不同租户的成员授权需求。系统继续控制 permission 的定义；本功能不提供自定义 permission、用户组、ABAC 或角色继承。

## 数据归属

- 继续使用现有 `role`、`role_permission`、`user_role` 表，不新增角色实体表。
- `role.tenant_id IS NULL` 表示系统内置角色；非空表示角色所属租户。系统内置角色不能由租户修改或删除。
- 租户角色 code 在所属租户内唯一；同一 code 可在不同租户重复。角色权限只能引用系统 permission 目录中的 code。
- `user_role.scope_tenant_id` 表示分配范围。租户角色只能分配给所属租户的用户；内置 `TENANT` 角色可按其 scope 分配给租户用户；`SYSTEM` 角色只能分配给系统用户。
- `role` 与 `role_permission` 当前由 MyBatis-Plus 租户插件忽略。访问这两张表时必须显式限制系统角色或当前租户角色，并验证角色归属；不能把插件忽略规则当作授权。

## 行为约束

- 仅具备 `tenant.role.manage` 的租户管理员可创建、查看、修改和删除本租户角色。该 permission 由系统目录提供并授予内置 `TenantAdmin`。
- 管理角色时，客户端只能选择 permission 目录中存在的 permission；不得通过请求参数创建或修改 permission。
- 角色标识使用稳定 ID；角色 code 不作为跨租户标识。邀请及成员角色分配必须在服务端验证 role ID 属于当前租户或是可用的内置租户角色。
- 不得删除仍被成员分配或待处理邀请引用的角色；修改角色或角色 permission 后，相关成员旧授权必须立即失效，并沿用现有授权修订版本和会话撤销机制。
- 租户任意分配、停用成员或修改角色 permission 后，都必须至少保留一名启用成员同时拥有 `tenant.user.manage` 与 `tenant.user.invite`。并发修改须在事务内串行检查。
- 所有请求默认拒绝；目标租户来自已认证主体，不接受请求体或路径中的租户 ID 覆盖当前租户。

## API 与验收标准

具体 HTTP 路径、请求/响应字段、错误码、角色列表可见范围，以及被引用角色的删除响应，须在 Spec 获批前定稿。至少验收：

- 租户只能读写本租户角色，不能读写系统角色或其他租户角色。
- 不存在的 permission、跨租户 role ID、错误 scope 和无 `tenant.role.manage` 权限的请求均被拒绝。
- 创建/更新角色权限后，旧 access token 与 refresh session 不能继续使用已撤销权限。
- 删除未引用角色成功；删除被用户或邀请引用的角色失败且数据不变。
- 并发移除管理员权限的操作不会使租户失去最后一名有效管理员。
- 迁移与所有现有认证、邀请、成员管理测试通过。

## 实施门禁

本文件是待评审草案。获批后再编写 Implementation Plan；Plan 获批后实施。实现前还需明确当前 `targetRole` 字符串 API 到稳定 role ID 的兼容或迁移策略。
