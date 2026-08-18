---
id: "TASK-0004"
title: "在隔离环境中显式配置并验证工具链 PATH"
type: "tech-debt"
area: "sandbox/container"
priority: "P2"
status: "todo"
assignee: null
depends_on: []
related: ["TASK-0003"]
created_at: "2026-08-18T19:10:35+08:00"
updated_at: "2026-08-18T19:10:35+08:00"
claim_branch: null
claimed_at: null
lease_until: null
completed_at: null
review_required: true
---

# TASK-0004：在隔离环境中显式配置并验证工具链 PATH

## 背景

语言配置使用 `g++`、`python3`、`javac`、`sh` 等裸命令。host 版继承宿主进程 PATH，
但 namespace/chroot 实现会重新构造环境；PATH 缺失会导致所有提交以 SE 失败。

## 目标

隔离环境显式构造 PATH，并在启动或部署自检中验证启用语言所需命令确实可解析。

## 范围

包含：

- 明确 PATH 的配置来源和默认值。
- 隔离环境只使用显式传入的环境变量。
- 在真实容器路径中验证 C++、Python、Java 等已启用工具链。

不包含：

- 把解释器路径硬编码为某台机器上的绝对路径。
- 自动下载或安装缺失工具链。

## 验收标准

- [ ] 隔离环境的 PATH 不依赖启动 judge/sandbox 的 shell 环境。
- [ ] 缺少必需命令时启动自检给出具体语言和命令名称。
- [ ] `languages_e2e_test.go` 的关键路径在真实隔离环境内运行。
- [ ] Docker Compose 联调继续通过。

## 执行记录

- 2026-08-18：从本地 `docs/backlog.md` 迁入任务中心。

## 阻塞信息

无。

## 完成结果

尚未完成。

