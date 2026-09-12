---
id: "TASK-122"
type: "task"
title: "准备并发布固定软件包资产"
status: "todo"
work: "WORK-056"
owners: ["team/judge-engine"]
depends_on: ["TASK-121", "PLAN-040"]
related: []
implements: ["IMPROVEMENT-006#REQ-002"]
verifies: []
tags: []
read_paths: ["deploy/sandbox-linux", ".github/workflows", "development/works/WORK-056", "development/works/WORK-055"]
write_paths: ["deploy/sandbox-linux/ci", "development/works/WORK-056"]
forbidden_paths: ["apps", "contracts", "deploy/sandbox-linux/rootfs"]
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# TASK-122：准备并发布固定软件包资产

## 任务目标

准备可审核的准确包集、许可和对应源码；获得实际发布授权后发布固定资产。

## 依据

IMPROVEMENT-006、DESIGN-050 与 PLAN-040；具体要求由 implements 锚定。

## 可查看范围

以 front matter 的 read_paths 为准。TASK-122 另可只读查询本仓库 Release 能力与官方包源码索引。

## 可修改范围

以 front matter 的 write_paths 为准。TASK-122 的外部发布仅限本仓库本批经审核资产；设置变更及发布在实际清单交付、用户授权后执行。

## 禁止修改

不得修改包锁、默认部署下载器、应用或契约，不操作用户服务器，不删除既有发布。

## 依赖

依赖前置 TASK 完成以及意图闸和实施授权；提交推送、发布遵循 PLAN-040 的批次授权。

## 产出

确定性包归档、逐包和外层摘要、源码与许可材料、发布清单和固定资产身份。

## 完成标准

- [ ] 包归档仅含原锁的 56 个 .deb；源码版本与许可逐包核对。发布后重新下载核验摘要与不可变状态；未经实际内容审核不执行发布。
- [ ] 相关测试与文档校验通过，独立复核问题已处理，回收证据完整。

## 验证

本地包获取及安全负向单测、工作流语法、文档校验；按 PLAN-040 完成对应阶段的真实 Linux 获取/运行验证，记录 source、harness、lock、asset 和耗时。

## 风险

源码缺失或仓库不可变能力不满足时停止发布，快照恢复可以独立交付。

## 执行记录

- 2026-09-12：文档阶段，未实施。
