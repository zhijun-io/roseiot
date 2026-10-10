# API 路径

本文记录确认的 HTTP API 路径设计。表格明确区分已实现接口和目标设计；目标设计没有对应业务接口前，不代表可调用 API。

## 约定

- 登录使用 `POST /login`。
- 不使用 `/api`、`/noauth` 等统一路径前缀；认证要求由 Spring Security 按具体接口配置。
- 当前用户相关操作使用 `/me` 前缀。
- 集合资源使用复数名称，嵌套集合也使用复数名称。
- 不使用 `PATCH`、`201 Created` 或 `202 Accepted`。成功创建或更新时返回 `200 OK`；无响应体的更新或删除可返回 `204 No Content`。
- 密码重置和租户邀请 token 是一次性凭证，可以作为路径标识；成功使用后失效。
- 密码重置和邀请接受完成后不签发 refresh token。

## 已实现路径

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `POST` | `/login` | 使用邮箱和密码登录 |
| `GET` | `/me` | 获取当前用户资料 |
| `PUT` | `/me` | 更新当前用户名称 |
| `PUT` | `/me/password` | 修改当前用户密码 |
| `POST` | `/password-resets` | 请求密码重置邮件 |
| `GET` | `/password-resets/{token}` | 校验重置 token 并获取状态 |
| `PUT` | `/password-resets/{token}` | 提交新密码并完成重置 |
| `POST` | `/tenants` | 创建租户 |
| `PUT` | `/tenants/{tenantId}` | 完整更新租户名称和状态 |
| `GET` | `/tenants/{tenantId}/users` | 列出租户用户 |
| `PUT` | `/tenants/{tenantId}/users/{userId}` | 完整替换租户成员的状态和角色 |
| `GET` | `/tenants/{tenantId}/invitations` | 列出租户邀请，可按状态筛选 |
| `POST` | `/tenants/{tenantId}/invitations` | 创建租户邀请 |
| `GET` | `/tenants/{tenantId}/invitations/{invitationId}` | 获取指定邀请 |
| `DELETE` | `/tenants/{tenantId}/invitations/{invitationId}` | 撤销邀请 |
| `GET` | `/tenant-invitations/{token}` | 校验邀请 token 并获取状态 |
| `PUT` | `/tenant-invitations/{token}` | 接受邀请并设置密码 |

嵌套租户路径中的 `tenantId` 必须与认证用户的租户上下文一致；不能仅凭路径参数访问其他租户资源。

## 尚未实现路径

| 方法 | 路径 | 用途 |
| --- | --- | --- |
| `GET` | `/me/tenants` | 列出当前用户可访问的租户 |
| `GET` | `/users` | 查询用户列表 |
| `POST` | `/users` | 创建用户 |
| `GET` | `/users/{userId}` | 获取指定用户 |
| `PUT` | `/users/{userId}` | 更新指定用户 |
| `DELETE` | `/users/{userId}` | 删除指定用户 |
| `GET` | `/tenants/current` | 获取当前租户 |
| `GET` | `/tenants` | 查询租户列表 |
| `GET` | `/tenants/{tenantId}` | 获取指定租户 |
