---
id: "TASK-105"
type: "task"
title: "重构 sandbox 命令执行主线与本机协议表达"
status: "todo"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-104", "TASK-113"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-002", "CHANGE-013#REQ-003", "CHANGE-013#REQ-004", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-050", ".github/workflows/ci.yml"]
write_paths: ["apps/judge-engine/internal/sandbox", "apps/judge-engine/cmd/sandbox", "apps/judge-engine/cmd/sandbox-helper", "apps/judge-engine/tests/sandbox-linux", "apps/judge-engine/README.md", "development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-105：重构 sandbox 命令执行主线与本机协议表达

## 任务目标

让读者沿 handleRun 跟到用户 exec 并返回，显式表达跨进程交接、启动握手和资源完成条件。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的主阅读路线、阶段表达与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

用户要求先固化现有已验收测试；新增依赖 TASK-113 的自动基线交付。此前只能进行不改源码的阅读准备，不能以旧 WORK-048 手工报告替代本次 CI 基线。

TASK-104 的基线、映射与交接完成；任何扩大到 contracts/部署/权限模型的需要先升级定义与计划。
获得文档通过及后续实施授权、意图闸由人签署后才可推进；当前 todo 不代表可执行。

## 产出

sandbox 与相关 cmd 的结构调整、必要的协议/生命周期回归测试、包内 README 阅读入口；更新本工作符号映射与数值比对证据。

## 完成标准

- [ ] 顶层依次可见输入准备、执行监督、回收确认、产物交付和最终响应。
- [ ] helper.prepare 中真实启动与并发动作有准确名称；所有后台任务可找到停止和等待者。
- [ ] 每个协议端点及 FD/握手/错误阶段值均可追踪，两端原始值不变。
- [ ] 决定成功与否的回收动作在正常主线上显式可见，panic 兜底仍保留。
- [ ] 平台/线程/权限/产物发布不变量及线格式未改变。

## 验证

每批执行相关包 go vet 与 go test -race；核对数值与序列化结果。执行计划所需的 Linux 编译及已授权实机回归，无法执行须记录，交由最终验收保留未满足项。对最终 exec 阶段特别检查没有增加策略安装后的不允许调用。

## 风险

函数移动可能改变 defer、goroutine 捕获、FD 生命周期或 Go 线程状态；协议类型提取不能直接改变 cgroup 字段表示。安全依赖数值的提取仍需逐项比对。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
