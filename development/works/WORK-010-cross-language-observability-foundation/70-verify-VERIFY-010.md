---
id: "VERIFY-010"
type: "verify"
title: "建立跨语言可观测性基础设施"
status: "approved"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["TASK-010", "TASK-011", "TASK-012", "TASK-013"]
related: []
implements: []
verifies: ["CAPABILITY-003", "TASK-010", "TASK-011", "TASK-012", "TASK-013"]
tags: []
result: "pass"
created_at: "2026-08-25"
updated_at: "2026-08-26"
---



# VERIFY-010：建立跨语言可观测性基础设施

## 验证对象

CAPABILITY-003 全部要求、四个实现任务、七个进程的三信号基线、现有 API/判题行为回归和本地采集栈。

## 对应要求

REQ-001～REQ-009，以及各 TASK 完成标准。重点验证 public request ID 与 Trace 分离、跨 HTTP 父子关系、
event traceId/header 语义、低基数、安全脱敏、collector fail-open 和跨模块查询闭环。

## 检查与结果

验证日期为 2026-08-26，本地使用 OpenJDK 21.0.12.1、Go 1.26.3 和 Docker Desktop：

- `python3 scripts/contracts_test.py`：8/8 通过；event traceId、HTTP/Kafka W3C header、RunSpec requestId
  语义、字段/label allowlist 与反例全部符合契约。`scripts/work check` 校验 78 份文档无提示，
  `git diff --check` 通过。
- `cd apps/server && ./mvnw -q clean verify`：聚合 Reactor 全绿；五个服务与共享 observability 模块的
  ECS JSON、public/internal request ID、WebFlux/MVC Trace、client propagation、Metrics、无 Collector
  启动和脱敏测试全部通过。跨栈 smoke 发现 Gateway 完成日志最初缺 traceId 后，按流程退回 TASK-011；
  修复采用 Reactor Context 中的 Micrometer Observation 显式取 span，并新增日志关联回归后再次全绿。
- `cd apps/judge-engine && gofmt -l .` 无输出，`go vet ./...`、`go build ./...`、
  `go test -race -count=1 ./...` 全部通过；in-memory exporter 与 httptest 覆盖 HTTP 父子 span、并发
  request/trace 隔离、judge/sandbox 领域指标、脱敏及 collector 不可达 fail-open。
- 默认和 observability profile 的 Compose JSON 展开通过；Alloy v1.19.0 `validate` 通过；固定的
  `grafana/otel-lgtm:0.31.0` 与 Alloy 实际 healthy，管理端口均绑定 `127.0.0.1`，reference backend
  不加入产品 backend network，Docker socket 与 Java 日志目录只读，四个服务都有 CPU/内存/PID 上限。
- 7-panel Grafana dashboard JSON 与自动 provisioning 通过，覆盖 HTTP RED、judge verdict、sandbox
  status、进程内存、collector 自监控与结构化日志。
- `scripts/observability_smoke.py` 默认容器路径通过：实际构建/启动 judge 与 sandbox，发送 Gateway status
  和触发 blob/run/delete 的真实 C++ AC 判题；Loki 由 requestId 找到 traceId，Tempo 断言
  judge HTTP → judge request → sandbox HTTP → sandbox run 父子图，Prometheus 断言标准/领域/collector
  Metrics 与 series label 预算，Grafana dashboard 可查。停止 Alloy 后 Gateway 仍 200、判题仍 AC、
  业务容器与 reference backend 继续运行；恢复后新请求遥测重新可见。
- 同一脚本的 `--host-go` fallback 也通过，并把真实 Go JSON 经一次性容器 stdout 重放，独立验证 Docker
  discovery；敏感源码 canary 不在 Loki/Tempo，request/trace/business ID 不在 Loki labels 或 Metrics。

## 未通过项

暂无。过程中 Go race 测试首次在受限沙箱绑定 `httptest` 环回端口失败，获批在本机环境用相同命令重跑
通过；Docker Hub token endpoint 连续超时后，从 pull-through cache 预取并本地标记完全相同的基础镜像，
未经修改的默认 Compose/Dockerfile 随后构建及 smoke 通过。两者都是已解决的执行环境限制。

## 范围检查

四个 TASK 的变更均在各自 write_paths 内。TASK-013 发现 Gateway 日志缺陷后没有越界修改应用，而是
将 TASK-011 从 done 退回 doing，修复并完成 Java 回归后再回到采集栈；未修改 Web、业务 API/DTO、
数据库、被阻塞的 WORK-002，也未实现尚不存在的 Kafka producer/consumer。

## 遗留问题

生产发布、HA、长期留存、容量、告警阈值、on-call、tail sampling、前端 RUM/profiling 与未来 Kafka
实际 instrumentation 不在当前工作范围。

## 剩余风险

即使本地 reference stack 全绿，生产容量、留存、告警阈值、HA、on-call 和真实采样成本仍需发布环境
证据，不能由本 VERIFY 代签。

## 结论

CAPABILITY-003、DECISION-007 五项边界及 TASK-010～TASK-013 的仓库内实现和本地跨运行时链路验证
通过。reference stack 只证明开发/演示/验收路径，不能代签生产发布和线上观察。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：契约、Java、Go、Compose、Alloy、Grafana 与默认/host 跨运行时 smoke 已有完整通过证据
- 2026-08-26：状态变更：review → approved。原因：所有仓库内完成标准及本地跨模块验证通过，生产发布与线上观察明确不由本验证代签
