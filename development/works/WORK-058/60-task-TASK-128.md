---
id: "TASK-128"
type: "task"
title: "S4 执行后端改为一次性调用并回收容量池职责"
status: "done"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-126"]
related: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041"]
implements: ["CHANGE-014#REQ-005", "CHANGE-014#REQ-011", "CHANGE-014#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md", "compose.yaml"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md", "compose.yaml"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/tests/acceptance"]
created_at: "2026-09-14"
updated_at: "2026-09-15"
---

# TASK-128：S4 执行后端改为一次性调用并回收容量池职责

## 任务目标

把 `container.Container` 的四阶段时序协议（放输入 → 启动一次 → 等待 → 取产物 → 关闭）改为
`backend.Backend` 的一次性 `Execute`，使「只能执行一次」由「不存在可复用对象」保证；把暂存根管理
从执行实现中分出为 `workspace` 包；把产物回滚从容量池移回 `runner`；把零隔离后端改名 `devhost`
并在未显式允许时拒绝启动。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-005、REQ-011、REQ-013；
[DESIGN-051](30-design-DESIGN-051.md) 「执行后端：一次性调用」；[DECISION-035](40-decision-DECISION-035.md)
决定二；[PLAN-041](50-plan-PLAN-041.md) 阶段 S4。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。`deploy/sandbox-linux/ci/` 的必跑用例清单在
可修改范围内，但**只允许更新 judge-engine 用例的包路径**：不得增删用例、改断言或放宽必需数量
（Go 必跑固定 52 项）。同理 `.github/workflows/ci.yml`、`deploy/sandbox-linux/tests/README.md`
与 `compose.yaml` **只允许改因本工作而失效的路径或配置取值**，不改 job 结构、触发条件、
权限、步骤顺序、服务定义、网络、卷与健康检查。报告 schema、
`deploy/` 与 `.github/` 下其余内容仍然禁止修改。理由见
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。不改变本机执行协议的线格式、取消语义与回收顺序；
不降低隔离强度。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `sandbox/internal/backend`：`Job`、`Source`、`NamedSource`、`OutputSink`、`Facts` 与
  `Backend.Execute`；两个实现分别位于 `backend/hostexec` 与 `backend/devhost`。
- `sandbox/internal/workspace`：暂存根独占锁、启动恢复与单次执行目录分配。
- `sandbox/internal/pool`：移除产物回滚与 store 引用，只保留名额、排队与关闭。
- `sandbox/internal/runner`：承接产物回滚。
- `sandbox/config.go`：不安全后端开关，默认关闭。

## 完成标准

- [x] `Backend` 接口只有 `Execute` 一个方法；两个实现中不再存在 `attempted`、`closed`、
      `process != nil` 之类用于守护调用顺序的状态字段。
- [x] `pool` 包不再引用 `store`。
- [x] `Execute` 返回 nil 时，资源回收已完成：正常完成、墙钟超时、请求取消、部分启动失败四条路径
      的 Linux 回归均无残留进程、挂载、cgroup 与临时文件。
