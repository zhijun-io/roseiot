# ADR：统一 User 身份与 RBAC 授权（RBAC-0）

- **状态**：已接受
- **日期**：2026-10-08
- **决策者**：产品 / 架构（访谈与实现澄清）
- **关联需求**：`IAM-001`～`IAM-010`、`PRD-001`、`PRD-002`；修订后的 `IAM-003`、`IAM-004`、`IAM-011`～`IAM-012`
- **关联 Spec**：Spec #2 平台认证；迁移与 RBAC-0 见 [`identity-user-rbac-migration-spec.md`](../sdd/2026-10-08-stage-1-identity-user-rbac-migration-spec.md)（已交付）；租户与成员见 [`tenant-and-members-spec.md`](../sdd/2026-10-08-stage-1-tenant-and-members-spec.md)（**Spec #3，待批准 / 未实现**）

## 背景

Roseiot 需要同时服务**平台运维**（租户生命周期）与**租户内 IoT 业务**（成员、设备、数据）。早期 Spec #2 为尽快交付，采用独立的 `platform_admin` 持久化与专用 API。后续建设租户用户认证时，若再复制一套 login / refresh / recovery，维护成本高且易漂移。

产品访谈已确认：邮箱全平台唯一、首版一个用户最多属于一个租户、系统管理员不应默认读取租户设备数据（Q38）、固定租户内权限边界（Q38）。同时允许调整不合理的产品边界，并参考 ThingsBoard 的**单 User 表 + 租户归属**思路，但**不**采用 Community 版的 `Authority` 枚举作为授权模型；授权统一为 **RBAC**，并为 **ABAC** 与 ThingsBoard PE 风格的扩展（用户组、权限依赖等）预留演进空间。

## 决策

### 1. 身份：单一 `User` 聚合

- 所有**人类登录主体**对应一条 `User` 记录（稳定内部 ID + 规范化邮箱 + 凭证字段或关联凭证表）。
- **`tenant_id`**
  - `NULL`：**系统用户**（system user），不参与任何租户的成员归属。
  - 非 `NULL`：**租户用户**，且首版**至多绑定这一个租户**（与 Q59 一致）。
- **互斥**：同一 `User` 行不能既表示系统用户又绑定租户；「系统管理员兼任租户用户」通过**不同 User 记录（不同邮箱）**实现，而不是同一行上的双 hat。
- **设备**不是 `User`；设备凭证与设备业务归属 **`device` 能力包**（与 `identity` 并列），不纳入本 ADR。

### 2. 授权：RBAC-0（无 `Authority` 枚举）

- **不使用** ThingsBoard 式 `SYS_ADMIN` / `TENANT_ADMIN` 等 **Authority** 作为授权来源。
- 授权由以下概念组成（持久化命名在 Spec 中固定）：
  - **Permission**：稳定字符串键（如 `tenant.create`、`device.read`），与 PRD / 能力域对齐，增量定义。
  - **Role**：命名角色；带 **作用域种类** `SYSTEM` 或 `TENANT`。
  - **RoleAssignment**：`User` ↔ `Role`，且 assignment 的作用域必须与 `User.tenant_id` 一致：
    - 系统用户：仅可分配 `SYSTEM` scope 的角色；assignment 不携带租户 ID。
    - 租户用户：仅可分配 `TENANT` scope 的角色；assignment 的租户 ID 必须等于 `User.tenant_id`。
- **首版（RBAC-0）**：仅 **内置角色** 与 **系统 seed 的 permission 集**，不提供租户自定义角色或权限编辑器 UI（与 PRD 非目标一致）。
- **内置角色（种子，名称可在 Spec 中微调）**：
  - `SystemAdmin`（`SYSTEM`）：覆盖 IAM-002 等平台租户生命周期能力；**默认不包含**租户设备数据、设备凭证读取类 permission（落实 Q38）。
  - `TenantAdmin`（`TENANT`）：对应 Q38 租户管理员权限包。
  - `TenantUser`（`TENANT`）：对应 Q38 租户用户权限包。
- **默认拒绝**：无 permission 即拒绝；API 层声明所需 permission（或集中 registry），Resource Server 统一求值。

### 3. 认证与会话（与 Spec #2 机制对齐）

