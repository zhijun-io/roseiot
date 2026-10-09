# 阶段 1 · 系统用户认证 Spec

> 状态：已交付

## HTTP Contract

| 方法 | 路径 | 认证 |
| --- | --- | --- |
| POST | `/api/login` | 匿名 |
| POST | `/api/token` | 匿名 |
| POST | `/api/logout` | Bearer |
| POST | `/api/logout-all` | Bearer |
| POST | `/api/noauth/password-reset/email` | 匿名（始终 **200**，无 body） |
| GET | `/api/noauth/password-reset?resetToken=` | 匿名（**200** + `{ "status": "valid"|"expired"|"invalid" }`） |
| POST | `/api/noauth/password-reset` | 匿名（body：`resetToken`、`password`；成功 **204**；无效 token **400** + `invalid_reset_token`） |
| GET | `/api/noauth/user-activation?activateToken=` | 匿名（**尚未实现**，**501**） |
| POST | `/api/noauth/user-activation` | 匿名（**尚未实现**，**501**） |
| GET | `/api/users/me` | Bearer（`sub` 对应用户；租户归属见 `users.tenant_id`） |
| PATCH | `/api/users/me/password` | Bearer（同上） |

## noauth 路径约定

- 前缀 **`/api/noauth/`**；资源段使用 **kebab-case**（如 `password-reset`、`user-activation`）。
- 子动作为额外路径段（如 **`POST …/password-reset/email`**），不用 camelCase 路径。

## Behavior

- `POST /api/login` 经 Spring Security `AuthenticationManager` + `UserDetailsService` 校验邮箱与密码；JWT 仍由应用会话服务签发。
- access JWT 含 `sub`、`sid`、`azv`（不含 `ctx`；系统/租户 API 以 `ROLE_*` 区分）。
- refresh token 为服务端可撤销的 opaque token；轮换后的重放撤销整个 family。
- 改密、密码重置确认和 logout-all 撤销所有 refresh；改密与密码重置确认递增 `authorization_version`。
- `/api/users/me/**` 对系统用户与租户用户均开放（**无** `/api/tenants/users/me`）；需 `user.profile.*` / `user.password.write`。
- 未知邮箱与密码错误返回同一认证失败语义；密码重置邮件请求不泄露账号是否存在。
- 登录与 `POST …/password-reset/email` 按邮箱限速；受保护资源默认拒绝。

验证：`./mvnw -q verify`。
