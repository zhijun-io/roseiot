# 阶段 1 · 系统用户认证 Spec

> 状态：已交付

## HTTP Contract

| 方法 | 路径 | 认证 |
| --- | --- | --- |
| POST | `/api/auth/login` | 匿名 |
| POST | `/api/auth/token` | 匿名 |
| POST | `/api/auth/logout` | Bearer |
| POST | `/api/auth/logout-all` | Bearer |
| POST | `/api/auth/recovery/request` | 匿名 |
| POST | `/api/auth/recovery/confirm` | 匿名 |
| GET | `/api/users/me` | Bearer |
| PATCH | `/api/users/me/password` | Bearer |

## Behavior

- access JWT 含 `sub`、`sid`、`ctx=system`、`azv`。
- refresh token 为服务端可撤销的 opaque token；轮换后的重放撤销整个 family。
- 改密、恢复确认和 logout-all 撤销所有 refresh；改密和恢复确认递增 `authorization_version`。
- `/api/users/me/**` 仅接受 `ctx=system`，并要求对应的 `user.profile.*` permission。
- 未知邮箱与密码错误返回同一认证失败语义；恢复请求不泄露账号是否存在。
- 登录与恢复请求按邮箱限速；受保护资源默认拒绝。

验证：`./mvnw -q verify`。