- [x] 配置未显式允许不安全后端时，以 `devhost` 启动 sandbox 失败并给出明确原因。
- [x] `trusted-host` 字面量在代码中不再出现；配置迁移说明写入执行记录。
- [x] 对外 `/run` 响应字段与状态取值与基线 `a611be3` 相同。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
```

外加 WORK-050 固化的 Linux 隔离与故障回收回归，逐条记录四条路径的清理证据。

## 风险

接口重写会改动取消与产物交付的时序，这是现有实现中最易出错的部分。处置：先在旧接口上补齐四条
路径的测试，确认通过后再重写；重写后用同一批测试对照。

把回收责任收进 `Execute` 后，若某条失败路径提前返回而未完成回收，会退化为「回收未确认却发布产物」。
处置：在 `Execute` 的每个返回点核对回收已发生，并保留「无法确认回收即停止接单」的既有行为。

## 执行记录

- 2026-09-14：创建任务。
- 2026-09-15：接口重写完成。`container.Container` 的四阶段时序协议改为
  `backend.Backend` 的一次性 `Execute`。「只能执行一次」由「不存在可复用对象」保证，
  两个实现中守护调用顺序的状态字段（`attempted`、`closed`、`closeOnce`、`process != nil`）
  随之全部消失；`Execute` 返回即代表回收完成，调用方不再需要任何关闭方法。
- 2026-09-15：`OutputSink` 的签名带上执行事实：`func(facts Facts, name string, r io.Reader) error`。
  产物在执行结束、回收完成之后逐个交付，收集方在交付当场即可判断这次结果值不值得保留。
  这一处刻意与旧实现不同：若只给名字和流，一次超时或非零退出的执行会把产物写进 Store 再删掉，
  既多一次落盘，也可能在容量紧张时把一次超时变成平台错误。
- 2026-09-15：回收失败从「看是哪个方法返回的错误」变成显式类型 `backend.CleanupError`。
  普通执行失败只让本次执行失败，回收未确认则意味着容量不能归还给新任务；
  `pool` 用 `errors.As` 识别并停止接单，语义与旧的「Container.Close 失败即停服」一致，
  但不再依赖调用顺序来区分。
- 2026-09-15：`pool` 回归纯容量管理：不再创建工作区、不再持有 Store、不再回滚产物引用。
  产物回滚回到 `runner`——产物发布本来就在那里。`workspace`（暂存根的独占锁与启动恢复）
  与 `backend`（执行）分成两个包，前者不理解执行，后者不管理暂存根生命周期。
- 2026-09-15：零隔离后端改名 `devhost` 并要求显式承认。新增 `sandbox.allowUnsafeBackend`，
  缺省即拒绝启动，配置校验与装配处各挡一次。包注释写明它**不强制** CPU/内存/进程数限额——
  只有墙钟真正生效，其余数值仅作为事实回报供事后比较；旧名 `trusted-host` 听起来像一种可选的
  信任模式，实际是「没有隔离」，这正是改名的理由。
- 2026-09-15：测试按原断言迁移，一条未丢。原 `container` 包的用例重写到 `backend`；
  `pool` 与 `runner` 的替身从假容器换成假后端。保留的关键断言：缺完成尾帧不得交付产物、
  取消要让对端观察到断连、内存超限要求本任务 OOM 证据、回收结束前不归还容量也不返回结果、
  回收未确认要停止接单并回滚产物、输入在每条退出路径恰好关闭一次、失败的执行不得发布产物、
  panic 不泄漏容量、中途 Put 失败要回滚已登记的引用。
  新增：`Execute` 返回后工作目录必须已不存在、墙钟超时要区别于请求取消、零隔离后端不得声称
  整组计量、越界输入不能在宿主上落下文件、零隔离后端未显式承认时拒绝启用。
- 2026-09-15：**范围再次扩充（用户已同意）。** 改名后 `compose.yaml` 的
  `CHERRY_OJ_SANDBOX_BACKEND: trusted-host` 失效，legacy 回退 job 会起不来。该文件不在原
  `write_paths` 内，且此前的扩充规则只写了「指向内部**路径**的引用」，未覆盖**配置取值**。
  经用户确认，规则放宽为「任何因本工作而失效的 CI 或部署引用」，`compose.yaml` 纳入范围，
  仍只改失效的那两项取值。PLAN-041 与 TASK-125～131 已同步更新。
- 2026-09-15：本地验证通过。darwin 与 linux/arm64 原生容器：`gofmt -l .` 无输出，
  `go vet ./...` 与 `GOOS=linux GOARCH=amd64 go vet ./...` 均无输出，`go test -race ./...` 全绿。
  另用 CI 同款命令在本地完整复现容器联调：`docker compose config`、`docker compose build`、
  起栈后调用 `/judge` 做 A+B，`verdict=AC`、3 个测试点全 AC、指纹 `local-compose`。
- 2026-09-15：CI run 34925407839 首次运行时业务闭环 `business.calibrate` 失败。
  取证后确认与本阶段无关：判题相关步骤全部通过（校准状态 VALID、发布前检查就绪、版本发布成功），
  失败的是随后一次纯业务侧请求 `PATCH /api/admin/problems/{id}`（设置可见性）返回 500，
  耗时 2.09 秒而同批其他请求均在 0.1～0.6 秒；`resources-after` 与 `native-resources-after`
  均为空，`cleanup.confirmed=true`，沙箱侧没有任何残留。该请求路径不经过判题引擎。
  以同一 commit 整条重跑（attempt 3）后全绿，因此判定为偶发，非本阶段引入的回归。
- 2026-09-15：**遗留观察（不属于本工作，建议另行立项）**：上述 500 是一次未解释的偶发。
  证据已保留在 run 34925407839 attempt 1 的业务产物中。重跑转绿不等于问题消失——
  不记下来的话，这类偶发会被一次次「再跑一遍」养成常态。
- 2026-09-15：CI run 34925407839（attempt 3，sourceSha 5555521）全绿，12 个 job 全部成功，
  必需回归汇总 `"status": "PASS"`，93 项必需用例全部通过：basic 5/5、kernel 63/63、
  native 10/10、business 15/15。其中 kernel job 覆盖真实内核下的隔离、计量与回收，
  提供了四条路径中「部分启动失败」与真实清理的证据。至此 TASK-128 的完成标准全部满足。
- 2026-09-15：状态变更：todo → ready。原因：前置 TASK-126 已完成
- 2026-09-15：状态变更：ready → doing。原因：开始把执行后端改为一次性调用并回收容量池职责
- 2026-09-15：状态变更：doing → done。原因：S4 完成：一次性 Execute 接口、容量池回归本职、devhost 改名与默认拒绝；CI 34925407839 全绿，93 项必需回归通过
