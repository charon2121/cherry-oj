---
id: "TASK-130"
type: "task"
title: "S6 拆分节点能力并改造部署校验与预算断言"
status: "ready"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["TASK-127"]
related: ["CHANGE-014", "DESIGN-051", "PLAN-041"]
implements: ["CHANGE-014#REQ-007", "CHANGE-014#REQ-008", "CHANGE-014#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "CLAUDE.md", "docs/coding-standards", "docs/architecture.md", "docs/engine.md", "development/README.md", "development/works/WORK-049", "development/works/WORK-050", "development/works/WORK-058", "apps/judge-engine", "contracts", ".github/workflows/ci.yml", "deploy/sandbox-linux/ci", "deploy/sandbox-linux/tests/README.md", "compose.yaml"]
write_paths: ["apps/judge-engine", "development/works/WORK-058", "deploy/sandbox-linux/ci", ".github/workflows/ci.yml", "deploy/sandbox-linux/tests/README.md", "compose.yaml"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "scripts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-049", "development/works/WORK-050", "deploy/sandbox-linux/install", "deploy/sandbox-linux/rootfs", "deploy/sandbox-linux/systemd", "deploy/sandbox-linux/build-release.sh", "deploy/sandbox-linux/probe.sh", ".github/workflows/language-diagnostic.yml", ".github/workflows/sandbox-download-cold.yml", "deploy/sandbox-linux/tests/acceptance"]
created_at: "2026-09-14"
updated_at: "2026-09-15"
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

- [ ] `node` 下四个包各自的对外面不超过其职责；模块内 HTTP 客户端实现只剩两个（sandbox 客户端与
      节点控制面客户端）。
- [ ] 代码中不再出现宿主安装路径、sandbox 端点地址与限额条目数量的字面量。
- [ ] 部署校验对「清单声明未被校验」与「被校验项无声明」两类情况分别报错，并指出是哪一条；
      两类情况各有一个测试用例。
- [ ] 人为把 HTTP 写期限配成小于会话期限，sandbox 拒绝启动并说明是哪两项冲突；judge 侧调用期限
      与 sandbox 端总预算的关系同样断言。
- [ ] 节点控制协议与安装协议的线格式与基线 `a611be3` 相同。

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
- 2026-09-15：状态变更：todo → ready。原因：前置 TASK-127 已完成
