---
id: "TASK-131"
type: "task"
title: "S7 统一错误消息语言并重写结构文档"
status: "todo"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-128", "TASK-129", "TASK-130"]
related: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041"]
implements: ["CHANGE-014#REQ-009", "CHANGE-014#REQ-010"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs", "development/README.md", "development/works/WORK-049", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md"]
write_paths: ["apps/judge-engine", "docs/engine.md", "docs/coding-standards/languages/go.md", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "docs/architecture.md", "docs/product.md", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/tests/acceptance"]
created_at: "2026-09-14"
updated_at: "2026-09-14"
---

# TASK-131：S7 统一错误消息语言并重写结构文档

## 任务目标

把全模块的错误消息统一为英文（[DECISION-035](40-decision-DECISION-035.md) 决定三）；为三个服务补齐
说明职责与引用边界的包文档；按新结构重写 `docs/engine.md`（决定五）；同步 Go 编码规范中引用旧包
路径的条目。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-009、REQ-010；[DECISION-035](40-decision-DECISION-035.md)
决定三与决定五；[PLAN-041](50-plan-PLAN-041.md) 阶段 S7。

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
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。改写错误消息时只改字面量，不改 `%w` 包装结构、
`errors.Is`/`errors.As` 判定条件与错误值身份；不改变 `docs/architecture.md` 的系统级结论。

## 依赖

以 front matter 的 `depends_on` 为准。必须在结构调整全部完成后进行，使文档描述最终结构。

## 产出

- 全模块英文错误消息。
- `judge/doc.go`、`sandbox/doc.go`、`helperd/doc.go`：职责、引用边界、谁可以引用本子树。
- 重写后的 `docs/engine.md`：按三棵服务子树与两条硬边界组织；保留其中仍然成立的判断（sandbox 不
  理解判题、隔离与限量是两条正交的轴、为什么没有 `/compile`），删除已不存在的结构描述
  （容器复用、`Container.Reset()`、cgroup 与 container 互不依赖）。
- `docs/coding-standards/languages/go.md` 中引用旧包路径的条目更新（规则本身不变）。
- 受错误消息改写影响的日志检索表达式清单，写入执行记录供运维更新。

## 完成标准

- [ ] 模块内 `fmt.Errorf` / `errors.New` 的字面量全部为英文，由检查命令给出证据。
- [ ] 错误包装结构与判定条件未变：`errors.Is` / `errors.As` 的既有用例全部通过。
- [ ] 三个服务各有 `doc.go`，说明本子树可被谁引用、不可引用什么。
- [ ] 按重写后的 `docs/engine.md` 实走一次源码，不出现文档描述与实现不符之处；实走记录写入
      [VERIFY-059](70-verify-VERIFY-059.md)。
- [ ] `docs/engine.md` 中不再出现 `Container.Reset()`、容器池化复用等已不存在的结构。
- [ ] 受影响的日志检索表达式清单完整。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
grep -rnE '(fmt\.Errorf|errors\.New)\("' --include='*.go' . | grep -P '[\x{4e00}-\x{9fff}]'   # 应无输出
```

外加 WORK-050 固化的 CI 全部通过；文档实走由人或独立复核执行。

## 风险

批量改写错误字面量时可能误改判定条件（例如把用于比较的字符串一并改掉）。处置：只替换传给
`fmt.Errorf` / `errors.New` 的字面量，不触碰任何字符串比较；改写后完整跑一遍测试。

`docs/engine.md` 重写可能丢失其中仍然成立的设计判断。处置：先列出需要保留的判断清单，重写后
逐条核对。

## 执行记录

- 2026-09-14：创建任务。
