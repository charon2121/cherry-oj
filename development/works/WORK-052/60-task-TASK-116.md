---
id: "TASK-116"
type: "task"
title: "修复沙箱启动通信被信号中断时的处理"
status: "doing"
work: "WORK-052"
owners: ["codex/root"]
depends_on: ["ISSUE-016", "DESIGN-046", "DECISION-030", "PLAN-036"]
related: []
implements: ["ISSUE-016#REQ-001", "ISSUE-016#REQ-002", "ISSUE-016#REQ-003", "ISSUE-016#AC-001", "ISSUE-016#AC-002", "ISSUE-016#AC-003", "ISSUE-016#AC-004"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "development/README.md", "docs/engineering", "development/works/WORK-050", "development/works/WORK-052", "apps/judge-engine", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-052", "development/works/WORK-050", "apps/judge-engine/internal/sandbox/launcher/channel_linux.go", "apps/judge-engine/internal/sandbox/launcher/channel_linux_test.go", "apps/judge-engine/internal/sandbox/launcher/files_linux_test.go", "apps/judge-engine/tests/sandbox-linux/boundary/start_linux_test.go", "apps/judge-engine/tests/sandbox-linux/boundary/channel_linux_test.go", "deploy/sandbox-linux/ci/cases.json"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "apps/judge-engine/internal/sandbox/cgroup", "apps/judge-engine/internal/sandbox/helper", "apps/judge-engine/internal/sandbox/policy", "apps/judge-engine/internal/sandbox/launcher/init_linux_amd64.go", "apps/judge-engine/internal/sandbox/launcher/exec_linux_amd64.go", "deploy/sandbox-linux/install", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/rootfs", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md"]
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# TASK-116：确认并修复控制消息的信号中断

## 任务目标

确定性复现中断后消息/FD处理错误，完成最小修复和原错误/取消/回收回归。

## 依据

ISSUE-016 REQ-001～003、AC-001～004及DESIGN-046/DECISION-030/PLAN-036。

## 可查看范围

以read_paths为准；先读Go工程规范，沿调用链核对描述符与期限。

## 可修改范围

以write_paths为准。边界新增channel测试只注入信号及记录实际事件；cases.json只增加必要测试名，不减少原63项或预算。

## 禁止修改

以forbidden_paths为准；其余未列入write_paths的生产文件也不可修改，需要越界先重审设计。

## 依赖

用户已审核、签署意图闸并明确继续实施；当前doing，先确认具体中断阶段。

## 产出

旧红新绿、中断处理、消息/FD恰好一次及关闭/期限测试、精确SHA Linux证据和独立复核。

## 完成标准

- [ ] 确定性旧红新绿覆盖收发中断，不依赖偶发调度。
- [ ] 合法消息/FD一次交付；非法/短写/截断仍失败，FD全部回收。
- [ ] 连续中断、取消/关闭和期限有界，不重跑整请求或修改预算。
- [ ] 完整工程CI、63内核及10原生在精确SHA通过，无必需skip，清理完整。
- [ ] 独立复核有界性及边界，人工验收后交回WORK-050。

## 验证

本地Go race/vet、Linux必需测试及边界真实信号验证；记录每轮实际结果，不宣称未执行平台支持。

## 风险

描述符关闭后复用、附带FD重复交付与期限延长；无法在现有边界证明正确时暂停并升级方案。

## 执行记录

- 2026-09-11：建立待审材料，未改生产或相关Go测试。
- 2026-09-11：状态变更：todo → ready。原因：用户已签署意图闸并明确继续实施，依赖与读写边界已核验
- 2026-09-11：状态变更：ready → doing。原因：开始确定性中断复现及控制通道最小修复

- 2026-09-11：新增startup poll/receive阶段报错，以及原TestStartupBoundaries内必经的control-signal-observation：独立5秒子进程，待决线程信号配合Ppoll，随后一次消息/FD/EOF观测。尚未运行Linux信号测试，尚未修改channel生产实现。
