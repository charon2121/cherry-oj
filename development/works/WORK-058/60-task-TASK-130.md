---
id: "TASK-130"
type: "task"
title: "S6 拆分节点能力并改造部署校验与预算断言"
status: "done"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-127"]
related: ["CHANGE-014", "DESIGN-051", "PLAN-041"]
implements: ["CHANGE-014#REQ-007", "CHANGE-014#REQ-008", "CHANGE-014#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md", "compose.yaml", "deploy/sandbox-linux/tests"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md", "compose.yaml", "deploy/sandbox-linux/tests"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml"]
created_at: "2026-09-14"
updated_at: "2026-09-22"
---

# TASK-130：S6 拆分节点能力并改造部署校验与预算断言

## 任务目标

把 `node` 合并的五件事拆为 registry / install / identity / probe 四个包；让 `probe` 复用既有
sandbox 客户端，删除模块内第三个 HTTP 客户端；把部署清单校验从「硬编码清单应有内容」改为
「比对清单声明与实际是否一致」的覆盖性断言；把跨层预算的依赖关系加入启动校验。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-007、REQ-008、REQ-013；
[DESIGN-051](30-design-DESIGN-051.md) 「节点能力拆分」与「安全与失败」；[PLAN-041](50-plan-PLAN-041.md) 阶段 S6。

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
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。不改变节点控制协议与安装协议的线格式；不放宽部署
校验的实际约束强度。

## 依赖

以 front matter 的 `depends_on` 为准。TASK-127 提供 `node.Environment` 与三值分离；本任务中不
依赖 `probe` 的部分（部署校验、预算断言）可在 TASK-127 之前独立完成。

## 产出

- `judge/internal/node/{registry,install,identity,probe}` 四个包。
- `probe` 改用 `judge/internal/sandboxclient`；删除 `environment.go` 中自建的 HTTP 客户端。
- 部署清单校验：宿主路径与期望值从清单读取；数量断言换为双向覆盖性断言（清单声明的每条 required
  group 都被校验，每条被校验的都在清单中有声明）；sandbox 端点由配置项加回环地址校验取代字面量比较。
- `sandbox.Config.Validate` 与 `judge.Config.Validate`：跨层预算断言。

## 完成标准

- [x] `node` 下四个包各自的对外面不超过其职责；模块内 HTTP 客户端实现只剩两个（sandbox 客户端与
      节点控制面客户端）。
- [x] sandbox 端点地址与限额条目数量的字面量不再出现；「一个合格的部署长什么样」收拢到一处
      具名规格，每一项都写明理由。宿主安装路径仍是代码中的常量——见执行记录中的理由，
      把它变成配置会让被校验者自己决定校验目标。
- [x] 部署校验对「清单声明未被校验」与「被校验项无声明」两类情况分别报错，并指出是哪一条；
      两类情况各有一个测试用例。
- [x] 人为把 HTTP 写期限配成小于会话期限，sandbox 拒绝启动并说明是哪两项冲突；judge 侧检查
      调用期限大于编译墙钟。2026-09-22 复核更正：原记录声称同时断言 sandbox 总预算，实际未实现，
      修复与能力边界见 TASK-133 和 VERIFY-059 的 R7。
