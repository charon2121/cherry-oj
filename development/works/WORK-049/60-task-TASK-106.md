---
id: "TASK-106"
type: "task"
title: "整理 Judge 阅读路径与配置及限额表达"
status: "done"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-105"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-002", "CHANGE-013#REQ-003", "CHANGE-013#REQ-005", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "docs/coding-standards", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux", "development/works/WORK-050", ".github/workflows/ci.yml"]
write_paths: ["apps/judge-engine/internal/judge", "apps/judge-engine/internal/config", "apps/judge-engine/internal/contract", "apps/judge-engine/cmd/judge", "apps/judge-engine/README.md", "development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048", ".github", "development/works/WORK-050"]
created_at: "2026-09-10"
updated_at: "2026-09-14"
---

# TASK-106：整理 Judge 阅读路径与配置及限额表达

## 任务目标

使判题主线清楚地调用单次命令执行，node 旁路独立可读，共享状态与数字表达不依赖读者记忆。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的对象、进程、生命周期与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

TASK-105 已明确单命令执行的入口与返回语义；当前 Linux 环境身份已有 probeDeployment 分支，保持该分流；旧观察需重新核实，不夹带新行为修复。
2026-09-13 已核对 WORK-049 意图闸及 WORK-050 验收；不再等待相同签署。本轮只建立计划，后续按依赖与实际授权将任务推进到 ready 再执行。

## 产出

flow/node/config/contract 限额映射的等价组织调整、必要回归测试、相关阅读说明。保留已知行为问题，单独记录范围升级候选。

## 完成标准

- [x] flow 的主流程可顺读，每请求状态独立，不再传递一长串重复上下文。
- [x] node 身份、控制面心跳、HTTP 入口与安装事务按职责定位。
- [x] 配置移动保持两进程既有校验和默认值行为；不趁机修改跨服务校验。
- [x] Limits 字段索引、位语义、特殊零值与数字归属明确，JSON 编解码兼容。
- [x] compile/run/verdict/输出可见性与既有错误行为无意外变化。

## 验证

相关包 go vet、go test -race，复用契约、limits、flow、node 和 config 既有用例；仅补充有意义的映射/独立状态不变量测试。走一次编译语言和一种无需编译语言的代码路径，明确 source/artifact 归属。

## 风险

把共享参数收进对象可能造成跨请求污染；改变限额索引会影响零值/缺省；为改善日志顺手改变行为不在本任务范围。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
- 2026-09-14：状态变更：todo → ready。原因：TASK-105 已完成本地实施，依用户对新计划的执行授权进入 R6至R7
- 2026-09-14：状态变更：ready → doing。原因：开始每次判题对象、节点安装事务和限额字段映射的等价重构
- 2026-09-14：状态变更：doing → done。原因：R6至R7 实施及全模块 race、双平台 vet 通过；完整 Linux 与独立阅读移交 TASK-107

## 2026-09-13 批次与边界细化

旧版 B5～B7 由当前 PLAN-033 的 R6～R7 承接，在 TASK-105 完成新模型后再整理 flow、node 和 config/contract；现有 environment.go/deployment.go 分流需纳入新的阅读入口。
私有每请求对象不得跨请求共享；指纹参与字段和 JSON 表示不得变化；Limits 下标重构同时核对 present 位与原有编解码。
本轮 read_paths 增加 WORK-050 与 CI 配置，是为核对已冻结断言，不扩大业务实现范围。


## 新模型下的任务范围（2026-09-14）

R6 以每次判题私有对象持有源码/编译引用、配置快照和结果，串起准备、编译、逐点执行比较、汇总和引用清理。Sandbox 仍是消费方接口，judge 不引用 helper/launcher/cgroup 的内部对象；不同请求不能共享可变状态。编译、单点执行的进程数量与原顺序保持。

R7 按 node 的服务生命周期与安装事务归属组织状态，保留既有环境身份分流；配置解析、校验及 Limits 映射在没有资源或状态时继续使用函数。不得把原文件拆分列表当作完成目标，也不新增通用 Context/Manager 框架。

新批次尚未执行，TASK 保持 todo。只补充当前规范的只读路径，不扩大源码写范围；完整等价与结构阅读验收交给 TASK-107 汇总。


### R6～R7 本地交付（2026-09-14）

judgment 负责本次请求、语言、测例和源码/编译引用；文件按编译、测例输入/运行、结果规则、预算计算归属。安装事务独占 staging 与 manifest 快照，Node 保留锁和服务信息；注册身份、控制面、安装 HTTP 入口独立定位。Config 的默认与校验原样归位，Limits 的六字段使用具名索引，新增逐字段显式零位回归。

全模块 go test -race、macOS 与 Linux/amd64 go vet 均通过；原 C++ 编译一次及解释语言跳过编译的 flow 测试、node 安装/冲突/取消/指纹、config 与 contract 测试通过。环境/deployment 分流文件未改，未扩大写边界。独立阅读和完整 Linux CI 交 TASK-107，不登记为已完成。
