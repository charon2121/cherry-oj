---
id: "TASK-116"
type: "task"
title: "修复沙箱启动通信被信号中断时的处理"
status: "done"
work: "WORK-052"
owners: ["codex/root"]
depends_on: ["ISSUE-016", "DESIGN-046", "DECISION-030", "PLAN-036"]
related: []
implements: ["ISSUE-016#REQ-001", "ISSUE-016#REQ-002", "ISSUE-016#REQ-003", "ISSUE-016#AC-001", "ISSUE-016#AC-002", "ISSUE-016#AC-003", "ISSUE-016#AC-004"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "development/README.md", "docs/engineering", "development/works/WORK-050", "development/works/WORK-052", "apps/judge-engine", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/install", "deploy/sandbox-linux/tests", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-052", "development/works/WORK-050", "apps/judge-engine/internal/sandbox/launcher/channel_linux.go", "apps/judge-engine/internal/sandbox/launcher/channel_linux_test.go", "apps/judge-engine/internal/sandbox/launcher/files_linux_test.go", "apps/judge-engine/tests/sandbox-linux/boundary/start_linux_test.go", "apps/judge-engine/tests/sandbox-linux/boundary/channel_linux_test.go", "deploy/sandbox-linux/ci/cases.json", "deploy/sandbox-linux/ci/native.py", "deploy/sandbox-linux/ci/memory_watch.py", "deploy/sandbox-linux/ci/memory_watch_test.py"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "apps/judge-engine/internal/sandbox/cgroup", "apps/judge-engine/internal/sandbox/helper", "apps/judge-engine/internal/sandbox/policy", "apps/judge-engine/internal/sandbox/launcher/init_linux_amd64.go", "apps/judge-engine/internal/sandbox/launcher/exec_linux_amd64.go", "deploy/sandbox-linux/install", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/rootfs", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md"]
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# TASK-116：确认并修复控制消息的信号中断

## 任务目标

确定性复现中断后消息/FD处理错误，完成最小修复和原错误/取消/回收回归。

## 依据

ISSUE-016 REQ-001～003、AC-001～004及DESIGN-046/DECISION-030/PLAN-036。

## 可查看范围

以read_paths为准；先读Go工程规范，沿调用链核对描述符与期限。

## 可修改范围

以write_paths为准。边界新增channel测试注入信号、记录事件，并允许仅对已实测可中断的Poll等待恢复：始终使用原两秒绝对截止时间，不重试ReceiveEvent或整个场景。cases.json只增加必要测试名，不减少原63项或预算。

## 禁止修改

以forbidden_paths为准；其余未列入write_paths的生产文件也不可修改，需要越界先重审设计。

## 依赖

用户已审核、签署意图闸并明确继续实施；当前doing，先确认具体中断阶段。

## 产出

旧红新绿、中断处理、消息/FD恰好一次及关闭/期限测试、精确SHA Linux证据和独立复核。

## 完成标准

- [x] 确定性旧红新绿覆盖收发中断，不依赖偶发调度。
- [x] 合法消息/FD一次交付；非法/短写/截断仍失败，FD全部回收。
- [x] 连续中断、取消/关闭和期限有界，不重跑整请求或修改预算。
- [x] 完整工程CI、63内核及10原生在精确SHA通过，无必需skip，清理完整。
- [x] 独立复核有界性及边界通过，技术交回材料已准备；人工验收后才交回WORK-050。

## 验证

本地Go race/vet、Linux必需测试及边界真实信号验证；记录每轮实际结果，不宣称未执行平台支持。

## 风险

描述符关闭后复用、附带FD重复交付与期限延长；无法在现有边界证明正确时暂停并升级方案。

## 执行记录

- 2026-09-11：建立待审材料，未改生产或相关Go测试。
- 2026-09-11：状态变更：todo → ready。原因：用户已签署意图闸并明确继续实施，依赖与读写边界已核验
- 2026-09-11：状态变更：ready → doing。原因：开始确定性中断复现及控制通道最小修复

- 2026-09-11：新增startup poll/receive阶段报错，以及原TestStartupBoundaries内必经的control-signal-observation：独立5秒子进程，待决线程信号配合Ppoll，随后一次消息/FD/EOF观测。尚未运行Linux信号测试，尚未修改channel生产实现。

- 2026-09-11：诊断首轮CI34507325931因夹具Ppoll掩码长度为0而EINVAL，尚未注入信号；仅修正该测试的内核ABI调用并复验，不将此失败当成生产问题。

- 2026-09-11：ebb8b73的CI34507853030九job通过，真实等待阶段EINTR及后续一次消息/FD/EOF已验证，63内核/45必需Go/10原生及完整回收核验通过。生产Recvmsg仍未注入或修复，原始失败来源未追溯确定，保持doing，不能视为旧红新绿或最终验收。

- 2026-09-11：依据前两轮真实等待中断证据，在实现前细化边界：允许测试自身Poll按剩余期限恢复。原“只观察”不足以消除已确认的测试等待脆弱点；该变更不扩大生产文件范围、不改变协议或执行状态机，也不在测试中掩盖ReceiveEvent错误。

- 2026-09-11：生产单次系统调用恢复、FD引用和失败释放候选已实现；参数注入的旧红新绿与Poll绝对期限测试本地race通过，Linux交叉构建通过。新增7个必需Linux Go用例，真实Linux与独立复核待执行，不标done。

- 2026-09-11：新增失败为原生安装驱动OOM，与已通过的控制通道用例不同。在编码前同步DESIGN/PLAN并仅扩展CI观测文件的读写路径，安装器增加只读依据、禁止修改不变；不提高128MiB上限、不重跑请求、不把后续通过覆盖本轮失败。用户已授权本批CI运行及问题处理。

- 2026-09-11：b3e5ec0的CI34557663857内核63/必需Go52通过，两种真实信号marker、1000次/并发/故障及清理复验通过；独立复核生产修复无阻断。原生安装1FAIL/9NOT_RUN，保持doing。观测批41a5396已授权发布；观测复核发现截断回执阻止原诊断的P2，修正和反例复核后关闭。

- 2026-09-11：用户确认仅安装驱动256MiB后，先同步DESIGN/PLAN例外；在已有native.py和memory_watch_test.py路径内实施，非install驱动及原生服务/编译/用户程序限额不变。后续超限不自动再上调。

- 2026-09-11：用户批准的256MiB版本b699604在34559188597和34559407284两台新VM现有九job全过，63内核/52必需Go/10原生、1000次/并发/故障与全空清理下载复验通过。无独立复核阻断，技术完成；人工验收与交回分开记录，不能以此启动尚依赖WORK-052验收的TASK-112。
- 2026-09-11：状态变更：doing → done。原因：b699604两台新VM完整九job通过，63内核/52必需Go/10原生与全空清理复验通过；独立复核无阻断，安装驱动256MiB例外获用户批准，人工验收交回仍待闸
