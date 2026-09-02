# 工作项总览

这里集中列出项目中的全部工作项，方便不阅读技术细节也能快速了解项目正在做什么、做到哪里。
详细背景、成功标准和风险请打开每行的 `00-work.md`。本页由 `scripts/work` 维护，不手工修改。

| 编号 | 工作内容 | 类型 | 状态 | 风险 | 影响面 | 负责人 | 详细说明 |
|---|---|---|---|---|---|---|---|
| WORK-001 | 重建统一开发文档系统 | 整理维护 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-001/00-work.md) |
| WORK-002 | 交付 C++ ACM 答题闭环 | 产品功能 | 待确认 | 中 | 整个系统 | product/owner | [00-work.md](./works/WORK-002/00-work.md) |
| WORK-003 | 按工作项聚合开发文档 | 整理维护 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-003/00-work.md) |
| WORK-004 | 按类型与风险编排开发流程 | 整理维护 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-004/00-work.md) |
| WORK-005 | 修复开发文档 CI 的 clean checkout 链接校验 | 问题修复 | 验证通过 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-005/00-work.md) |
| WORK-006 | 按思维导图结构重写开发文档系统规范 | 整理维护 | 验证通过 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-006/00-work.md) |
| WORK-007 | 校正全局 PRD 与当前 MVP 基线的漂移 | 整理维护 | 验证通过 | 中 | 整个系统 | codex/root | [00-work.md](./works/WORK-007/00-work.md) |
| WORK-008 | 建立 Web 到 Gateway 的 REST 基础连通模块 | 基础能力 | 验证通过 | 中 | 多个模块 | codex/root | [00-work.md](./works/WORK-008/00-work.md) |
| WORK-009 | 建立统一的 Web REST 交换协议与请求基建 | 基础能力 | 验证通过 | 中 | 整个系统 | codex/root | [00-work.md](./works/WORK-009/00-work.md) |
| WORK-010 | 建立跨语言可观测性基础设施 | 基础能力 | 验证通过 · 已被取代 WORK-012 | 中 | 整个系统 | codex/root | [00-work.md](./works/WORK-010/00-work.md) |
| WORK-011 | 收敛 Go 领域日志调用 | 整理维护 | 验证通过 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-011/00-work.md) |
| WORK-012 | 撤回可观测性实现并保留追溯契约 | 整理维护 | 验证通过 | 中 | 整个系统 | codex/root | [00-work.md](./works/WORK-012/00-work.md) |
| WORK-013 | 建立用户身份与访问控制服务 | 基础能力 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-013/00-work.md) |
| WORK-014 | 统一登录空闲过期配置并修复提前掉线 | 问题修复 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-014/00-work.md) |
| WORK-015 | 建立 Cherry OJ Web 设计系统 | 基础能力 | 实现完成 | 中 | 整个系统 | codex/root | [00-work.md](./works/WORK-015/00-work.md) |
| WORK-016 | 修复设计系统发布后的文档 CI | 问题修复 | 实现完成 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-016/00-work.md) |
| WORK-017 | 建立 Web 设计系统代码基建 | 基础能力 | 已取消 | 中 | 整个系统 | codex/root | [00-work.md](./works/WORK-017/00-work.md) |
| WORK-018 | 解除 Web 对设计系统文档目录的依赖 | 整理维护 | 实现完成 | 中 | 整个系统 | codex/root | [00-work.md](./works/WORK-018/00-work.md) |
| WORK-019 | 设计 Cherry OJ 任务入口主页 | 产品功能 | 实现完成 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-019/00-work.md) |
| WORK-020 | 搭建用户端与管理端应用布局 | 产品功能 | 实现完成 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-020/00-work.md) |
| WORK-021 | 修复 IDEA 错误按叶子工程构建 user-service | 问题修复 | 已取消 | 低 | 多个模块 | codex/root | [00-work.md](./works/WORK-021/00-work.md) |
| WORK-022 | 微调双端应用布局页脚 | 产品功能 | 实现完成 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-022/00-work.md) |
| WORK-023 | 设计双端导航栏与导航功能组件 | 产品功能 | 实现完成 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-023/00-work.md) |
| WORK-024 | 重新设计登录页视觉与体验 | 产品功能 | 实现完成 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-024/00-work.md) |
| WORK-025 | 交付题库、题目与测试数据管理 | 产品功能 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-025/00-work.md) |
| WORK-026 | 为 Java 服务提供可直接启动的本地默认配置 | 基础能力 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-026/00-work.md) |
| WORK-027 | 把手写基础组件改为基于 shadcn 官方实现 | 整理维护 | 验证通过 | 中 | 多个模块 | codex/root | [00-work.md](./works/WORK-027/00-work.md) |
| WORK-028 | 修复后台用户列表偶发误跳登录页 | 问题修复 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-028/00-work.md) |
| WORK-029 | 新增页面主题切换入口 | 产品功能 | 验证通过 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-029/00-work.md) |
| WORK-030 | 修复后台题目列表间歇性 502 | 问题修复 | 实现完成 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-030/00-work.md) |
