---
id: "TASK-118"
type: "task"
title: "为CI软件包下载增加有界同源地址回退"
status: "doing"
work: "WORK-054"
owners: ["codex/root"]
depends_on: ["ISSUE-018", "DESIGN-048", "DECISION-032", "PLAN-038"]
related: []
implements: ["ISSUE-018#REQ-001", "ISSUE-018#REQ-002", "ISSUE-018#REQ-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-050", "development/works/WORK-054", "docs/engineering", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-054", "development/works/WORK-050", "deploy/sandbox-linux/rootfs/download.py", "deploy/sandbox-linux/rootfs/download_test.py", "deploy/sandbox-linux/rootfs/transport.py", "deploy/sandbox-linux/rootfs/transport_test.py", "deploy/sandbox-linux/ci/prepare.py", "deploy/sandbox-linux/ci/prepare_test.py", "deploy/sandbox-linux/ci/README.md"]
forbidden_paths: ["apps", "contracts", "compose.yaml", "deploy/backend", "deploy/sandbox-linux/rootfs/ubuntu24-amd64-smoke.lock.json", "deploy/sandbox-linux/rootfs/build.py", "deploy/sandbox-linux/install", "deploy/sandbox-linux/systemd", "AGETNTS.local.md", ".github/workflows"]
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# TASK-118：限定CI下载连接回退

## 任务目标

实现默认关闭、CI显式开启的同源地址连接回退，并验证没有削弱内容/TLS校验或超出原期限。

## 依据

ISSUE-018 REQ-001至003，DESIGN-048、DECISION-032、PLAN-038。

## 可查看范围

以read_paths为准。

## 可修改范围

以write_paths为准；仅下载连接适配与测试及一个CI参数，不修改安装、rootfs内容或业务。

## 禁止修改

以forbidden_paths为准；不得固定IP、换源、改包锁、关闭TLS验证、修改宿主DNS或用户服务。

## 依赖

本工作意图闸passed且用户明确允许实施，当前doing。用户已单独授权本批独立复核、commit/push main及远端CI。

## 产出

显式CLI选项、有限HTTPS连接适配、正反例与CI准备接线、按实际peer与锁定摘要记录的证据。

## 完成标准

- [x] 默认行为保持；确定性首地址TLS网络失败后下一地址成功形成旧红新绿。
- [x] 证书问题立即拒绝，HTTP或响应体失败不重放，大小/路径/摘要原检查通过。
- [x] DNS地址去重和最多8次、共同30秒、单次5秒及socket关闭受测；外层240秒预算保持。
- [ ] 真实Linux全部锁定包及构建摘要通过，或如实保留新的失败；未运行的业务不计PASS。
- [ ] 独立源码/证据复核完成并交付人工验收；不代签闸。

## 验证

现有basic.py会发现rootfs及CI目录下*_test.py，运行真实认证/浏览器的原job不改。受控TLS测试和实际GitHub结果分别列证据，不能以mock代替下载成功。

## 风险

标准库连接适配不得发展成通用下载或代理框架。对当前不支持的条件失败关闭，实施发现定义不适用时先升级材料。

## 执行记录

- 2026-09-11：仅创建明确路径的修复任务和待审材料，未实施。
- 2026-09-11：状态变更：todo → ready。原因：意图闸passed且用户明确允许实施，精确读写边界和回退明确
- 2026-09-11：状态变更：ready → doing。原因：先完成可控TLS失败复现和安全边界测试，再实现显式连接回退

- 2026-09-11：在签闸与明确实施许可后完成显式连接适配和测试；本地96项基础回归及真实TLS正反例通过，原下载/业务预算保持。本工作独立复核、提交推送与Linux CI仍待单独授权，TASK保持doing，详见VERIFY-055。

- 2026-09-11：用户明确授权本批独立复核、修正后提交推送main及运行处理Linux CI。开始work054_review只读复核，未扩大任务业务/包锁边界。

- 2026-09-11：独立复核发现的P2重定向截断读取已修正并复核通过；修正后97项基础测试通过，准备发布到一次性Linux CI，详细证据见VERIFY-055。
