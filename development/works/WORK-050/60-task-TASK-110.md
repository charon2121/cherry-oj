---
id: "TASK-110"
type: "task"
title: "编排真实Linux隔离资源与完整回收回归"
status: "done"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-109", "TASK-114"]
related: []
implements: ["CAPABILITY-008#REQ-001", "CAPABILITY-008#REQ-002", "CAPABILITY-008#REQ-005", "CAPABILITY-008#REQ-006", "CAPABILITY-008#AC-002"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "development/works/WORK-050", "docs/engineering", ".github/workflows", "apps/judge-engine", "deploy/sandbox-linux", "contracts", "development/works/WORK-051"]
write_paths: ["development/works/WORK-050", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests", "apps/judge-engine/tests/sandbox-linux", ".github/workflows/ci.yml", "development/works/WORK-051"]
forbidden_paths: ["apps/judge-engine/internal", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "contracts", "compose.yaml", "compose.legacy.yaml", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md", "apps/server", "apps/web"]
created_at: "2026-09-10"
updated_at: "2026-09-11"
---

# TASK-110：编排真实Linux隔离资源与完整回收回归

## 任务目标

当前源码在一次性Linux VM完整运行既有边界、权限、资源、1000次、并发和崩溃套件。

## 依据

CAPABILITY-008 REQ-001/002/005/006与AC-002，TASK-109清单，DESIGN-044安全与计量规则。

## 可查看范围

以 front matter 的 read_paths 为准；已有实现只读用于核对不变量。

## 可修改范围

以 write_paths 为准。仅测试编排、工作流、报告和用例适配，不实现另一套沙箱或业务服务。远端动作仅指审核后的一次性 GitHub VM，不能连接现有服务器或 IDEA。

## 禁止修改

以 forbidden_paths 为准。禁止修改生产代码、协议、限额/权限来迁就测试；禁止使用旧私有配置、数据和凭据；不得清理未知所有权资源。发现真实缺陷先记录并另拆修复边界。

## 依赖

依 depends_on 顺序推进；本轮只是文档，需人工意图闸及后续实施授权，当前 todo 不可直接执行。

## 产出

准备/运行/证据/清理驱动；固定旧脚本的参数化适配；内核job与按当前SHA构建的二进制、静态rootfs、C++锁定rootfs。生产包不可修改，Go边界夹具仅适配环境/报告。

## 完成标准

- [x] 实测systemd、clone3入组、namespace/seccomp、cgroup控制器和peak/kill接口可用；能力缺失明确失败。
- [x] 边界各握手及exec失败阶段/errno、链接和特殊文件及1000次路径交换全部执行，无必需skip。
- [x] C++编译/运行隔离及全部线程权限、宿主/网络/任务间访问拒绝通过。
- [x] 单/多进程CPU、独立峰值、OOM/swap/pids、墙钟、普通SIGKILL、OLE后空程序、排队/handler/聚合OOM归因通过。
- [x] 完整1000次与双并发，取消和init/helper/HTTP崩溃恢复均通过；同PID FD与进程/挂载/cgroup/文件快照完整，无残留。
- [x] 原预算和断言保留；基于所有权的finally/always与超时托底生效，失败也有可归属报告。

## 验证

无特权部分本地单测；真实job使用ubuntu-24.04/amd64宿主，一批一个独占目录，不与原生部署同VM并行。每case和每batch保留原期限，记实际内核与LSM；全量test-json中必需测试不得skip，资源错误不能映射成用户超限。

## 风险

旧脚本存在固定UID/端口/前缀；只能在空白一次性环境运行，未知冲突拒绝。VM内核不同不能假定支持，也不能关闭LSM让测试变绿。

## 执行记录

- 2026-09-10：已完成入口盘点，未触发GitHub或远端运行。
- 2026-09-10：状态变更：todo → ready。原因：TASK-109已完成，开始按已批准边界编排真实Linux回归
- 2026-09-10：状态变更：ready → doing。原因：实现一次性VM准备、受限执行、逐项证据和所有权回收

- 2026-09-10：已实现prepare/kernel、受限命令执行、运行所有权、完成记录校验与sandbox-kernel job；原手工脚本只增加可选单元名及零限额日志，未改断言。清单冻结38个Linux专属包测试名，专用套件拒绝所有skip。
- 本地无特权校验：basic入口共39项单测通过（install15/rootfs6/CI18）；actionlint1.7.12通过；linux/amd64边界测试二进制交叉构建成功，不能计为运行验证。
- 保持doing：尚未发布这批CI，未取得一次性GitHub VM上的真实内核、1000次、故障和清理证据；依PLAN-034逐阶段验证后再进入TASK-111。

- 2026-09-10：依PLAN-034增加WORK-051文档读写范围，仅整理已复现连续请求异常的独立修复材料；原生产Go禁止边界保留，不授权修复实施。
- 2026-09-10：状态变更：doing → blocked。原因：两轮连续请求reset且helper存活；需要WORK-051独立修复，当前任务禁止改生产Go
- 2026-09-11：TASK-114已完成技术修复、现有8项CI及Linux63项/45必需Go测试验证和独立复核，证据见VERIFY-052。按PLAN-035第5步仍等待WORK-051人工验收后交回；保持blocked，不把技术完成或委派复核授权视为验收签署。通过后据已有实跑证据逐项收束本任务，再开始TASK-111。
- 2026-09-11：状态变更：blocked → doing。原因：WORK-051人工验收已核验通过，解除阻塞，按已有完整Linux证据逐项收束

- 2026-09-11：已核验WORK-051验收闸passed并refresh为verified。完成标准对应证据为VERIFY-052的CI34470867753、34478647561及最新34479485875：Linux63项、45必需Go无skip、1000次/双并发/故障/容量和空资源快照均通过；原预算保留。TASK-110技术完成，WORK-050整体仍等待部署、业务与汇总。
- 2026-09-11：状态变更：doing → done。原因：WORK-051人工验收通过，63项Linux及45必需Go、连续1000次并发故障清理证据完整，原预算保留
