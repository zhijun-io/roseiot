# 本地任务状态

本项目通过 Markdown 任务文件中的 `Status` 字段跟踪工作，不使用 GitHub 标签。

| 状态 | 含义 |
| --- | --- |
| `needs-triage` | 尚待维护者评估 |
| `needs-info` | 需要补充需求信息 |
| `ready-for-agent` | 需求与验收标准清楚，可以开始实施 |
| `ready-for-human` | 需要人工判断或外部操作后实施 |
| `in-progress` | 已开始实施 |
| `blocked` | 存在尚未解除的阻塞，任务中需说明原因 |
| `done` | 验收标准全部满足且验证证据已记录 |
| `wontfix` | 明确决定不实施 |

依赖关系由 `Blocked by` 字段表达，与状态分开维护。只有所有依赖任务均为 `done`，任务才可从待办进入 `ready-for-agent` 或 `in-progress`。
