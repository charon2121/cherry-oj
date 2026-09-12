---
id: "TASK-119"
type: "task"
title: "实现CI包获取、校验与prepare包输入"
status: "done"
work: "WORK-055"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-005", "DESIGN-049", "DECISION-033", "PLAN-039"]
related: []
implements: ["IMPROVEMENT-005#REQ-002", "IMPROVEMENT-005#REQ-003", "IMPROVEMENT-005#REQ-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-055", "development/works/WORK-054", "development/works/WORK-050", "docs/engineering", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/build-release.sh"]
write_paths: ["development/works/WORK-055", "deploy/sandbox-linux/ci/packages.py", "deploy/sandbox-linux/ci/packages_test.py", "deploy/sandbox-linux/ci/prepare.py", "deploy/sandbox-linux/ci/prepare_test.py", "deploy/sandbox-linux/rootfs/download.py", "deploy/sandbox-linux/rootfs/download_test.py"]
forbidden_paths: ["apps", "contracts", "compose.yaml", "deploy/backend", "deploy/sandbox-linux/rootfs/ubuntu24-amd64-smoke.lock.json", "deploy/sandbox-linux/rootfs/build.py", "deploy/sandbox-linux/install", "deploy/sandbox-linux/systemd", "AGETNTS.local.md", ".github/workflows"]
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# TASK-119：实现CI包获取、校验与prepare包输入

## 任务目标

按DESIGN-049和PLAN-039完成标题范围，不自行改变预算、测试判据或正式部署默认行为。

## 可查看范围

以front matter精确路径为硬边界。包锁与rootfs构建器可读不可写；apps、安装器、systemd和用户配置不可改。需要新路径先升级计划，不先写代码。

## 依赖

WORK-055意图闸及用户明确实施许可；文档阶段保持todo。TASK-120另依赖TASK-119完成。

## 产出

实现、匹配的正反例测试和VERIFY-056证据；TASK-119负责传输/文件边界，TASK-120负责工作流/身份与运行证据。

## 完成标准

- [x] 包获取、校验与prepare实现的本地正反例有可复现证据；Linux缓存/artifact及冷下载集成证据由依赖本任务的TASK-120记录，不以本地结果冒充。
- [x] 基础回归与任务相关负例通过，独立复核无未解决阻断。
- [x] 超时、取消及校验失败清理负例通过；未修改禁写路径。

## 验证

执行PLAN-039对应阶段；公网验证仅GitHub一次性VM。单测通过不代表冷下载或业务通过。新工作提交、推送及复核委派须获对应授权。

## 执行记录

- 2026-09-11：仅形成任务边界，未实施。
- 2026-09-11：状态变更：todo → ready。原因：意图闸已签署且用户允许实施，任务路径和回退已明确
- 2026-09-11：状态变更：ready → doing。原因：开始包获取与文件校验实现和受控正反例
- 2026-09-11：状态变更：doing → done。原因：实现与本地负例完成，发布回收P2修复且独立复核通过；Linux接线验证交TASK-120

## 依据

implements列出的IMPROVEMENT-005要求及DESIGN-049、PLAN-039。

## 可修改范围

只允许front matter的write_paths，任务记录仅写WORK-055。

## 禁止修改

front matter的forbidden_paths是硬边界，不修改现有业务和用户数据。

## 风险

发现现有文件安全或网络实现需超出精确路径时先改计划，不顺手重构其他模块。

- 2026-09-11：包获取/安全复制/prepare接线及122项基础回归完成，含真实curl本地TLS、慢流重取与子进程回收；待单独授权独立复核，状态保持doing，证据见VERIFY-056。

- 2026-09-11：用户明确授权本工作只读独立复核；work055_review确认发布后取消/证据失败遗留目录P2。已加入私有准备阶段与按发布身份清理，补元数据失败、发布后取消及路径替换负例，等待复核确认。

- 2026-09-11：发布后P2已由work055_review复核通过，20项包测试全部通过；本任务按PLAN串行交TASK-120，工作整体与Linux验收仍未完成。
