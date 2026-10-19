# 架构决策记录

本目录记录跨模块、长期有效且已确认的架构决策。具体用例、API 字段、验证场景和仍未确定的参数由对应 Spec 维护；实现方案不得静默违反 Accepted ADR。

## 决策

- [Roseiot 后端与 Nextboard 的开发边界](2026-10-19-backend-and-nextboard.md)
- [用户租户归属与角色范围](2026-10-19-tenant-identity-and-role-scope.md)
- [基于分层资源目录的 RBAC](2026-10-19-resource-based-rbac.md)
- [受控的平台与租户账户创建](2026-10-19-controlled-account-provisioning.md)
- [权限目录的代码定义与数据库投影](2026-10-19-permission-catalog-ownership.md)
- [审计记录与认证事件的独立边界](2026-10-19-audit-and-auth-event-boundaries.md)
