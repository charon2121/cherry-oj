---
id: "TASK-103"
type: "task"
title: "修复独立复核的槽位身份与errno偏差"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-102"]
related: []
implements: ["IMPROVEMENT-004#REQ-001"]
verifies: []
tags: []
read_paths: ["apps/judge-engine/internal/sandbox", "development/works/WORK-048"]
write_paths: ["apps/judge-engine/internal/sandbox/helper/helper.go", "apps/judge-engine/internal/sandbox/helper/server_linux_amd64.go", "apps/judge-engine/internal/sandbox/helper/identity.go", "apps/judge-engine/internal/sandbox/helper/identity_test.go", "apps/judge-engine/internal/sandbox/launcher/exec_linux_amd64.go", "apps/judge-engine/internal/sandbox/launcher/errno.go", "apps/judge-engine/internal/sandbox/launcher/errno_test.go", "development/works/WORK-048"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/internal/judge"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-103：修复独立复核的槽位身份与errno偏差

## 任务目标

按冻结设计为每个并发槽分配不同payload/init身份，错误上报保留真实errno。

## 依据

DESIGN-042独立复核修正，独立复核两项P2发现。属于原身份隔离与错误检查要求的落实。

## 可查看范围

以read_paths为准。

## 可修改范围

以write_paths为准。helper配置验证、身份派生/编号令牌、启动探测；launcher错误提取及测试。

## 禁止修改

以forbidden_paths为准，不改公开契约、judge或原有服务，不削减设计要求。

## 依赖

TASK-102已done，用户已授权实施与独立复核。

## 产出

独立身份分配和errno保真实现、本地回归、TASK-098实机证据与独立复查结论。

## 完成标准

- [x] 全部派生UID/GID有效且互不重叠；编号槽直到回收完成才归还。
- [x] 每槽启动能力经过真实探测；并发实机观测到不同身份。
- [x] 包装errno保留，非errno失败明确为阶段错误；ENOSYS/EINVAL实机注入通过。
- [x] 本地race/vet与Linux构建通过，独立复核修复无未解决问题。

## 验证

配置碰撞/越界与编号槽测试；全模块race/vet；Linux阶段故障、并发身份与1000次回收；独立复查。

## 风险

部署需保留完整身份范围；正式托管和capability白名单在TASK-099完成。

## 执行记录

2026-09-10：精确任务范围与修复依据已明确，原实施授权内修复。
- 2026-09-10：状态变更：todo → ready。原因：独立复核指出两项未满足原设计的P2，修复机制和精确路径已明确
- 2026-09-10：状态变更：ready → doing。原因：实现每槽身份和errno保真，并安排原复核者复查
- 2026-09-10：状态变更：doing → done。原因：两项独立复核P2已修复并复查通过；最终Linux不同槽身份、EINVAL/ENOSYS及1000次回收全部通过

## 最终完成记录（2026-09-10）

最终构建通过boundary-04错误阶段/errno、fault-08并发身份与容量、chain-02 C++整链/1000次/并发2、fault-09崩溃恢复。独立复核两项P2由TASK-103修复，原复核者复查无新增阻断；验证范围与未穷举故障组合在VERIFY-049明确区分。原始日志已归档本地临时证据目录；六个本轮独占目录与六个state在检查无进程/挂载/cgroup且所有权标记匹配后清理。首站完成，其他平台按矩阵保持待验证或明确不支持，正式部署/业务闭环留TASK-099/100，未代签验收闸。
