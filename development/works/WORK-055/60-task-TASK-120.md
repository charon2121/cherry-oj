---
id: "TASK-120"
type: "task"
title: "接入共享软件包与独立冷下载CI"
status: "doing"
work: "WORK-055"
owners: ["codex/root"]
depends_on: ["TASK-119", "PLAN-039"]
related: []
implements: ["IMPROVEMENT-005#REQ-001", "IMPROVEMENT-005#REQ-002", "IMPROVEMENT-005#REQ-004", "IMPROVEMENT-005#REQ-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-055", "development/works/WORK-054", "development/works/WORK-050", "docs/engineering", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/build-release.sh", ".github/workflows/sandbox-download-cold.yml"]
write_paths: ["development/works/WORK-055", ".github/workflows/ci.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/ci/workflow_test.py", "deploy/sandbox-linux/ci/report.py", "deploy/sandbox-linux/ci/report_test.py", "deploy/sandbox-linux/ci/README.md"]
forbidden_paths: ["apps", "contracts", "compose.yaml", "deploy/backend", "deploy/sandbox-linux/rootfs/ubuntu24-amd64-smoke.lock.json", "deploy/sandbox-linux/rootfs/build.py", "deploy/sandbox-linux/install", "deploy/sandbox-linux/systemd", "AGETNTS.local.md"]
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# TASK-120：接入共享软件包与独立冷下载CI

## 任务目标

按DESIGN-049和PLAN-039完成标题范围，不自行改变预算、测试判据或正式部署默认行为。

## 可查看范围

以front matter精确路径为硬边界。包锁与rootfs构建器可读不可写；apps、安装器、systemd和用户配置不可改。需要新路径先升级计划，不先写代码。

## 依赖

WORK-055意图闸及用户明确实施许可；文档阶段保持todo。TASK-120另依赖TASK-119完成。

## 产出

实现、匹配的正反例测试和VERIFY-056证据；TASK-119负责传输/文件边界，TASK-120负责工作流/身份与运行证据。

## 完成标准

- [ ] 本任务负责的REQ与AC有实际可复现证据，失败不转为PASS。
- [ ] 基础回归与任务相关负例通过，独立复核无未解决阻断。
- [ ] 超时、取消及校验失败清理完整；未修改禁写路径。

## 验证

执行PLAN-039对应阶段；公网验证仅GitHub一次性VM。单测通过不代表冷下载或业务通过。新工作提交、推送及复核委派须获对应授权。

## 执行记录

- 2026-09-11：仅形成任务边界，未实施。
- 2026-09-11：状态变更：todo → ready。原因：TASK-119独立复核完成，意图与实施授权有效
- 2026-09-11：状态变更：ready → doing。原因：开始已批准共享缓存与独立冷下载工作流接线

## 依据

implements列出的IMPROVEMENT-005要求及DESIGN-049、PLAN-039。

## 可修改范围

只允许front matter的write_paths，任务记录仅写WORK-055。

## 禁止修改

front matter的forbidden_paths是硬边界，不修改现有业务和用户数据。

## 风险

发现现有文件安全或网络实现需超出精确路径时先改计划，不顺手重构其他模块。

- 2026-09-11：共享包/缓存/冷workflow、身份与README接线完成；134基础测试及独立workflow复核通过。待本工作提交推送授权和真实Linux冷/热及跨VM验证，保持doing；见VERIFY-056。
