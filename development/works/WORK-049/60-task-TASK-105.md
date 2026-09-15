---
id: "TASK-105"
type: "task"
title: "重构 sandbox 命令执行主线与本机协议表达"
status: "done"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-104", "TASK-113"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-002", "CHANGE-013#REQ-003", "CHANGE-013#REQ-004", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "docs/coding-standards", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-050", ".github/workflows/ci.yml"]
write_paths: ["apps/judge-engine/internal/sandbox", "apps/judge-engine/cmd/sandbox", "apps/judge-engine/cmd/sandbox-helper", "apps/judge-engine/tests/sandbox-linux", "apps/judge-engine/README.md", "development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "docs/coding-standards", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048", ".github", "development/works/WORK-050"]
created_at: "2026-09-10"
updated_at: "2026-09-14"
---

# TASK-105：重构 sandbox 命令执行主线与本机协议表达

## 任务目标

实现 DESIGN-043 的 service / execution / isolationPlan / isolatedProcess / 执行产物所有权模型；让读者沿 handleRun 跟到用户 exec 并返回，并能从类型与主入口判断合法状态和最终回收条件。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的对象、进程、生命周期与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

用户要求先固化现有已验收测试；新增依赖 TASK-113 的自动基线交付。此前只能进行不改源码的阅读准备，不能以旧 WORK-048 手工报告替代本次 CI 基线。

TASK-104 的基线、映射与交接完成；任何扩大到 contracts/部署/权限模型的需要先升级定义与计划。
2026-09-13 已核对 WORK-049 意图闸及 WORK-050 验收；用户随后明确开始第一轮，按依赖推进任务并执行，不再等待相同签署。

## 产出

sandbox 与相关 cmd 的结构调整、必要的协议/生命周期回归测试、包内 README 阅读入口；更新本工作符号映射与数值比对证据。

## 完成标准

以下检查对应新计划 R0～R5，本地实施与检查已完成，完整 Linux 与独立复核移交 TASK-107；下方 B1～B4 的历史勾选不代替这些标准。

- [x] R0 逐项完成旧字段/方法到新 owner 的映射，资源转移、失败注入与既有测试无遗漏。
- [x] isolationPlan 只读且不跨请求共享可变配置，清楚表达 namespace、固定挂载、cgroup 和身份；协议及安全值不变。
- [x] isolatedProcess 拥有 init、控制/存活/输入/捕获任务，外部无需处理原始 FD 与内部完成通道，所有部分启动路径可回收。
- [x] execution.Run 独占推进完整生命周期；service 只创建、执行、交付和处理停服，不记忆 Start/Stop/Wait/Close 的组合。
- [x] rootFilesystem 与 initSession 显露 P4 的文件系统职责；P3 建 namespace/cgroup、P5 最终限制与 execve 的顺序不变。
- [x] 执行产物只移交一次，交付和回收故障阻止成功发布；原 Completion/EOF、取消及本地 ref 回滚语义保持。
- [x] 无双重 owner、并行权威状态或纯转发壳；旧名称仅出现在历史记录中。
- [x] 按 PLAN 的局部检查取得证据，正常/超时/取消/部分启动失败可按对象解释；完整 Linux 与独立审查未执行项明确移交 TASK-107。

## 验证

每批执行相关包 go vet 与 go test -race；核对数值与序列化结果。执行计划所需的 Linux 编译及已授权实机回归，无法执行须记录，交由最终验收保留未满足项。对最终 exec 阶段特别检查没有增加策略安装后的不允许调用。

## 风险

函数移动可能改变 defer、goroutine 捕获、FD 生命周期或 Go 线程状态；协议类型提取不能直接改变 cgroup 字段表示。安全依赖数值的提取仍需逐项比对。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
- 2026-09-13：状态变更：todo → ready。原因：TASK-104 和 TASK-113 已完成，用户已明确开始首轮重构
- 2026-09-13：状态变更：ready → doing。原因：实施 B1：具名化启动协议、数值边界与传输期限，后续 B2至B4 尚未执行
- 2026-09-14：状态变更：doing → done。原因：R0至R5 对象所有权重构及约定本地检查通过；完整 Linux 与独立阅读移交 TASK-107

## 2026-09-13 批次与边界细化

历史上对应旧版 PLAN-033 的 B1～B4，严格依次提交可审阅 diff：先协议数字，再 HTTP/runner/container/pool 主线，再 helper 启动监督回收，最后 init/exec 与交付表达。
不得减少或改名 cases.json 固定入口与 requiredGoTests。tests/sandbox-linux 仅可作必要符号适配并记录新 harness 摘要；默认不动该目录、不改断言。
每批验证和撤销范围写 VERIFY-050；任何新增本机协议包都先在 TASK-104 的符号映射说明依赖收益，避免增加无意义转发层。


### B1 交付（2026-09-13）

