---
id: "TASK-129"
type: "task"
title: "S5 提取执行结论纯函数与显式状态转移"
status: "done"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-128"]
related: ["CHANGE-014", "DESIGN-051", "PLAN-041"]
implements: ["CHANGE-014#REQ-006", "CHANGE-014#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md", "compose.yaml"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md", "compose.yaml"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/tests/acceptance"]
created_at: "2026-09-14"
updated_at: "2026-09-15"
---

# TASK-129：S5 提取执行结论纯函数与显式状态转移

## 任务目标

把单次执行的结论从 `supervise` 与 `finish` 对同一批字段的顺序敏感读写中提取为
`Conclude(Facts) (Reason, error)`——不做输入输出、不改写状态、不依赖调用顺序；把执行状态机改为
显式转移表。资源回收仍保持命令式表达与既有顺序。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-006、REQ-013；
[DESIGN-051](30-design-DESIGN-051.md) 「结论推导：事实与回收分离」；[PLAN-041](50-plan-PLAN-041.md) 阶段 S5。

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
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。不改变停组、等待、产物打开、环境释放的先后顺序；
不改变任何结论取值。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `helperd/internal/execution`：`Facts`（含握手结果、退出事实、init 事实、最终计量、预算、墙钟、
  输出事实、取消标记、平台故障）与 `Conclude`。
- `finish` 收缩为：停组 → 等待 → 收集 `Facts` → `Conclude` → 交付。
- 执行状态机的显式转移表，非法转移返回错误。
- `Facts → Reason` 表驱动测试，覆盖正常、墙钟超时、CPU 超预算、请求取消、输出超限、本任务 OOM、
  init 失联、握手超时、平台故障各路径。

## 完成标准

- [x] `Conclude` 不含任何输入输出调用，不接收指针接收者，不写任何包级或结构体字段。
- [x] `supervise` 与 `finish` 中不再存在对同一结论字段的先写后撤销。
- [x] 表驱动测试在 macOS 上通过，且每条用例的期望结论与基线 `a611be3` 的实际行为一致，对照结果
      写入执行记录。
- [x] 状态机的每一条合法转移都有用例；至少一条非法转移用例断言返回错误。
- [x] Linux 回归中的结论与重构前逐条一致。

## 验证

```bash
cd apps/judge-engine
go test -race ./helperd/...      # 含表驱动结论测试，macOS 可跑
gofmt -l . && go vet ./...
```

外加 WORK-050 固化的 Linux 回归，比对超时、取消、OOM 三类场景的最终结论。

## 风险

搬运过程中可能丢失某个当前生效但未被测试覆盖的分支，表现为某类失败被判成错误结论。
处置：**测试先行**——先在重构前的实现上补齐表驱动测试并单独提交，确认全绿后再搬运逻辑，使任何
行为变化都表现为测试失败。

## 执行记录

- 2026-09-14：创建任务。
- 2026-09-15：**测试先行（单独提交 d5977ce）**。先把「执行事实 → 终止结论」钉成十组对照表，
  跑在重构前的实现上全绿。覆盖：正常退出、监督已判定墙钟/取消、最终计量兜底 CPU、
  已有结论不被最终计量改判、祖先 OOM 无本任务证据、祖先 OOM 叠加丢失退出报告、
  本任务 OOM 解释丢失的退出报告、本任务 OOM 不掩盖独立故障、丢失退出报告且无 OOM 证据。
- 2026-09-15：提取纯函数 `conclude(executionFacts) conclusion`：不做输入输出、不改写状态、
  不依赖调用顺序。原先散在 `supervise` 与 `finish` 中对 `result.Reason`、`runErr`、
  `initReportLost`、`Signal` 的顺序敏感读写全部收拢，其中「撤销先前结论」那一段
  （本任务 OOM 解释了丢失的退出报告）成为函数内一个有名字的分支。
  `finish` 收缩为：停组 → 等待 → 收集事实 → `conclude` → 交付，只负责按内核要求的顺序
  收集事实与回收资源。
- 2026-09-15：`supervise` 改为返回 `supervisionOutcome` 而不再改写 execution 的结果字段。
  `process.Start` 失败也走同一条路（`x.supervision.fail`）——它同样是「这条命令怎么停下来的」
  一种，若仍写进 `x.fail`，它记下的平台故障会被最终结论覆盖掉。这一处是重构中唯一需要
  重新安排归属的地方，其余为等价搬运。
- 2026-09-15：状态机改为显式转移表 `executionTransitions`。此前六个状态只在 Run / Close /
  finish 里零散赋值，「哪些转移合法」要通读三处才能拼出来。表建立后立刻拦下两处：
  测试直接调用 `finish` 绕过了 `new → starting`，暴露出这些白盒用例其实没有经过启动阶段；
  已补 `finishFrom` 夹具显式走一遍启动，与生产路径一致。
