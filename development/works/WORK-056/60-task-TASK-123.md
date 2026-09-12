---
id: "TASK-123"
type: "task"
title: "接入固定资产并验证冷热全链 CI"
status: "todo"
work: "WORK-056"
owners: ["team/judge-engine"]
depends_on: ["TASK-122", "PLAN-040"]
related: []
implements: ["IMPROVEMENT-006#REQ-003"]
verifies: []
tags: []
read_paths: ["deploy/sandbox-linux", ".github/workflows", "development/works/WORK-056", "development/works/WORK-055"]
write_paths: ["deploy/sandbox-linux/ci", ".github/workflows/ci.yml", ".github/workflows/sandbox-download-cold.yml", "development/works/WORK-056"]
forbidden_paths: ["apps", "contracts", "deploy/sandbox-linux/rootfs"]
created_at: "2026-09-12"
updated_at: "2026-09-12"
---

# TASK-123：接入固定资产并验证冷热全链 CI

## 任务目标

让日常 CI 使用已发布固定资产，独立冷检查直取官方快照。

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

缓存键与来源身份接线、安全解包和负向测试、工作流及两次全新 Linux 运行证据。

## 完成标准

- [ ] 缓存未命中和命中各有实际证据；独立快照冷检查通过；三个消费者实际运行并记录各自结果。全量业务失败不能标为全链通过。
- [ ] 相关测试与文档校验通过，独立复核问题已处理，回收证据完整。

## 验证

本地包获取及安全负向单测、工作流语法、文档校验；按 PLAN-040 完成对应阶段的真实 Linux 获取/运行验证，记录 source、harness、lock、asset 和耗时。

## 风险

当前业务历史失败仍可能出现，保持原工作归属；无静默回退和自动放宽限额。

## 执行记录

- 2026-09-12：文档阶段，未实施。