- [x] init/exec 继承 FD、READY/GO、失败阶段及记录布局具名化，收发双方引用一致。
- [x] 请求/可信 exec/响应容量各归所有者，时间有单位和计时起点；未知历史依据显式保留。
- [x] 原有 52 个必需 Go 测试入口及 93 项 harness 不变；新增固定 ready 消息与 Event 的一致性测试。
- [x] 全模块 gofmt、go vet、go test -race；Linux/amd64 vet、helper 构建和启动边界测试交叉编译通过。
- [ ] B2～B4 实施与验证；TASK 保持 doing，完整 Linux CI 和独立阅读复核未被本地测试替代。

新文件与数字映射、具体命令和限制见 VERIFY-050。下一批 B2 从 runner.Run 的输入→执行→结果→产物开始，不新增转发层。


### 2026-09-14 注释专项

用户补充并明确要求应用15条注释规范，沿既有 /run 执行链补齐必要注释。本轮覆盖 sandbox/API、Pool、Runner、Container、helper、launcher 及 store/cgroup/policy 和两个服务组装入口；属于现有 TASK 写边界。
以当前未提交 B1 为基线，只增删或修正注释，不改变标识符、数值、控制流和构建指令，不提前执行 B2～B4。
逐处说明可从实现验证的所有权、等待条件、平台/权限约束与错误归属，删去语法复述、错误的完成保证及只有 TASK 编号的说明；未知选值依据留在 DESIGN 的盘点，不编造原因。
验收前用 Go scanner 对照本轮起点，要求所有 Go 文件非注释 token（含字面量及自动分号）一致，构建/编译指令不变；再做格式与既有模块检查。记录当前工作区存在其他任务的 CI 和工程文档修改，不覆盖或把其 harness 身份混作 B1 原基线。

- 2026-09-14：注释专项完成。39个Go文件补充/修正必要约束，78个Go文件非注释token及编译指令对照一致；全模块race、双平台vet、格式检查通过。B2～B4仍待实施，证据见VERIFY-050。


### 重新重构的起点

用户指出前两轮没有解决顺序阅读问题并明确要求重新重构。本轮实施已计划的 B2～B4：主编排显式包含影响结果的回收；helper 分离启动、监督、回收，launcher 按实际进程角色组织。前轮数字定义保留，重复解释代码的注释随结构精简。仓库规范已迁入 docs/coding-standards，补充该只读路径以遵循当前 AGENTS 路由，不扩大业务代码写范围。


### B2～B4 本地交付（2026-09-14）

- runner.Run 显式 Start → Wait → 产物收集 → 输入关闭；Pool.Run 显式关闭容器并处理回滚，再返回。输入/产物/结果分类按职责分文件。
- isolated.go 保留 Start/execute/Wait/Close，文件暂存/读取在 isolated_files.go；helper.Call 的结果读取、Completion/EOF 和上传收尾各有直接入口。
- execute → launch → supervise → finish 的总编排已落地；启动失败返回 error，状态字段明确区分 init 停止、退出报告丢失、输入复制结束与输入任务退出。
- launcher 的 Dispatch、PID 1 生命周期与 payload 启动各归职责文件；配置、响应类型、StageSpec/Event、捕获器从混杂文件归位。最终 exec 语句未改。
- 新增输入关闭路径、输入关闭失败回滚、容器关闭前的容量/结果持有三个测试；全模块 race、双平台 vet、Linux helper 与边界测试交叉编译通过。
- TASK 暂保留 doing：完整 Linux 集成与独立阅读验收尚未执行，最后的平台不变量验收项保持未勾选。未替人签验收闸，未启动后续 TASK。


## 新模型交接（2026-09-14）

用户本轮只要求确定后续计划。R0～R5 均未开始，既有 doing 状态保留历史进度，不构成新方案已实施；用户看过修订文档并明确允许后再执行。不扩大 write_paths，不新建任务或改人工闸。

R0 再保存实际未提交源码，保留前轮有效改动。按 R1 隔离配置 → R2 隔离进程 → R3 执行/服务边界 → R4 P4 文件系统 → R5 产物与 P2 集成推进。每批旧字段/方法迁移和测试选择器核对记入 VERIFY-050；当前自由函数不是必须保留的 API。

资源组与最终状态由 execution 负责；协议/FD/I/O 由 isolatedProcess 负责；移交后的产物由 executionResult 负责。若为封装需要改变线程、权限、线格式、预算或完成保证，先升级 DESIGN，不在本 TASK 顺便改变行为。

- 2026-09-14：用户在新计划交付后明确“开始重新重构”，授权执行 R0～R5。已保存实际起点并完成 DESIGN 的 R0 字段/资源/失败测试映射；既有意图闸沿用。


### R0～R5 本地交付

execution.Run、isolationPlan、isolatedProcess、artifactSet 和 P4 rootFilesystem 已落地；service 持有服务资源。原 execute/launch/supervise 平台文件中的重复编排已移除，原故障测试沿资源所有权迁移，新增快照、合法放行、一次执行与产物移交测试。P2 原有 Container/Pool/client 语义经回归保留，无需增加转发对象。

实际检查：sandbox 全包 go test -race、macOS vet、Linux/amd64 vet 与 CGO_ENABLED=0 helper 构建通过。临时 socket 测试通过工具授权在沙箱外执行；Linux 内核运行与独立阅读仍未完成，不计为验收通过。
