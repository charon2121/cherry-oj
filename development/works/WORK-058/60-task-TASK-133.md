---
id: "TASK-133"
type: "task"
title: "修复独立复核发现的 R1–R10"
status: "done"
work: "WORK-058"
owners: ["team/judge-engine"]
depends_on: ["DESIGN-051"]
related: ["VERIFY-059"]
implements: ["CHANGE-014#REQ-003", "CHANGE-014#REQ-005", "CHANGE-014#REQ-008", "CHANGE-014#REQ-010", "CHANGE-014#REQ-013"]
verifies: []
tags: []
read_paths: ["AGENTS.md", "docs", "development", "apps/judge-engine", "contracts", "deploy/sandbox-linux"]
write_paths: ["apps/judge-engine", "docs/engine.md", "development/works/WORK-058", "deploy/sandbox-linux/tests", "deploy/sandbox-linux/ci"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "scripts", ".github"]
created_at: "2026-09-22"
updated_at: "2026-09-22"
---

# TASK-133：修复独立复核发现的 R1–R10

## 任务目标

修复 VERIFY-059 独立复核的 R1–R10，补齐原方案未落实的边界，并用原有反例与新增回归证明行为。

## 依据

CHANGE-014、DESIGN-051、PLAN-041 与 VERIFY-059 的 R1–R10。用户在阅读复核结论后明确要求
“修复这些问题”，本任务承接该实施授权，不另立工作或代签历史人工闸。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

deploy 范围仅限 R5 的故障采样与对应纯 Python 回归；允许将该回归接入现有 basic.ci 测试发现，
不改变必跑用例数量、报告 schema 或放宽任何断言。WORK-059 的 business 日志增量保持原状。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- R1/R2：清理错误正确传播；取消回调不读取尚未初始化的停止函数。
- R3/R4：devhost 回收未确认即停单；runner 持有执行依赖，pool 只消费执行接口。
- R5/R6：采样只容忍明确的消失 errno；judge 主动取消自身心跳生命周期再等待退出。
- R7：调用超时纳入显式指纹，并对请求墙钟与调用期限冲突提前拒绝；修正总预算的过度声明。
- R8/R9：共享客户端提供有界且禁止重定向的探测调用，完整解码版本与探测响应。
- R10：文档明确当前原生节点模式同机部署；记录修复后的验证与独立复审。

## 完成标准

- [x] R1–R10 均有代码或文档处置与针对性证据。
- [x] 原始生命周期、探测与身份反例在修复后通过，且正常/拒绝两侧均有断言。
- [x] Go race 全量、双平台 vet、Python 定向回归与文档检查完成，失败或环境限制单独记录。
- [x] 独立复审记录完整；真实 Linux 隔离未执行时不将历史 CI 记为当前候选通过。

## 验证

运行 `go test -race -count=1 ./...`、本机与 linux/amd64 的 `go vet ./...`，以及 fault_batch 的
errno 注入测试、`scripts/work check` 和文档链接检查。先复现关键回归，再修复并比较。

## 风险

执行生命周期修复必须保持错误链和产物回滚顺序。pool 内部接口变化需同步全部调用与测试。
超时重新进入指纹会轮换身份，已在 DECISION-035 的全量轮换授权内；本任务不切换 ACTIVE。
排队、收尾和网络耗时不能由当前 judge 配置完整推导，不假造全链路预算保证，不新增跨服务字段。

## 执行记录

- 2026-09-22：创建任务。
- 2026-09-22：状态变更：todo → ready。原因：复核反例、修复方案和路径边界已明确，用户已要求实施。
- 2026-09-22：状态变更：ready → doing。原因：开始修复 R1–R10。
- 2026-09-22：R1–R10 完成；原始生命周期反例先失败，修复后通过。Go 全量 race、双平台 vet、
  Python basic 五项通过。三位独立 Agent 复审通过，明细见 VERIFY-059。真实 Linux 与业务 CI
  未执行，工作仍保留 partial；没有提交、推送、改动其他工作的实施文件或签闸。
- 2026-09-22：状态变更：doing → done。原因：R1–R10 修复、本地验证和独立复审完成；真实 Linux CI 单独保留未执行记录。
