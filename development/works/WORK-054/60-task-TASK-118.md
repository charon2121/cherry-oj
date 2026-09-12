---
id: "TASK-118"
type: "task"
title: "为CI软件包下载增加有界同源地址回退"
status: "blocked"
work: "WORK-054"
owners: ["codex/root"]
depends_on: ["ISSUE-018", "DESIGN-048", "DECISION-032", "PLAN-038"]
related: []
implements: ["ISSUE-018#REQ-001", "ISSUE-018#REQ-002", "ISSUE-018#REQ-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-050", "development/works/WORK-054", "docs/engineering", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-054", "development/works/WORK-050", "deploy/sandbox-linux/rootfs/download.py", "deploy/sandbox-linux/rootfs/download_test.py", "deploy/sandbox-linux/rootfs/diagnostics.py", "deploy/sandbox-linux/rootfs/diagnostics_test.py", "deploy/sandbox-linux/rootfs/transport.py", "deploy/sandbox-linux/rootfs/transport_test.py", "deploy/sandbox-linux/ci/prepare.py", "deploy/sandbox-linux/ci/prepare_test.py", "deploy/sandbox-linux/ci/README.md"]
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

本工作意图闸passed且用户明确允许实施；本轮CI准备未全面恢复，现等待重审。用户已单独授权本批独立复核、commit/push main及远端CI。

## 产出

显式CLI选项、有限HTTPS连接适配、正反例与CI准备接线、按实际peer与锁定摘要记录的证据。

## 完成标准

- [x] 默认行为保持；确定性首地址TLS网络失败后下一地址成功形成旧红新绿。
- [x] 证书问题立即拒绝，HTTP或响应体失败不重放，大小/路径/摘要原检查通过。
- [x] DNS地址去重和最多8次、共同30秒、单次5秒及socket关闭受测；原外层240秒预算已经验证；当前按用户批准方案1调整为600秒进行试验。
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

- 2026-09-11：已授权提交68c91d4并推送，CI34580313653为8PASS/2FAIL；native56包全量验证及10项通过，kernel/business仍在下载240秒期限终止。记录新失败，不扩大预算或HTTP重试，按PLAN重审条件等待后续诊断方案；详见VERIFY-055。
- 2026-09-11：状态变更：doing → blocked。原因：原生全量通过但两项下载耗尽240秒，按PLAN重审条件待审核有界逐包诊断，不扩大预算/策略；见VERIFY-055
- 2026-09-11：状态变更：blocked → doing。原因：开始已批准诊断实现、受控阶段/并发/有界输出测试及本批Linux证据验证
- 2026-09-11：状态变更：doing → blocked。原因：有界诊断已定位唯一慢GCC正文请求，后续处理将涉及慢响应策略取舍，按PLAN待另审；本轮不扩HTTP重试/240秒预算
- 2026-09-11：状态变更：blocked → doing。原因：用户已审核并批准方案1：仅CI下载240至600秒试验；上游有限预算例外及两轮冷下载验证已先记录，现恢复实施
- 2026-09-11：状态变更：doing → blocked。原因：方案1两轮6VM有2次GCC正文耗尽600秒，单独延长等待不足；当前授权试验已完成，等待其他处理策略重审，见VERIFY-055

## 已批准诊断实施边界（2026-09-11）

用户批准上轮VERIFY-055的有界逐包诊断提案，恢复本任务。新增rootfs/diagnostics.py和diagnostics_test.py写路径用于隔离诊断状态/输出预算及正反例；download.py负责插入阶段，transport.py关联请求序号，保持默认路径/来源/包锁/TLS/HTTP不重放/并发和预算。验收证据包含真实逐包阶段及失败事实；本任务原独立复核与提交推送/处理CI授权持续适用，人工验收不代签。

- 2026-09-11：已批准逐包诊断完成；独立复核发现的混合输出交错已修正，正负回归证实有效。最终104基础测试通过，按本工作已有授权提交推送并执行一次Linux诊断；原8PASS/2FAIL不删除，完整恢复仍待证据。

- 2026-09-11：诊断d001f03已推送并完成CI34582727482：104基础及kernel63项通过，native唯一GCC包正文未完成被240秒终止；business下载/构建/部署/校准通过后io失败，取得support.ts:45:24并回收干净。本轮诊断目标完成，完整下载恢复仍待慢响应处理方案重审；不扩大HTTP重试或预算。

## 用户批准的方案1预算调整（2026-09-11）

用户阅读响应慢方案比较后明确要求“先按照方案1进行调整，再尝试”。本次仅将ci/prepare.py的下载命令总期限240秒调整为600秒，作为CI冷下载试验；这是对上文“保持240秒/不增加总预算”的明确、有限例外。连接/TLS共享30秒、每地址5秒、读取30秒、4并发、包大小/SHA256、诊断上限和HTTP不重放保持。未引入缓存、共享包、镜像源或慢响应重试，也不修改正式沙箱及编译运行限额。回退为仅恢复该命令240秒及对应测试期望。

本批实际代码写入仅ci/prepare.py、ci/prepare_test.py、ci/README.md，均在既有write_paths中；上游授权例外先记录再改代码，不扩大forbidden_paths。

- 2026-09-11：方案1提交d795c7f已推送；两轮34584984978/34586094583完成，6台VM冷下载4成功、2同GCC包耗尽600秒。方案1单独不足且无240至600秒间成功样本，不再增预算或追加重跑，等待其他策略重审。另保留首轮内核proc采样失败和两轮既有io失败；证据见VERIFY-055。
