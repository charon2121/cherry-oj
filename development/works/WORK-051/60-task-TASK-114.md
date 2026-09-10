---
id: "TASK-114"
type: "task"
title: "修复沙箱连续请求完成与容量归还的竞态"
status: "done"
work: "WORK-051"
owners: ["codex/root"]
depends_on: ["ISSUE-015", "DESIGN-045", "DECISION-029", "PLAN-035"]
related: []
implements: ["ISSUE-015#REQ-001", "ISSUE-015#REQ-002", "ISSUE-015#REQ-003", "ISSUE-015#REQ-004", "ISSUE-015#AC-001", "ISSUE-015#AC-002", "ISSUE-015#AC-003", "ISSUE-015#AC-004"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "development/README.md", "docs/engineering", "development/works/WORK-050", "development/works/WORK-051", "apps/judge-engine", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-051", "development/works/WORK-050", "apps/judge-engine/internal/sandbox/helper/client.go", "apps/judge-engine/internal/sandbox/helper/server_linux_amd64.go", "apps/judge-engine/internal/sandbox/helper/client_test.go", "apps/judge-engine/internal/sandbox/helper/server_linux_amd64_test.go", "apps/judge-engine/internal/sandbox/helper/README.md", "deploy/sandbox-linux/ci/cases.json", "deploy/sandbox-linux/tests/client.py", "deploy/sandbox-linux/tests/smoke.py"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "apps/judge-engine/internal/sandbox/cgroup", "apps/judge-engine/internal/sandbox/launcher", "apps/judge-engine/internal/sandbox/policy", "apps/judge-engine/internal/sandbox/helper/execute_linux_amd64.go", "apps/judge-engine/internal/sandbox/helper/execution.go", "apps/judge-engine/internal/sandbox/helper/trust_linux.go", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md"]
created_at: "2026-09-10"
updated_at: "2026-09-11"
---

# TASK-114：确认并修复helper请求完成与容量归还顺序

## 任务目标

以确定性测试确认并修复合法连续请求偶发被拒绝的问题，保持原忙碌拒绝与完整收尾边界。

## 依据

ISSUE-015 REQ-001～004、AC-001～004与DESIGN-045/DECISION-029/PLAN-035。

## 可查看范围

以front matter read_paths为准；先读go.md，源码仅用于已定义生命周期问题。

## 可修改范围

以write_paths为准。新server_linux_amd64_test.go仅用于真实Unix连接与可控制调度的回归；现有测试只适配协议收尾，不能删除断言。ci/cases.json增加必需Go测试名。

## 禁止修改

以forbidden_paths为准。不得碰执行、权限、cgroup、部署、业务和外部契约；需要新增生产文件先更新边界说明。

## 依赖

本工作意图闸由用户签署且后续明确允许实施；意图闸已通过，用户后续已明确允许实施。WORK-050已完成测试资产可只读使用，不依赖其全部验收以免形成环。

## 产出

确定性失败用例、最小生命周期修复、取消及传输/容量正反例、对应本机协议说明；精确提交的完整CI与Linux回收证据。

## 完成标准

- [x] 旧完成/slot顺序在可控调度下失败，修复后通过。
- [x] 正常EOF前客户端不返回成功，取消/超时/缺尾帧/垃圾尾部/连接reset拒绝成功。
- [x] 真正满槽仍拒绝，慢客户端不累积无界连接，资源/身份回收先于复用。
- [x] 完整Go/race和Linux63项（含1000次/并发/故障/容量/清理）在对应SHA通过；TASK-115已完成旧语言功能期限修复，e44f9b4与最新a0e1b35现有8项CI全绿，见VERIFY-052。
- [x] 独立复核并记录影响、身份与回退，无现有环境变更；2026-09-11只读复核无本次引入的阻断发现，两项测试证据限制见VERIFY-052。

## 验证

本地go test -race -count=1 ./internal/sandbox/helper及完整Go回归；Linux首站沿WORK-050 ci.yml，无额外服务器连接。保留真实失败，不把交叉编译算实机。

## 风险

如果最小测试证伪时序归因或需要改执行器，停下更新设计；不能扩大修改来凑绿色。

## 执行记录

- 2026-09-10：由WORK-050的真实失败建立修复材料，未实施。
- 2026-09-10：状态变更：todo → ready。原因：用户已签署WORK-051意图闸并明确允许实施，精确读写边界已核验
- 2026-09-10：状态变更：ready → doing。原因：先建立完成确认与容量归还的确定性回归，再做有界收尾修复
- 2026-09-10：在读取前补入 deploy/sandbox-linux/tests，只读核对 CI 使用的私有协议消费者是否同样需要等待 EOF；不因此授权修改测试驱动或放宽断言。
- 2026-09-10：按 DESIGN-045/PLAN-035 的兼容核对，在修改前精确授权 tests/client.py、tests/smoke.py 增加 EOF 断言；保持已有期限与全部断言。
- 2026-09-10：最小收尾修复及本地完整 Go race/vet/基础测试通过，Linux 测试已编译但未实跑；详见 VERIFY-052，任务保持 doing。
- 2026-09-10：用户明确授权本次修复及关联工作记录 commit + push 到 origin/main，并运行一次性 GitHub Linux CI；独立复核委派授权仍待回复。
- 2026-09-10：946e528 已推送，CI 34470867753 的 8 job 与内核 63 项全绿，下载后再次核验日志及完整回收。独立复核委派尚未授权，保持 doing。
- 2026-09-10：依 DESIGN-045/PLAN-035，在写入前补入 WORK-050 文档路径，移交旧 Java 语言测试失败；未增加语言测试、工作流或 host 源码写权限。
- 2026-09-10：最新文档提交 b03e6bd 整体 CI 7/8，Java 语言功能测试在 5 秒失败；Linux 63 项再次全过。形成 WORK-050/TASK-115 待审测试期限与诊断提案，没有修改额外源码或重跑覆盖。

- 2026-09-10：接收TASK-115完成后的全绿证据，确认helper相关文件与946e528相同；准备具体只读独立复核材料。用户明确先完成全部CI再整体重构，TASK-114仍等待独立复核，不提前解除TASK-110依赖。
- 2026-09-11：用户明确授权独立只读复核，Zeno完成审查；实施者核对源码并记录两项非阻断测试覆盖限制。技术完成，人工验收仍待用户按PLAN-035签署；本次未改源码或重跑Linux。
- 2026-09-11：状态变更：doing → done。原因：确定性旧红新绿、当前完整CI和Linux回收通过，独立源码复核完成；技术完成不代替人工验收
