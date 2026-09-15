---
id: "TASK-107"
type: "task"
title: "复核执行顺序可读性并记录行为回归证据"
status: "done"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-106"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-002", "CHANGE-013#REQ-003", "CHANGE-013#REQ-004", "CHANGE-013#REQ-005", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007", "CHANGE-013#AC-001", "CHANGE-013#AC-002", "CHANGE-013#AC-003", "CHANGE-013#AC-004", "CHANGE-013#AC-005", "CHANGE-013#AC-006"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "docs/coding-standards", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-050", ".github/workflows/ci.yml"]
write_paths: ["development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048", ".github", "development/works/WORK-050"]
created_at: "2026-09-10"
updated_at: "2026-09-14"
---

# TASK-107：复核执行顺序可读性并记录行为回归证据

## 任务目标

独立核对源码阅读路线与等价行为，交付可供人工验收的记录及候选规范处置清单。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的对象、进程、生命周期与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

TASK-105、TASK-106 完成并提供可比较 diff；独立复核方式在执行前落实。
2026-09-13 已核对 WORK-049 意图闸及 WORK-050 验收；不再等待相同签署。本轮只建立计划，后续按依赖与实际授权将任务推进到 ready 再执行。

## 产出

VERIFY-050 的逐 AC 证据与实际命令/环境/结果；MEMORY-036 的已证实教训；DESIGN-043 候选规则的适用性与未决项。只修改本工作文档。

## 完成标准

- [x] 未参与相应实现的审查者实走正常执行、超时和取消路径，记录卡点及修复后的复核。
- [x] 六项 AC 分别记录证据；测试通过不替代阅读判断。
- [x] 本机与 Linux 结果区分执行/未执行/跳过/失败，不能用交叉编译替代内核验证。
- [x] 数值、协议、权限、配置及工作区边界完成比对，回退只覆盖本工作。
- [x] 候选规则逐项列建议保留/调整/不提升及理由，人工确认待签；不修改全局规范。
- [x] WORK-050 交付的同一必需清单在重构最终提交全部运行通过，包含真实内核、原生部署、真实业务及清理；报告SHA对应本次候选，不复用基线成功记录。

## 验证

按 PLAN-033 验证矩阵执行。发现实现问题交回 TASK-105/106 按原边界处理，本任务不越界直接修改代码。scripts/work check 检查文档；验收闸仅由用户签署。

## 风险

同一实现者只按自身理解复述代码，可能掩盖新读者仍看不懂；阅读结果必须包含具体符号、卡点与解释。环境不可用时保持部分未验证，不作完成承诺。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
- 2026-09-14：状态变更：todo → ready。原因：TASK-105/106 已完成本地实施，进入 R8 交接；待落实独立审查者和最终 Linux CI
- 2026-09-14：状态变更：ready → doing。原因：用户明确要求先完成 R8；独立源码审查已交由未参与实现的审查者，开始准备候选 Linux 回归
- 2026-09-14：状态变更：doing → done。原因：R8 独立阅读七问和四条路径通过；候选 a611be3 的 Linux CI 34830811953/1 全部 93 项及必需 job、52 个固定 Go 测试和清理证据通过；候选规则逐项记录

## 2026-09-13 批次与边界细化

旧版 B8 由当前 PLAN-033 的 R8 承接。先独立走通正常/超时/取消源码路线，再核对候选自身 SHA 的完整必需汇总；前面的局部测试不能替代这两项。
候选报告必须同 sourceSha/harnessSha/runId/runAttempt，保留 93 项及 requiredGoTests 的断言语义。测试文件必要适配导致 harness 变化时单列比对，不假装摘要未变。
本轮仅计划，不新建运行或远端发布；后续按已有用户授权确定 PR/push/dispatch 入口，未获得的动作不执行，未执行证据不作 PASS。


## 新模型的独立验收（2026-09-14）

除原六项 AC 外，按 PLAN 的七个结构问题记录具体源码和结论：类型与主入口可见职责；P3/P4/P5 隔离动作可定位；service 不直接操作执行 FD/任务通道；资源唯一 owner 与移交后行为明确；部分启动/超时/取消/OOM/清理失败保持原结果；本地与远端完成不同；execve 不被误解为新建进程。

结构验收与行为回归分别记录。只有新名字、更多方法或更少单文件行数不能计为结构通过；仍要靠实现者口头补充调用顺序才能读懂时，记录卡点并交回 TASK-105/106。

计划阶段未启动审查代理；2026-09-14 用户明确要求完成 R8 后，已交由未参与实现的独立审查者执行，TASK 已进入 doing。Linux 不可用、报告非自身候选 SHA 或缺少必需检查时保留未验证，不签验收闸。


### 当前交接

R0～R7 本地实施、最终模块检查及精确增量已记录在 VERIFY-050。用户明确要求完成 R8 后，独立审查者已完成七问与四条路径，结论通过；完整 Linux/kernel/native/business 候选 CI 93/93 项及全部必需 job 已通过，下载证据复验通过。为使既有 workflow 绑定实际源码，使用独立临时 clone 的 codex/work-049-r8-3w1rszph 分支，候选为 a611be3d427fdfa03b724df0c1235148f897f648，仅提交 apps/judge-engine，保留原 main 工作区及其他工作的既有改动。人工验收闸不变。

- 2026-09-14：R8 独立七问与四条路径通过；最终候选 a611be3 的运行 34830811953/1 完整通过且四套清理确认，52 个固定 Go 测试无失败/跳过；11 项候选规则已逐项记录。技术完成，不签人工验收闸。
