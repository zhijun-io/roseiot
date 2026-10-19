# 架构决策：Roseiot 后端与 Nextboard 的开发边界

- **状态**：Accepted
- **日期**：2026-10-19

## 决策

- Roseiot 后端使用 Spring Boot 开发，并遵循全局 `spring-boot-developer` 技能与本项目文档约定。
- Roseiot 的目标形态是多租户平台，逐步建设管理、通知、IoT 和 AI 等业务模块。管理模块包括用户、角色、资源和系统设置等能力。
- 后端默认采用模块化单体。按已确认需求逐步建立功能边界，不预建尚无用例的空模块；功能间通过确有调用方的 Service API 协作。
- Nextboard 是独立的 Next.js Admin Dashboard 项目，不属于 Roseiot 后端源码或运行链路。
- Nextboard 不调用 Roseiot API；页面只使用模板内的演示数据和交互状态。
- Nextboard 的导航、菜单和页面随 Roseiot 后端功能规划逐步完善，用于呈现和演进对应的管理体验。功能对应不代表前后端存在运行时依赖或 API 集成。
- Roseiot 仓库根目录的 `nextboard` 软链接仅为本地查看入口，实际源码由同级 Nextboard 项目维护。

## 后果

- 后端开发和 Nextboard 页面开发可以围绕同一产品能力推进，但两者独立构建、运行和交付。
- Nextboard 中的演示数据不代表真实后端状态，不用于确认后端 API 行为。
- 后端功能成熟后，如需接入真实 API，必须通过新的需求与架构决策明确改变当前边界。