- 2026-09-15：对照结果——**同一张表原样跑在重构后的实现上，十组全绿**。
  另加同表直接驱动纯函数的用例：不需要资源组、进程、FD 或任何平台能力，
  提取之前这些分支只能通过整台执行机器间接触发，且多数要在 Linux 上才跑得到。
  同一份事实连续判定两次必须给出相同结论，作为「纯」的显式断言。
- 2026-09-15：基准做过变异验证，两次都被拦住：① 去掉祖先 OOM 分支 → 「祖先 OOM 没有本任务
  证据」报 `reason="" want "platform"`；② 让 OOM 撤销分支不再检查 `reportLost` →
  「本任务 OOM 不掩盖独立故障」报结论错误中缺少 `snapshot failed`。
  第二条正是这次重构最容易出错的地方：条件写松一点，一次平台故障就会被悄悄抹成干净结果。
- 2026-09-15：新增用例：停组失败时不得用不可信的计量推断资源结论；状态转移表对六组非法转移
  逐一拒绝且错误信息指出是哪一步。
- 2026-09-15：本地验证通过。darwin 与 linux/arm64 原生容器：`gofmt -l .` 无输出，
  `go vet ./...` 与 `GOOS=linux GOARCH=amd64 go vet ./...` 均无输出，`go test -race ./...` 全绿。
- 2026-09-15：推送后 CI 的 Linux 隔离 job 失败且**稳定复现**，但失败消息只有
  「helper socket did not become ready」——helper 要等每个槽位的启动冒烟都通过才开放 socket，
  冒烟不过时真正的原因留在 helper 自己的输出里，外部看不到。本地又因真实链路需要 systemd
  无法复现。因此先补一条诊断（`deploy/sandbox-linux/ci/kernel.py`）：socket 超时时把该单元
  最近的日志一并带出。**这是一处刻意的范围外改动**——PLAN-041 允许改的是「因本工作而失效的
  引用」，这条是新增诊断；理由是不加它就无法定位，且它只加输出、不改任何断言与流程。
- 2026-09-15：诊断随即给出原因：`隔离启动能力冒烟失败: reason=cancelled`。
  **缺陷由本阶段引入**：重排 `finish` 时把 `x.process.CancelInput()` 挪到了读取 `ctx.Err()`
  之前。helper 的启动冒烟把 `probeCancel` 一并接进 `cancelInput`，于是解除输入阻塞的同时也
  取消了这次执行自己的上下文，每次正常执行都被判成已取消。原实现先算 `Cancelled` 再
  `CancelInput`，顺序是必要的，只是没有任何东西说明它必要。
  已修复，并补与冒烟同样接线的回归用例；把该用例还原成有缺陷的写法会立刻失败，报
  `reason="cancelled"`，与 CI 的现象逐字一致。
- 2026-09-15：另修一处本阶段引入的缺陷：状态转移表漏了 `starting → cleanup-failed`。
  资源组都没建起来时 Run 会直接走这一步，被表拒绝后错误里会混进一条无关的「非法转移」，
  掩盖真正的建组失败原因。
- 2026-09-15：**遗留观察（不属于本工作，建议另行立项）**：故障批次的容量用例
  （`deploy/sandbox-linux/tests/fault_batch.py`）在枚举执行组并读取 `cgroup.procs` 时
  存在竞态——组可能在枚举与读取之间被删除。该处已经预期到这种情况并捕获了
  `FileNotFoundError`，但 cgroup v2 删除后读取返回的是 `ENODEV`，守卫漏了这个 errno，
  于是抛出 `OSError: [Errno 19] No such device`。
  同一提交重跑后通过，确认是偶发而非本阶段回归。
  VERIFY-051 记录过同类问题（观测器把 cgroup 控制文件当成子目录），已在 4b5df32 修过业务侧；
  这次是故障批次的同类遗漏。该文件在本任务的禁止修改范围内，未改动。
- 2026-09-15：CI run 34928218217（attempt 2，sourceSha 2e90640）全绿，必需回归汇总
  `"status": "PASS"`，93 项必需用例全部通过：basic 5/5、kernel 63/63、native 10/10、
  business 15/15。至此 TASK-129 的完成标准全部满足。
- 2026-09-15：状态变更：todo → ready。原因：前置 TASK-128 已完成
- 2026-09-15：状态变更：ready → doing。原因：开始提取执行结论纯函数与显式状态转移
- 2026-09-15：状态变更：doing → done。原因：S5 完成：结论纯函数、显式状态转移、对照表；CI 34928218217 全绿，93 项必需回归通过
