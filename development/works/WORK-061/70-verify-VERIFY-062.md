---
id: "VERIFY-062"
type: "verify"
title: "judge 进程内调用 C 执行器并移除 sandbox HTTP 服务"
status: "review"
work: "WORK-061"
owners: ["team/judge-engine"]
depends_on: []
related: []
implements: []
verifies: ["WORK-061", "WORK-061#AC-001", "WORK-061#AC-002", "WORK-061#AC-003", "WORK-061#AC-004", "WORK-061#AC-005"]
tags: []
result: "pass"
created_at: "2026-09-29"
updated_at: "2026-09-30"
format: "compact"
---

# VERIFY-062：judge 进程内调用 C 执行器并移除 sandbox HTTP 服务

<!-- 前四节是给人的交付视图，具体命令、环境与输出只写最后一节。
verifies 必须锚定主文档的 AC 条目；没运行的检查必须如实说明。 -->

## 实际结果

- 节点上只剩一个服务单元 `cherry-sandbox-judge.service`（外加 slice）。judge 在本进程内装配执行层
  （`execution.Engine`），每次执行 exec 一次 setuid 执行器；节点能安装、启动、注册并判题。
- 仓库里已没有 sandbox HTTP 服务：`cmd/sandbox`、`execution/api`、`judge/sandboxclient`、
  `sandbox.example.yaml`、`contracts/run.schema.json` 均已删除；judge 配置不再有 sandboxMode/
  sandboxURL/sandboxTimeout，执行层配置在顶层 `execution` 段。
- 本地 Compose 只启动一个 judge 容器（devhost 后端），容器冒烟判 A+B 为 AC。
- 执行层回收未确认（例如执行器被杀）时，judge 以失败退出、停止心跳，重启后恢复。

## 承诺差异

- 必需回归由 93 项变为 91 项：`native.kill-sandbox` 与 `kernel.handler-saturation` 随 sandbox 服务一起
  消失（被测对象不存在了）。其余能力都有对应用例，其中显式零限额、零输出预算、队列饱和改为执行层单元
  测试（在真实 Linux 上作为必跑 Go 测试执行），错误可执行格式改由 extended.py 直接驱动执行器。
- `inlineThresholdBytes` 未按原方案删除：它在进程内仍有意义，删除会改动判题编排的输入传递。
- 原方案「12 项上界」有误，实际为 16 项（4 个受管组 × 4）。
- 根目录 README.md 的 Compose 说明未更新（不在可写范围），待确认。

## 验证情况

最终候选的完整 CI 全部通过：91 项必需回归（basic 5、kernel 62、native 9、business 15），外加 Go、
容器冒烟、契约、文档与执行器独立 CI。执行器自身的 22 个真实内核测试在内核套件中执行。
没有在真实生产机器上安装与自测；机器重启后的恢复未验证（项目没有已部署节点）。

## 遗留问题

- 根目录 README.md 需要随 Compose 变化更新：`docker compose logs -f judge sandbox` 已失效，
  `SANDBOX_*` 变量说明需改为执行层含义。需要确认是否扩大本工作范围。
- `tracing.Transport` 目前没有出站调用方，judge 调控制面的客户端未接入 trace 传播（合并前也未接入）。
- 执行层错误消息与日志事件名仍沿用 `sandbox` 前缀（如 `sandbox queue is full`、`sandbox.pool.stopped`），
  改名会影响日志检索，未在本工作中处理。
- 两次提交（4b1f1fb、3687486）误带下一步的改名与删除，各自 CI 失败，已由后续提交补齐；main 历史上
  这两个提交单独检出时不可用。

## 检查与结果

| 提交 | 内容 | CI | 结果 |
|---|---|---|---|
| ed46675 | sandbox/ → execution/ 改名 | 36661514571 | 通过 |
| 780fda0 | judge 可在进程内装配执行层 | 36662009467 | 通过 |
| 4b1f1fb | Compose 只运行 judge（误带改名与单元删除） | 36662498388 | 失败，由 dcf92f6 补齐 |
| dcf92f6 | 原生部署只保留 judge | 36662995285 | 原生套件上界数写死为 20 而失败，其余通过 |
| 3687486 | 上界数从布局推出（误带脚本改名） | 36663441646 | 内核失败，由 1843be1 补齐 |
| 1843be1 | 内核套件改由 judge 驱动 | 36663903629 | 全部通过 |
| b3d007e | 删除 sandbox 服务、客户端与 run 契约 | 36664475189 | 全部通过，91/91 |

AC 证据（均见 CI 36664475189 及其报告）：

- AC-001：native.install、native.native（verify-native 只见 judge 单元，经 /judge 判题）；S3 删除清单见
  提交 b3d007e。
- AC-002：native.caps（8 项逐项删除时 judge 在执行层启动冒烟失败）；native.native 核对 judge 线程
  Uid=61010、无有效能力、CapBnd 恰为 8 项。
- AC-003：kernel 62 项（执行器真实内核测试、隔离与计量、judge 整链、init/执行器/judge 强杀与停止后恢复）、
  native 9 项、business 15 项全部通过。
- AC-004：必跑 Go 测试 `TestOnlyJudgeAssemblyImportsExecution`（反例已在本地验证会失败）、
  `TestStoppedExecutionEndsServe`；kernel.executor-kill 验证 judge 在执行层停止接单后退出。
- AC-005：容器冒烟（judge 单容器判 A+B 为 AC）；`python3 scripts/docs_test.py` 585 份文档链接有效。

## 变更记录

- 2026-09-30：状态变更：draft → review。原因：最终候选 CI 36664475189 全部通过（91/91），文档提交 711c40c 的 CI 36664973866 通过
