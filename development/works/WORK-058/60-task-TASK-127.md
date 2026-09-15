---
id: "TASK-127"
type: "task"
title: "S3 拆分服务配置并显式化环境指纹输入"
status: "todo"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-126"]
related: ["CHANGE-014", "DESIGN-051", "DECISION-035", "PLAN-041"]
implements: ["CHANGE-014#REQ-002", "CHANGE-014#REQ-003", "CHANGE-014#REQ-012"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/tests/acceptance"]
created_at: "2026-09-14"
updated_at: "2026-09-14"
---

# TASK-127：S3 拆分服务配置并显式化环境指纹输入

## 任务目标

把单一 `config.Config` 拆成三份服务配置，配置装配机制下沉为 `platform/config` 的泛型 `Load`；
把「加载到的配置」「探测到的环境」「注册得到的身份」分成三个值；把环境指纹的输入从「对整个
判题配置做 JSON 序列化」改为显式字段结构，并用固定输入的基准测试锚定。

本阶段是本工作中唯一会改变环境指纹的阶段（[DECISION-035](40-decision-DECISION-035.md) 决定一）。

## 依据

[CHANGE-014](10-change-CHANGE-014.md) REQ-002、REQ-003、REQ-012；
[DESIGN-051](30-design-DESIGN-051.md) 「三份配置」「配置、环境与身份三者分离」「环境指纹的显式输入」；
[PLAN-041](50-plan-PLAN-041.md) 阶段 S3。

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
[PLAN-041](50-plan-PLAN-041.md) §必跑用例清单随包路径同步。不改变任何配置项的默认值、YAML 键名与环境变量名；
不改变节点控制协议的线格式。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `internal/platform/config`：泛型 `Load`，类型参数是具体服务的配置类型、约束为 `Validatable`；
  保留 `Duration`、`KnownFields` 与反射式环境变量覆盖；不认识任何具体配置字段。
- `judge/config.go`、`sandbox/config.go`、`helperd/config.go`：各自的 `Config`、`Default`、`Validate`。
- `judge/internal/node/identity`：显式 `policyFingerprint` 结构（带 JSON 字段名标注）与基准测试。
- `node.Environment`、`node.Identity` 两个独立类型；`judge.Run` 不再把探测结果回填进配置。
- 语言清单由 `language` 注册表遍历生成，覆盖 cpp / python / java。

## 完成标准

- [ ] 以「仅 judge 段有效、sandbox 段为非法值」的配置启动 judge 成功；反向同样成立。
- [ ] 指纹基准测试三种情形：固定输入给出固定摘要；向 `judge.Config` 新增字段后仍通过；向
      `policyFingerprint` 新增字段后失败并提示更新基准。
- [ ] `judge.Config` 加载后在整个进程生命周期内不被写入（由类型或构造方式保证）。
- [ ] 全部配置项的默认值、YAML 键名与环境变量名与基线 `a611be3` 相同，在执行记录中列出比对结果。
- [ ] 节点向控制面声明的语言集合与 `language` 注册表一致。

## 验证

```bash
cd apps/judge-engine
gofmt -l . && go vet ./... && go test -race ./...
```

外加：两组交叉配置的实际启动记录；指纹基准测试三种情形的实际输出；WORK-050 固化的 CI 全部通过。

指纹轮换的节点协议表现由 [VERIFY-059](70-verify-VERIFY-059.md) AC-012 覆盖。**切换 `ACTIVE` 是人工
动作，本任务不执行。**

## 风险

配置拆分会改变环境指纹；若与节点切换动作配合不当，已部署测试数据的回执会失效而无人察觉。
处置：在执行记录中明确写出新旧指纹值，并提示人工切换是必需的后续动作。

反射式环境变量覆盖改为泛型后，可能在类型判断上出现与原实现不同的边界行为。处置：保留原有
`config` 包的全部测试用例并迁移到新位置，不减少覆盖。

## 执行记录

- 2026-09-14：创建任务。
