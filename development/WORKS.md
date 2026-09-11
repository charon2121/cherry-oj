# 工作项总览

这里集中列出项目中的全部工作项，方便不阅读技术细节也能快速了解项目正在做什么、做到哪里。
详细背景、成功标准和风险请打开每行的 `00-work.md`。本页由 `scripts/work` 维护，不手工修改。

| 编号 | 工作内容 | 类型 | 状态 | 风险 | 影响面 | 负责人 | 详细说明 |
|---|---|---|---|---|---|---|---|
| WORK-001 | 重建统一开发文档系统 | 整理维护 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-001/00-work.md) |
| WORK-002 | 交付 C++ ACM 答题闭环 | 产品功能 | 验证通过 | 高 | 整个系统 | product/owner | [00-work.md](./works/WORK-002/00-work.md) |
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
| WORK-030 | 修复后台题目列表间歇性 502 | 问题修复 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-030/00-work.md) |
| WORK-031 | 统一页面任务优先布局并移除状态占位 | 产品功能 | 验证通过 | 中 | 多个模块 | codex/root | [00-work.md](./works/WORK-031/00-work.md) |
| WORK-032 | 修复 WORK-031 遗留的 CI 测试断言 | 问题修复 | 验证通过 | 低 | 多个模块 | codex/root | [00-work.md](./works/WORK-032/00-work.md) |
| WORK-033 | 重设计后台题目创建与编辑体验 | 工程改进 | 实现完成 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-033/00-work.md) |
| WORK-034 | 基于下载版重建 Web 设计系统并保留浅色主题 | 工程改进 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-034/00-work.md) |
| WORK-035 | 收敛设计系统为单一真源 | 整理维护 | 验证通过 | 中 | 多个模块 | claude/root | [00-work.md](./works/WORK-035/00-work.md) |
| WORK-036 | 建立页面构图层并修复前景色层级 | 工程改进 | 验证通过 | 高 | 整个系统 | claude/root | [00-work.md](./works/WORK-036/00-work.md) |
| WORK-037 | 重建内部身份信任链并消除管理请求 502 | 问题修复 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-037/00-work.md) |
| WORK-038 | 兼容常见测试数据 ZIP 并返回可操作校验错误 | 问题修复 | 验证通过 | 中 | 多个模块 | codex/root | [00-work.md](./works/WORK-038/00-work.md) |
| WORK-039 | 修复 JWT 签发 30 秒后被资源服务误拒绝 | 问题修复 | 验证通过 | 中 | 多个模块 | codex/root | [00-work.md](./works/WORK-039/00-work.md) |
| WORK-040 | 重构判题节点注册与测试数据交付 | 问题修复 | 验证通过 | 高 | 整个系统 | codex/root | [00-work.md](./works/WORK-040/00-work.md) |
| WORK-041 | 建立题目阅读与 Monaco 编码工作台 | 产品功能 | 验证通过 | 中 | 局部 | codex/root | [00-work.md](./works/WORK-041/00-work.md) |
| WORK-042 | 修复后台用户列表拒绝尚未过期的身份令牌 | 问题修复 | 验证通过 | 高 | 局部 | codex/root | [00-work.md](./works/WORK-042/00-work.md) |
| WORK-043 | 题目内提交记录与代码回看 | 产品功能 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-043/00-work.md) |
| WORK-044 | 题目内自定义输入运行 | 产品功能 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-044/00-work.md) |
| WORK-045 | 统一本地judging服务启动入口 | 整理维护 | 实现完成 | 低 | 局部 | codex/root | [00-work.md](./works/WORK-045/00-work.md) |
| WORK-046 | 统一服务配置与环境启动管理 | 整理维护 | 实现完成 | 中 | 多个模块 | codex/root | [00-work.md](./works/WORK-046/00-work.md) |
| WORK-047 | 恢复自定义运行并移除运行冷却 | 问题修复 | 验证通过 | 中 | 多个模块 | codex/root | [00-work.md](./works/WORK-047/00-work.md) |
| WORK-048 | Linux 沙箱隔离与资源计量硬化 | 工程改进 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-048/00-work.md) |
| WORK-049 | 按命令执行顺序重构 Go 判题引擎源码 | 整理维护 | 待确认 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-049/00-work.md) |
| WORK-050 | 将沙箱已验收回归固化为重构 CI | 基础能力 | 待确认 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-050/00-work.md) |
| WORK-051 | 修复沙箱连续请求完成与容量归还的竞态 | 问题修复 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-051/00-work.md) |
| WORK-052 | 修复沙箱启动通信被信号中断时的处理 | 问题修复 | 验证通过 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-052/00-work.md) |
| WORK-053 | 修复登录会话期限在数据库往返后的精度不一致 | 问题修复 | 执行中 | 高 | 多个模块 | codex/root | [00-work.md](./works/WORK-053/00-work.md) |
