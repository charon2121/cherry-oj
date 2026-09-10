---
id: "TASK-106"
type: "task"
title: "整理 Judge 阅读路径与配置及限额表达"
status: "todo"
work: "WORK-049"
owners: ["codex/root"]
depends_on: ["CHANGE-013", "DESIGN-043", "DECISION-027", "PLAN-033", "TASK-105"]
related: []
implements: ["CHANGE-013#REQ-001", "CHANGE-013#REQ-002", "CHANGE-013#REQ-003", "CHANGE-013#REQ-005", "CHANGE-013#REQ-006", "CHANGE-013#REQ-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "docs/engineering", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "apps/judge-engine", "contracts", "deploy/sandbox-linux"]
write_paths: ["apps/judge-engine/internal/judge", "apps/judge-engine/internal/config", "apps/judge-engine/internal/contract", "apps/judge-engine/cmd/judge", "apps/judge-engine/README.md", "development/works/WORK-049"]
forbidden_paths: ["contracts", "apps/server", "apps/web", "deploy", "docs/engineering", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "development/works/WORK-048"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-106：整理 Judge 阅读路径与配置及限额表达

## 任务目标

使判题主线清楚地调用单次命令执行，node 旁路独立可读，共享状态与数字表达不依赖读者记忆。

## 依据

以 [DESIGN-043](30-design-DESIGN-043.md) 的主阅读路线、阶段表达与数字处理方案为准；对应要求见 implements。

## 可查看范围

以 front matter 的 read_paths 为准。contracts 与部署材料只用于理解既有行为，不授权修改或部署。

## 可修改范围

以 front matter 的 write_paths 为准。仅限本工作等价重构需要的代码组织、必要测试与说明；不能借目录范围扩大需求。

## 禁止修改

以 front matter 的 forbidden_paths 为准；此外不能改变外部协议、固定预算、配置默认值与校验行为、进程/权限模型。

## 依赖

TASK-105 已明确单命令执行的入口与返回语义；WORK-048 未解决的环境探测问题不得在本任务夹带修复。
获得文档通过及后续实施授权、意图闸由人签署后才可推进；当前 todo 不代表可执行。

## 产出

flow/node/config/contract 限额映射的等价组织调整、必要回归测试、相关阅读说明。保留已知行为问题，单独记录范围升级候选。

## 完成标准

- [ ] flow 的主流程可顺读，每请求状态独立，不再传递一长串重复上下文。
- [ ] node 身份、控制面心跳、HTTP 入口与安装事务按职责定位。
- [ ] 配置移动保持两进程既有校验和默认值行为；不趁机修改跨服务校验。
- [ ] Limits 字段索引、位语义、特殊零值与数字归属明确，JSON 编解码兼容。
- [ ] compile/run/verdict/输出可见性与既有错误行为无意外变化。

## 验证

相关包 go vet、go test -race，复用契约、limits、flow、node 和 config 既有用例；仅补充有意义的映射/独立状态不变量测试。走一次编译语言和一种无需编译语言的代码路径，明确 source/artifact 归属。

## 风险

把共享参数收进对象可能造成跨请求污染；改变限额索引会影响零值/缺省；为改善日志顺手改变行为不在本任务范围。

## 执行记录

- 2026-09-10：仅创建任务与边界；未执行实施任务。
