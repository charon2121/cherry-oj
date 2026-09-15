---
id: "TASK-126"
type: "task"
title: "S2 重切为三棵服务子树并收薄命令行入口"
status: "doing"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-125"]
related: ["CHANGE-014", "DESIGN-051", "PLAN-041"]
implements: ["CHANGE-014#REQ-001", "CHANGE-014#REQ-013", "CHANGE-014#REQ-014", "CHANGE-014#REQ-016"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/tests/acceptance"]
created_at: "2026-09-14"
updated_at: "2026-09-15"
---

# TASK-126：S2 重切为三棵服务子树并收薄命令行入口

## 任务目标

把 `apps/judge-engine` 重排为 `judge/`、`sandbox/`、`helperd/` 三棵服务子树，实现包下沉到各自的
`internal/`，使跨信任边界的引用由 Go 的可见性规则在编译期拒绝；`cmd/*` 退化为参数解析并调用服务
入口。本阶段只做移动、重命名与 import 路径修改，不改变任何逻辑。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-001、REQ-013、REQ-014；
[DESIGN-051](30-design-DESIGN-051.md) 「整体方案」的目标结构；[PLAN-041](50-plan-PLAN-041.md) 阶段 S2。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。`deploy/sandbox-linux/ci/` 的必跑用例清单在
可修改范围内，但**只允许更新 judge-engine 用例的包路径**：不得增删用例、改断言或放宽必需数量
（Go 必跑固定 52 项）。同理 `.github/workflows/ci.yml` 与 `deploy/sandbox-linux/tests/README.md`
**只允许改路径**，不改 job 结构、触发条件、权限、步骤顺序与操作语义。报告 schema、
`deploy/` 与 `.github/` 下其余内容仍然禁止修改。理由见
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。本阶段禁止修改任何函数体逻辑、常量取值与 HTTP 路由。

## 依赖

以 front matter 的 `depends_on` 为准。TASK-125 必须先完成，否则 `container` 仍引用 helper 包，
子树划分不成立。

## 产出

- `judge/`：`judge.go`（`Run(ctx, Config) error`）、`doc.go`，实现包迁入 `judge/internal/`。
- `sandbox/`：`sandbox.go`、`doc.go`，实现包迁入 `sandbox/internal/`；`cmd/sandbox/main.go` 中的
  生命周期编排迁入 `sandbox.Run`。
- `helperd/`：`helperd.go`、`doc.go`，helper 服务端、执行、cgroup、launcher 角色实现、policy、
  trust、recovery 迁入 `helperd/internal/`；`Dispatch` 由 `helperd` 顶层再导出。
- `internal/` 顶层只保留 `contract`、`hostexec`、`platform` 三者。
- 三个 `cmd/*/main.go` 只保留参数解析与入口调用。

## 完成标准

- [ ] `go list` 显示 `sandbox/...` 不引用 `helperd/...`，`judge/...` 不引用 `sandbox/...` 与
      `helperd/...`，`helperd/...` 不引用 `judge/...` 与 `sandbox/...`。
- [ ] 人为构造一处从 `sandbox/internal` 引用 `helperd/internal` 的改动，`go build ./...` 失败；
      在执行记录中保留报错原文，随后撤销该改动。
- [ ] 顶层 `internal/` 下只剩跨服务共享的 `contract`、`hostexec`、`platform` 与尚未拆分的
      `config`；`config` 的下沉属于 S3（[TASK-127](60-task-TASK-127.md)），不在本阶段完成。
- [ ] 三个 `cmd/*/main.go` 只做参数解析、配置加载、日志初始化与信号监听，不含任何服务逻辑；
      服务编排全部位于对应服务包的 `Run`。
- [ ] `git log --follow` 可追溯每个被移动文件的历史。移动之外的改动仅限于目录重切直接要求的
      部分（cmd 与服务包的切分、被新边界禁止的测试依赖），每一处在执行记录中单独说明。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
go list -deps ./... | grep cherry-oj    # 人工核对依赖方向
```

外加 WORK-050 固化的 CI 全部 job 通过。

## 风险

一次性大规模移动会产生难以逐行复核的 diff。处置：逐包搬运并分多次本地提交，最终压成一个阶段提交；
搬运过程中保持文件内容逐字节不变（仅改 package 行与 import 路径），使重命名检测生效。

## 执行记录

- 2026-09-14：创建任务。
- 2026-09-15：完成目录重排。`internal/logging` → `internal/platform/logging`；
  `internal/tracecontext` → `internal/platform/tracing`（包名一并改为 `tracing`）；
  `internal/judge/*` → `judge/internal/*`，其中 `client` 更名 `sandboxclient`，与节点控制面
  客户端区分；`internal/sandbox/{api,container,pool,runner,store}` → `sandbox/internal/*`；
  `internal/sandbox/{helper,cgroup,launcher,policy}` → `helperd/internal/*`；
  `tests/sandbox-linux/*` → `helperd/tests/*`（`boundary` 依赖 cgroup/launcher/policy，
  留在原处将无法引用）。`internal/config` 仍在顶层，按计划由 S3 拆分。
- 2026-09-15：新增三个服务入口与包文档：`judge.Run`、`sandbox.Run`、`helperd.Run`（后者再导出
  `Dispatch`/`LoadConfig`，使 cmd 不必引用 `helperd/internal/launcher`）。三个 `cmd/*/main.go`
  只保留参数解析、配置加载、日志初始化与信号监听，服务编排全部下沉；行数 51/38/51。
- 2026-09-15：**边界已由编译器强制**。构造四处跨边界引用，`go build ./...` 均失败，报错原文：
  - `sandbox/internal/runner/zz_probe.go:3:8: use of internal package .../helperd/internal/helper not allowed`
  - `judge/internal/flow/zz_probe.go:3:8: use of internal package .../sandbox/internal/container not allowed`
  - `helperd/internal/helper/zz_probe.go:3:8: use of internal package .../judge/internal/flow not allowed`
  - `cmd/judge/zz_probe.go:3:8: use of internal package .../judge/internal/flow not allowed`

  探针文件已全部删除，删除后 darwin 与 linux/amd64 构建均通过。
- 2026-09-15：**偏离一（移动之外的改动，边界直接要求）**：
  `judge/internal/language/languages_e2e_test.go` 原先引用 `sandbox/internal/container` 的零隔离
  后端作为命令执行器。新边界下 judge 子树无法引用它——这正是 `docs/engine.md` §1 要求的
  「judge 不得进程内调用 sandbox 的执行实现」。改为在 `judge/internal/language/workspace_test.go`
  内自备「临时目录 + 起进程」的最小夹具，复刻该测试依赖的两条规则（命令在工作目录中解析、
  进程工作目录即工作间）。断言一条未改；cpp/python/java 三个用例本地全部通过。
  `deploy/sandbox-linux/ci/diagnose_language.py` 按 `%+v` 文本解析 java 编译耗时，夹具的
  `usage` 结构保持相同字段名与顺序，用该脚本的正则实测仍能匹配。
- 2026-09-15：**偏离二（本阶段引入并已修复的缺陷）**：把 `cmd/sandbox` 的 `return 1` 机械替换为
  `return err` 时，后端探测那段的局部错误变量名为 `e` 而非 `err`，`return err` 因此返回了外层
  已为 nil 的 `err`——探测失败会被当作启动成功并照常开放 HTTP 端口，与该处注释
  「失败不开放HTTP端口」相反。`go vet` 无法发现（`err` 确在作用域内）。现返回真实错误，
  并把遮蔽命名的局部变量 `result` 改名 `probe`，避免与具名返回值混淆。
- 2026-09-15：同步 CI 与部署中指向判题引擎内部路径的引用（范围经用户同意后扩充，只改路径）：
  `ci/cases.json`（必跑清单包路径与 5 条 case 路径，Go 必跑仍为 52 项）、`ci/prepare.py`、
  `ci/report.py`、`ci/diagnose_language.py`、`.github/workflows/ci.yml` 的 `TESTDATA_PATH`
  （`compose.legacy.yaml` 以它作 bind mount 源，失效会让 legacy 回退 job 失败）、
  `deploy/sandbox-linux/tests/README.md` 的手工构建路径。
  以 CI 同款命令 `docker compose config --quiet` 验证新挂载源可解析。
  全仓复查确认无其他功能性引用指向旧位置；其余命中均在历史 WORK 文档中，记录的是当时的事实，
  不改写。
- 2026-09-15：本地验证通过。darwin：`gofmt -l .` 无输出，`go vet ./...` 与
  `GOOS=linux GOARCH=amd64 go vet ./...` 均无输出，`go test -race ./...` 全绿。
  linux/arm64 原生容器（`golang:1.26.3-bookworm`）：gofmt、vet、`go test ./...` 全绿。
  `deploy/sandbox-linux/ci` 的 110 项 Python 自测全绿。
  注：先前在 amd64 模拟下 `TestOutputRejectsLinksAndSpecialFiles` 报
  `function not implemented`，用上一提交 473144e 在同一容器做对照，失败方式完全一致，
  确认是 QEMU 未实现该系统调用，非本次引入。
- 2026-09-15：推送后 CI（run 34921700536）「judge + sandbox（legacy 回退）」失败：镜像构建报
  `package cherry-oj/judge-engine/judge is not in std`。原因在 `.dockerignore`：其中的
  `judge` 与 `sandbox` 两条原本用于排除同名的**构建产物二进制**，新建的服务**目录**同名，
  于是整棵源码树被挡在构建上下文之外。这两条现已失效——`judge`/`sandbox` 位置已是目录，
  `go build -o judge` 不可能再产出同名文件——故直接删除。
  本地用 CI 同款命令复现并验证：`docker compose build` 两个镜像构建成功；
  `docker compose up --wait` 后调用 `/judge` 做 A+B 联调，`verdict=AC`、3 个测试点全 AC、
  指纹 `local-compose`，与 CI 断言一致。
- 2026-09-15：**尚未执行**：WORK-050 固化的 Linux 隔离与故障回收回归，需推送后由 CI 执行。
- 2026-09-14：状态变更：todo → ready。原因：意图闸已签署
- 2026-09-15：状态变更：ready → doing。原因：开始按信任与部署边界重排目录
