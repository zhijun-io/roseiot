# 本地 Spec 与任务文件

本项目使用 Matt skills 默认的本地 Markdown tracker。Spec 与任务文件纳入 Git，离线可读、可评审；不创建或引用 GitHub Issue。

## 文件布局

- `.scratch/<feature-slug>/spec.md`：一个功能或垂直切片的需求契约。
- `.scratch/<feature-slug>/issues/<NN>-<slug>.md`：一个可独立实施的 tracer-bullet 任务，每个任务单独一个文件，按依赖顺序从 `01` 编号。
- `.scratch/README.md`：Spec 索引；具体任务状态和阻塞关系以各任务文件为准。

Spec 使用 `Problem Statement`、`Solution`、`User Stories`、`Implementation Decisions`、`Testing Decisions`、`Out of Scope` 和 `Further Notes` 等章节。验收标准使用 Markdown checkbox，以便在实现过程中追踪。

任务沿用 Matt skills 的本地模板：

```markdown
# <NN>: <任务标题>

**What to build:** 用户可见或可验证的端到端行为。

**Blocked by:** 阻塞任务编号和标题，或 `None (can start immediately)`。

**Status:** ready-for-agent

- [ ] 验收标准
```

任务应是可独立演示或验证的纵向切片，而不是按数据库、API、UI 等技术层横向拆分。`Blocked by` 明确列出真正的前置任务；没有未完成阻塞项的任务才可标为 `ready-for-agent`。任务被阻塞时，状态设为 `blocked` 并保留阻塞关系。依赖完成后，维护者将任务状态更新为 `ready-for-agent`。

## 状态

本项目在任务文件的 `Status` 字段使用以下状态：

- `needs-triage`：尚待维护者评估。
- `needs-info`：需要补充需求信息。
- `ready-for-agent`：需求、验收标准与依赖已明确，可以开始实施。
- `ready-for-human`：需要人工判断或外部操作后实施。
- `in-progress`：已开始实施。
- `blocked`：存在尚未解除的阻塞；在 `Blocked by` 中写明依赖。
- `done`：验收标准全部满足且验证证据已记录。
- `wontfix`：明确决定不实施。

状态不替代验收标准。完成任务时勾选验收标准，并在相关实现提交或验证记录中留下证据；不要只改状态。

## 操作约定

- 创建或更新 Spec 时，沿用 `.scratch/<feature-slug>/spec.md`；拆分实现工作时，为每个任务创建独立 Markdown 文件。
- 先列出任务之间的真实阻塞关系；编号按依赖顺序排列，且每个 `Blocked by` 使用对应编号和标题。
- 任务描述聚焦用户可见或可验证的端到端行为，避免具体文件路径和代码片段。
- 所有本地任务使用本地路径和编号，不保留远程 Issue 编号或链接。
- 未经用户要求，不自动提交或推送。
