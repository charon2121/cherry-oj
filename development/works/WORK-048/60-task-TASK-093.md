---
id: "TASK-093"
type: "task"
title: "Linux 能力探测与实施边界冻结"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-004", "DESIGN-042", "DECISION-026", "PLAN-032"]
related: []
implements: ["IMPROVEMENT-004#REQ-001", "IMPROVEMENT-004#REQ-002", "IMPROVEMENT-004#REQ-003", "IMPROVEMENT-004#REQ-004", "IMPROVEMENT-004#REQ-005"]
verifies: []
tags: []
read_paths: ["dev-dependency/go-sandbox", "CLAUDE.md", "docs", "apps/judge-engine", "contracts", "compose.yaml", "development/works/WORK-044", "development/works/WORK-047", "development/works/WORK-048"]
write_paths: ["development/works/WORK-048", "deploy/sandbox-linux"]
forbidden_paths: ["apps/server", "apps/web", "apps/judge-engine", "contracts"]
created_at: "2026-09-09"
updated_at: "2026-09-09"
---

# TASK-093：Linux 能力探测与实施边界冻结

## 任务目标

只读探测目标环境，冻结资源、启动、权限和部署支持矩阵，补齐后续具体实施任务；本 TASK 不实现特权后端或部署服务。

## 依据

IMPROVEMENT-004 REQ-001～005、DESIGN-042、DECISION-026、PLAN-032。

## 可查看范围

以 read_paths 为准；服务器接入后仅检查版本、架构、内核、cgroup、namespace/seccomp、systemd/权限、资源和当前服务，无秘密输出。

## 可修改范围

本工作文档与 deploy/sandbox-linux 下的只读能力探针/说明。远程文件、系统配置、账号、服务与业务代码均不在本任务写范围。

## 禁止修改

以 forbidden_paths 为准；不得修改全局 sysctl、停用 AppArmor、防火墙或现有业务；禁止部署、重启用户进程和执行资源耗尽实验。

## 依赖

意图闸签署及实施授权后执行；远端探测另需用户提供连接信息、目标用途及可用权限。服务器缺席不阻止本地文档准备，但阻止宣称远程验收完成。

## 产出

只读探针、支持矩阵、启动链/清理链设计、后续分项 TASK 精确代码边界。

## 完成标准

- [x] 明确 Ubuntu 版本、架构、部署形态、权限及服务器是否专用。
- [x] 核实 cpu/memory/pids、memory.peak/cgroup.kill、namespace/seccomp 和委派范围。
- [x] 完成本地 go-sandbox 参考取舍记录并建立跨发行版/内核/架构测试清单。
- [x] 固定 helper/成熟执行器选择、入组机制、权限降级、文件访问和崩溃恢复方案。
- [x] 后续每个实施任务都有依赖、精确路径和对应验收场景。

## 验证

探针不得写系统状态；缺权限或能力时返回明确结果；记录原始版本及脱敏输出，不索取粘贴密码。

## 风险

当前首站为 Ubuntu 24.04.4 / 6.8 / x86_64，用户已确认专用于本项目测试并保留系统及云厂商代理。root 连接权限不等于服务已获得委派，更不等于实际隔离通过。

## 执行记录

2026-09-09：设计回合，仅写文档，尚未实施。

- 用户要求参考本地 go-sandbox 且后续跨 Linux 服务器测试；增加只读参考路径，不复制实现、不修改依赖目录。当前仍是文档回合。

- 2026-09-09：已只读审读本地 go-sandbox 的 v2 cgroup、forkexec、container 生命周期与许可；证据和取舍写入 DESIGN-042。后续机器矩阵待接入信息，未运行参考库或实施探针。
- 2026-09-09：状态变更：todo → ready。原因：用户已签署意图闸并明确允许执行；仅只读探测和边界冻结，路径范围已明确
- 2026-09-09：状态变更：ready → doing。原因：开始编写可复现只读探针并核实服务器能力及后续任务边界
- 2026-09-09：状态变更：doing → done。原因：只读探针已在首站执行；用户确认专用；能力和待验证矩阵、启动清理规格及 TASK-094至100 精确边界已完成；未实施隔离后端

## 本任务交付（2026-09-09）

- 意图闸已由用户签署，随后明确允许执行；TASK-093 经 ready 进入 doing。
- 新增 deploy/sandbox-linux/probe.sh 与 README，只做只读盘点；目标机实测摘录和跨 Linux 矩阵进入 VERIFY-049。
- 服务器当前版本、权限、资源和共存服务已核实；用户确认专用于本项目测试并保留系统/云厂商代理。
- 当前 SSH scope 无委派、本项目 helper 单元未安装；未来委派树与创建/读回行为在设计和 TASK-098 中明确，不把只读盘点充当写权限证明。
- DESIGN-042 完成 helper/成熟执行器比较，当时提出 Go helper + C launcher；用户随后选择纯 Go re-exec，现行路线见 DESIGN-042 修订。无 userns、clone3 入组、文件句柄交付、权限和回收要求保留。
- TASK-094～100 已逐项生成依赖、read/write/forbidden_paths 和完成条件；后续实施未授权部分保持 todo。
- 本任务完成标准中的“核实”限于只读接口/配置事实和实施规格；真实 namespace/seccomp/限额/清理行为仍未执行，由后续测试任务证明。