- 邮箱 + 密码、短期 access token、**服务端可撤销的 opaque refresh**（轮换与族撤销）继续有效（`IAM-007`）。
- Access token claims（JWT 或等价）**至少**包含：
  - `sub`：User 内部 ID
  - `ctx`：`system` | `tenant`（与 `tenant_id` 是否为空一致）
  - `tid`：租户 UUID（仅 `ctx=tenant` 时必填）
  - `azv`：授权修订版本（整数）；角色 assignment 变更、主体停用、密码修改等安全事件时递增，并配合 refresh 撤销（`IAM-007`）
- **不在 access token 中嵌入完整 permission 列表**（RBAC-0）；请求边界加载 effective permissions（可缓存，键含 `userId` 与 `azv`）。
- **Token 类型**（如 refresh、recovery、邀请）属于**会话/凭证层**，不是 Role，也不复用 `Authority` 命名。

### 4. HTTP 与租户上下文

- **系统管理 REST**：系统 profile **`/api/users/me/**`**；租户生命周期 **`/api/tenants/**`**（Spec #3）；均需 **`ctx=system`**（或对应 permission）与请求边界 RBAC。
- **租户业务 REST**：从认证主体推导租户上下文（`User.tenant_id`），**不信任**请求体/Query/Header 中的租户 ID（`PRD-001`）。
- 登录入口 **`/api/auth/**`**（系统与租户共用）；profile 分 **`/api/users/me`**（系统用户）与 **`/api/tenants/users/me`**（租户用户，Spec #3）；**内部分派到同一套会话服务**。
- 租户停用、用户/成员停用等 **资格检查** 在 RBAC 之后执行（`IAM-009`）；后续可抽为显式 **ABAC Policy**，不推翻 RBAC。

### 5. 与 Spec #2 已实现代码的关系

- 当前 `platform_admin` 表与 Spec #2 行为**保持有效**，直至 **身份迁移 Spec 批准并交付**。
- 目标映射：`platform_admin` 行 → `User(tenant_id IS NULL)` + `RoleAssignment(SystemAdmin)`；迁移步骤与 AC 见身份迁移 Spec，不在本 ADR 展开。

## 备选方案（未采纳）

| 方案 | 未采纳原因 |
| --- | --- |
| 长期保留 `platform_admin` 与 `user` 双表双 auth | 重复会话/ recovery / 限速逻辑，与 IAM-007/008 漂移风险高 |
| ThingsBoard Community `Authority` 三档 | 与「RBAC + 未来 ABAC/PE」路线不一致；平台/租户边界与细粒度 permission 混在一个枚举里 |
| 首版 JWT 内嵌全部 permissions | 角色变更与 access TTL 冲突；token 膨胀 |
| 首版完整 ThingsBoard PE（用户组、权限依赖图） | 超出当前 PRD 范围；RBAC-0 表结构可扩展，不必一次建设 |

## 后果

### 正面

- 租户用户与平台用户共享认证机制与 RBAC 词汇表；Spec #3 可复用 refresh/密码/限速模块。
- Permission 键可测试、可审计（`AUD-001` 角色变更）。
- `azv` + refresh 撤销使角色变更可收敛到 IAM-007 语义。

### 负面 / 成本

- 每个租户资源 API 需声明 permission + 租户归属校验；测试矩阵大于硬编码 `if (admin)`。
- RBAC 表与 seed 在 **身份迁移 Spec**（[`2026-10-08-stage-1-identity-user-rbac-migration-spec.md`](../sdd/2026-10-08-stage-1-identity-user-rbac-migration-spec.md)）落地平台侧；租户 HTTP 消费留 Spec #3；Spec #2 与目标模型短期并存直至该 Spec 交付。
- 文档与 Glossary 需与「User 含系统用户」一致，避免「系统管理员不是 User」的旧表述。

## 演进（非 RBAC-0 交付范围）

1. **RBAC-1**：租户管理员创建自定义 Role（修订 PRD 非目标与 `IAM-004` 说明）。
2. **ABAC-1**：租户停用、最后一名管理员等规则以 Policy 接口显式化（在 RBAC 通过后求值）。
3. **PE-like**：User Group、Group Permission、permission 依赖链 — 见路线图候选；引入前需 Spec 与测量依据。

## 验证建议（实现 Spec 时）

- 平台用户无法调用租户设备 API；租户用户无法调用 `tenant.create`。
- `SystemAdmin` 默认无 `device.read` / 凭证读取 permission。
- 角色变更后 `azv` 递增且 refresh 族撤销；旧 access 在 TTL 内仍可能有效 — TTL 需在 Spec 中明确上界。
- 跨租户资源 ID：403/404 语义符合 `PRD-002`。
