# 阶段 1 · 系统引导 Spec

> 状态：已交付
> 当前实现：`BootstrapUserService`、`BootstrapApplicationRunner`

## Goal

在空数据库中以显式 CLI 创建恰好一名系统管理员；凭证只来自环境变量。

## Contract

- `--bootstrap` 仅在 `users` 中不存在 `tenant_id IS NULL` 的用户时成功。
- 必须提供 `BOOTSTRAP_USER_EMAIL` 与 `BOOTSTRAP_USER_PASSWORD`；邮箱规范化、密码校验失败时不写库。
- 成功时创建 `users` 行及 `SystemAdmin`（`SYSTEM`）角色绑定，密码仅保存 bcrypt 摘要。
- 已存在系统用户时以非零状态退出；错误输出不得含密码。
- bootstrap 模式不启动 HTTP 服务。

## Verification

- 单元测试覆盖输入校验与唯一引导。
- Testcontainers 覆盖 CLI 成功、重复执行失败与数据库写入。
- `./mvnw -q verify` 通过。