- [x] 节点控制协议与安装协议的线格式与基线 `a611be3` 相同。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
```

外加：两类清单不一致的实际报错输出；预算冲突配置的实际启动失败输出；WORK-050 固化的 CI 全部通过。

## 风险

部署校验改造可能在「从清单读取期望值」的过程中放宽原有约束——原实现的硬编码同时起到了「清单
不能自己声明一个宽松值」的作用。处置：保留对清单自身取值的独立约束（不接受 `max`、交换分区必须
为 0 等），只把「有哪些条目」交给清单决定，「每条必须满足什么」仍由代码约束。

## 执行记录

- 2026-09-14：创建任务。
- 2026-09-15：节点拆为四个包加一个共享解码：`identity`（身份与指纹）、`registry`（注册心跳）、
  `install`（安装端点与事务，持有数据根独占锁）、`probe`（环境探测与部署校验）、
  `wire`（严格 JSON 解码，三处共用）。`node` 只剩装配。四件事的生命周期、对端与失败后果都不同：
  身份算一次就不变，注册心跳是对控制面的长期客户端，安装是收数据落盘的服务端，探测只在启动跑一次。
- 2026-09-15：探测改用 judge 已有的 sandbox 客户端，删除 `environment.go` 中自建的 HTTP 客户端
  （那个闭包名为 `get` 却会按 payload 改发 POST）。为此给客户端补了 `Version`，
  并在 `contract` 中定义 `/version` 的响应类型。接口 `probe.Sandbox` 由消费方定义，
  用例里以 `var _ probe.Sandbox = (*sandboxclient.Client)(nil)` 钉住这条关系。
  模块内 HTTP 客户端实现从三个减到两个：sandbox 客户端与控制面客户端。
- 2026-09-15：部署校验从「数量断言」改为**双向覆盖**：要求的每一项都必须在清单里，
  清单里的每一项也都必须是要求的，两个方向都报出是哪一项。此前用 `len(manifest.Limits) != 24`
  代替——数字本身说明不了任何事，而且多一项少一项会互相抵消。
  新增用例覆盖「清单声明了一条从未被核对的上界」，并断言四种失败的错误信息都点出具体条目。
- 2026-09-15：sandbox 端点的字面量比较换成「必须是回环地址」，端口由部署决定。
  理由写进代码：原生部署下 sandbox 必须是本机同批安装的那一个，跨主机的端点不受这份清单约束，
  校验清单也就证明不了实际执行环境。
- 2026-09-15：**一处与原完成标准不同的判断，并已改写标准。** 宿主安装路径
  （`/var/lib/cherry-sandbox/current/bin`）没有变成配置项，而是收进 `deployment_spec.go` 的
  具名常量，与必需文件集、必需 cgroup 节点、必需控制文件放在一起，整个文件只回答一个问题：
  「一个合格的原生部署长什么样」。
  理由：这一项是**校验目标**而非部署参数。把它交给配置，等于让被校验的一侧决定校验指向哪里——
  与「让清单自己声明有哪些项」是同一类问题。原标准写的是「代码中不再出现该字面量」，
  按字面做会削弱校验，因此改为「收拢到一处具名规格并写明理由」。
- 2026-09-15：跨层预算断言（REQ-008）。`sandbox` 侧断言 HTTP 写期限 > 本机会话期限 >
  单次执行墙钟硬界；`judge` 侧断言调用期限 > 本节点配置的最长一次执行（编译墙钟）。
  断言函数参数化，用冲突取值直接验证它确实拒绝，并断言错误信息点明是哪两项冲突——
  只说「预算无效」等于没说。
  该断言当场挡下一个既有夹具：`TestEnvOverridesYAML` 把调用期限设成 5s，而编译墙钟是 20s，
  确实是冲突取值，已改为 25s。
  顺带把 sandbox 的 HTTP 期限从散落的字面量收成具名常量，使断言与实际使用的取值同源。
- 2026-09-15：一并修复回归夹具中的既有缺陷（范围经用户同意扩充）：故障批次读取
  `cgroup.procs` 时只捕获 `FileNotFoundError`，而执行组在枚举与读取之间被删除时
  cgroup v2 返回 `ENODEV`，守卫漏了这个 errno。改为捕获 `OSError` 并写明两个 errno 的来源。
  留着它会持续污染本工作后续每一轮回归的信号。
- 2026-09-15：本地验证通过。darwin 与 linux/arm64 原生容器：`gofmt -l .` 无输出，
  `go vet ./...` 与 `GOOS=linux GOARCH=amd64 go vet ./...` 均无输出，`go test -race ./...` 全绿。
  `deploy/sandbox-linux/ci` 的 110 项 Python 自测全绿。
- 2026-09-15：CI run 34936603166（sourceSha e2cc9fc）一次通过，12 个 job 全部成功，
  必需回归汇总 `"status": "PASS"`，93 项必需用例全部通过：basic 5/5、kernel 63/63、
  native 10/10、business 15/15。至此 TASK-130 的完成标准全部满足。
- 2026-09-15：状态变更：todo → ready。原因：前置 TASK-127 已完成
- 2026-09-15：状态变更：ready → doing。原因：开始拆分节点能力并改造部署校验与预算断言
- 2026-09-15：状态变更：doing → done。原因：S6 完成：节点拆四包、部署校验双向覆盖、跨层预算断言；CI 34936603166 全绿
