---
id: "DESIGN-004"
type: "design"
title: "按类型与风险编排开发流程"
status: "approved"
work: "WORK-004"
owners: ["codex/root"]
depends_on: ["CHANGE-003"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# DESIGN-004：按类型与风险编排开发流程

## 背景

CHANGE-003 定义了类型化流程、增量规则和多对多 artifact 关系。原模型把阶段的适用性写在 status 中，
既没有真实进度，也无法说明阶段为什么出现，导致文件选择与流程控制耦合。

## 目标与限制

目标是让 `00-work.md` 成为可计算的流程实例：先保持 WORK Type 的业务语义，再叠加风险控制；同时让
Markdown 只在有独立信息产物时生成。限制是继续使用 JSON 子集 front matter、标准库 Python、现有
文档类型、状态机和单工作项目录，不引入数据库或第三方工作流引擎。

## 整体方案

使用声明式 `WORKFLOW_PROFILES` 定义五种类型的有序阶段。每个模板项包含阶段 ID、中文 label、默认
document type、是否 required 以及能否被快速流程跳过。`select_workflow` 复制基础模板后依次应用：

1. fix 复杂度规则；
2. fast 规则；
3. high/critical 或 system 风险影响规则；
4. delivery 和专项 concern 规则；
5. required checks 与 human confirmations 分配。

输出阶段使用 requirement 与 status 分离，并用 status_source 区分事实推导和显式推进，同时记录
artifacts、checks、source、reason。创建文档后，
`bind_workflow_artifacts` 按文档类型绑定永久 ID；TASK 同时绑定 tasks 与 development。随后
`synchronize_workflow` 根据实际状态计算阶段进度。

## 模块与数据

- `WORKFLOW_PROFILES`：五种 WORK Type 的基础顺序与默认 artifact 类型。
- `select_workflow`：增量规则、必需文档、检查和人工确认的唯一生成入口。
- `bind_workflow_artifacts`：从本 WORK 文档建立阶段到永久 ID 的零到多关系。
- `synchronize_workflow`：根据文档、TASK、VERIFY、blocking 和 WORK 状态计算进度。
- `validate_documents`：重算期望流程，校验配置、artifact 归属、同步状态和 WORK 状态边界。
- `rebuild-flow`：迁移旧 workflow 或在分类、风险输入变化后重建。
- `set-stage`：仅推进无 artifact 的复核、上线、观察等操作阶段。

## 接口与状态

阶段 requirement 为 required/optional；进度为 pending/ready/doing/done/skipped/blocked。`flow` 输出类型、
中文阶段、必需性/进度、artifacts 及 checks。文档状态变化会同步所属 WORK；`refresh` 同时刷新流程与
WORK。WORK 进入 ready、implemented、verified、released、confirmed 时，分别检查 tasks、development、
verification、release 或全部 required 阶段边界。

`set-stage WORK-004 observe doing --reason ...` 只适用于 artifacts 为空的阶段。有 artifact 的阶段由
对应文档或任务推进；clarify 由 blocking_items 推进；required 阶段禁止 skipped。

## 安全与失败

流程重建不改变文档 ID、正文或关系，先在内存中重建全部 WORK，整体验证通过后才写回。任何不存在或
跨 WORK 的 artifact、类型模板漂移、未同步阶段、越过状态边界都会失败。人工确认列表仍阻止 WORK
进入 ready，工具不会根据测试自动完成产品、风险、发布或线上判断。

## 监控与部署

无需业务部署。CI 中的 CLI 端到端测试、`scripts/work check`、Markdown 链接检查和 diff 检查是主要
信号；`flow` 输出用于人工观察阶段来源、门禁和实际进度。

## 迁移与兼容

执行 `scripts/work rebuild-flow`，从现有 WORK 的 work_type、risk、impact、concerns 和风险标记重建
workflow，再把同目录文档重新绑定为 artifacts。原 required_documents/checks/human_confirmations 一并
按规则重算。命令名、文档目录、永久 ID 和正文保持兼容；旧的 stage.status=required/not-needed 格式
不再接受，Schema 和 check 会阻止混用。

## 备选方案

1. 继续使用一条通用阶段列表：实现简单，但无法表达类型差异，not-needed 节点会污染实际流程。
2. 一阶段强制一文档：文件关系直观，但会为开发、复核、上线制造空文档，也无法表达一个 TASK 支撑
   任务与开发。
3. 把流程完全写进各 WORK，不提供模板：自由度高，但同类工作会漂移，风险规则无法统一校验。
4. 引入 BPMN/数据库引擎：能力更强，但明显超出当前仓库规模和本地 Markdown 协作需求。

选择声明式类型模板、增量 overlays 与 artifact 图，兼顾差异化、可计算性和文档轻量性。

## 风险与重审条件

当前模板和规则仍由代码维护，新增 WORK Type 或阶段需要同步实现、规范和测试。若出现大量合法的单项
流程定制、需要多人审批历史或并发状态变更，再引入显式 workflow overrides、独立事件日志或服务化
引擎；在此之前，禁止通过手改 workflow 绕开模板和风险门禁。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：声明式模板、绑定、同步、校验和迁移方案已复核
