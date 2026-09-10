---
id: "TASK-111"
type: "task"
title: "自动验证原生安装权限与服务恢复"
status: "doing"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-110"]
related: []
implements: ["CAPABILITY-008#REQ-003", "CAPABILITY-008#REQ-005", "CAPABILITY-008#REQ-006", "CAPABILITY-008#AC-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "development/works/WORK-050", "docs/engineering", ".github/workflows", "apps/judge-engine", "deploy/sandbox-linux", "contracts"]
write_paths: ["development/works/WORK-050", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/install/verify-native.py", "deploy/sandbox-linux/install/verify-faults.py", "deploy/sandbox-linux/install/verify-lifecycle.py", "deploy/sandbox-linux/install/verify-uninstall.py", "deploy/sandbox-linux/install/verify-capabilities.py", ".github/workflows/ci.yml"]
forbidden_paths: ["apps/judge-engine/internal", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "contracts", "compose.yaml", "compose.legacy.yaml", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md", "apps/server", "apps/web", "deploy/sandbox-linux/install/manage.py", "deploy/sandbox-linux/install/layout.py", "deploy/sandbox-linux/install/render.py", "deploy/sandbox-linux/systemd"]
created_at: "2026-09-10"
updated_at: "2026-09-11"
---

# TASK-111：自动验证原生安装权限与服务恢复

## 任务目标

在独立一次性VM运行与WORK-048相同的正式安装、权限、资源及服务恢复断言。

## 依据

CAPABILITY-008 REQ-003/005/006与AC-003；复用TASK-110当前构建与可信rootfs准备。

## 可查看范围

以 front matter 的 read_paths 为准；已有实现只读用于核对不变量。

## 可修改范围

以 write_paths 为准。仅测试编排、工作流、报告和用例适配，不实现另一套沙箱或业务服务。远端动作仅指审核后的一次性 GitHub VM，不能连接现有服务器或 IDEA。

## 禁止修改

以 forbidden_paths 为准。禁止修改生产代码、协议、限额/权限来迁就测试；禁止使用旧私有配置、数据和凭据；不得清理未知所有权资源。发现真实缺陷先记录并另拆修复边界。

## 依赖

依 depends_on 顺序推进；本轮只是文档，需人工意图闸及后续实施授权，当前 todo 不可直接执行。

## 产出

安装准备/管理调用与suite驱动、既有verify脚本可复用适配、原生job及安装回执/环境/恢复报告。manage/render/layout/systemd保持只读，发现缺陷另立修复任务。

## 完成标准

- [ ] 全新账号/路径/端口预检、安装回执及root保护校验、实际新指纹和24项限额可核对。
- [ ] verify-native的编译运行、全线程权限、六namespace和只读挂载通过。
- [ ] 七cap正常链与逐项删减拒绝通过，临时drop-in恢复后重新核验安装摘要。
- [ ] 缺helper配置/rootfs清单/helper二进制拒绝及恢复、judge/sandbox/helper在途崩溃及清理通过。
- [ ] 卸载保留账号/配置/数据，restore仅恢复原单元再显式start，身份一致性及清理通过。
- [ ] 不enable、不整机重启、不接现有后端；每阶段串行、预算封顶、失败保留证据并清理本次资源。

## 验证

以现有verify-native/lifecycle/faults/capabilities/uninstall入口对照清单逐项运行。注册需要控制面时仅用隔离的协议测试接收器检查注册事实，明确标为部署层；完整真实Java注册及校准另由TASK-112负责，不能混称真实业务。

## 风险

固定单元/account只有确认全新VM且无冲突才能安装。安装器不允许覆盖已有部署，不能为CI跳过所有权核验或降低权限断言。

## 执行记录

- 2026-09-10：已盘点现有安装与verify入口，未实施。
- 2026-09-11：等待前置修复验收期间完成只读准备：确认可复用现有prepare构建和五类verify脚本；部署层使用有界本地协议接收器核验新节点注册，真实Java另归TASK-112。原生安装使用固定账号、路径及单元，现有kernel所有权清理未覆盖这些资源，实施时须单独记录本次安装所有权，并覆盖部分安装、故障中断及取消清理；不得将VM最终销毁当作已证明无残留。未开始TASK-111编码或部署。
- 2026-09-11：状态变更：todo → ready。原因：意图与实施授权已存在，TASK-110完成且WORK-051已验收，原生部署测试边界明确
- 2026-09-11：状态变更：ready → doing。原因：实现一次性VM原生安装、逐项恢复验证与所有权清理

- 2026-09-11：新增native编排、受限协议接收器、所有权清理、严格逐项结果校验及11项CI反例；独立sandbox-native工作流已接线。基础55项单测、AST/shell和actionlint通过；未发布或取得Linux部署证据，保持doing，详见VERIFY-051。
- 2026-09-11：用户明确授权本批TASK-111原生部署CI、测试及WORK-051验收交接记录commit + push到origin/main，并在一次性GitHub Linux VM运行；准备发布精确SHA进行首次实测。
