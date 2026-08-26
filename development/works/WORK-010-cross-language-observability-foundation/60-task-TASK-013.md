---
id: "TASK-013"
type: "task"
title: "交付本地采集栈与跨运行时验收"
status: "done"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["CAPABILITY-003", "DESIGN-008", "DECISION-007", "PLAN-008", "TASK-010"]
related: ["TASK-011", "TASK-012"]
implements: ["CAPABILITY-003#REQ-007", "CAPABILITY-003#REQ-008", "CAPABILITY-003#REQ-009"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "compose.yaml", "contracts", "apps/server", "apps/judge-engine", "docs/architecture.md", "docs/backend.md", "development/works/WORK-010-cross-language-observability-foundation"]
write_paths: ["compose.yaml", "observability", "scripts", "apps/server/README.md", "apps/server/TOOLCHAIN.md", "apps/judge-engine/config.example.yaml", "development/works/WORK-010-cross-language-observability-foundation"]
forbidden_paths: ["apps/server/gateway-service/src/main/java", "apps/server/user-service/src/main/java", "apps/server/problem-service/src/main/java", "apps/server/submission-service/src/main/java", "apps/server/judging-service/src/main/java", "apps/judge-engine/cmd", "apps/judge-engine/internal", "apps/web", "contracts", "docs/product.md", "development/works/WORK-002-cpp-acm-loop"]
created_at: "2026-08-25"
updated_at: "2026-08-26"
---




# TASK-013：交付本地采集栈与跨运行时验收

## 任务目标

在不修改应用源码的前提下，交付可选的 Alloy + `grafana/otel-lgtm` 本地 reference stack、最小
dashboard/query、配置说明和机器可重复的跨 Java/Go smoke，证明日志/Trace/Metrics 能关联且采集故障
不会影响业务。

## 依据

只依据 approved 的 CAPABILITY-003、DESIGN-008、DECISION-007、PLAN-008 和已完成 TASK-010，落实
REQ-007～REQ-009。TASK-011/012 是关联输入而非 ready 门禁，以满足工作项工具“全部 TASK 先 ready”的
流程约束；PLAN-008 仍规定本任务必须等两者实际完成后才进入 doing。此任务只做部署与验收，发现应用
缺陷回到 TASK-011/012，不越界直接修。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

`observability/` 下的 Alloy/reference backend/Grafana provisioning/dashboard、根 Compose 可选 profile、
Java host log 的 opt-in 本地采集办法、smoke 脚本和 README/TOOLCHAIN/config example。所有镜像版本固定，
管理端口仅绑定 loopback/内部网络，本地数据使用独立可清理 volume。

## 完成标准

- [x] `docker compose --profile observability config/up` 可启动，profile 未启用时现有 judge/sandbox 拓扑
  与资源限制不变；停止 reference stack 不停止业务容器。
- [x] Alloy 接收 Java/Go OTLP Metrics/Trace，收集 Go container JSON stdout，并能按文档采集 host Java
  的 opt-in JSON file；Docker socket 权限与生产替代方案明确。
- [x] Grafana 数据源和最小 dashboard 自动 provisioning，可查询 service RED/runtime、judge verdict/
  duration、sandbox status/resource 与 collector 自监控。
- [x] smoke 发送至少一条 Gateway 请求和一条 judge→sandbox 请求，以机器查询断言 requestId 日志、
  trace parent-child、标准/领域 Metrics，而非只要求人工截图。
- [x] 停止/限速 collector 后重发请求，HTTP/verdict 不变且应用队列/资源有界；恢复后新遥测可见。
- [x] 管理端口不经 `/api` 暴露，日志/Trace 无敏感 fixture，metric/labels 符合 allowlist 和 series 预算。

## 验证

运行 `docker compose --profile observability config`、容器 health 与 smoke 脚本；通过 Loki/Tempo/Metrics
HTTP query API 做机器断言。检查端口绑定、网络、read-only mounts、镜像版本和 volume；分别在 stack
正常/停止/恢复下记录响应、verdict、查询与资源事实，作为 VERIFY-010 输入。

## 风险

Docker socket 即使只读也有高权限，本地栈资源占用也可能影响判题基准；必须 opt-in、说明风险并给出
干净停止/清理命令。需要容器化五个 Java 服务、修改应用源码、设计生产 HA/留存/告警阈值或暴露公网
端口时另建工作项，不能塞进本 smoke 任务。

## 执行记录

- 2026-08-25：创建任务。
- 2026-08-26：状态变更：todo → ready。原因：采集栈任务边界完整；按 PLAN-008 保持到 Java/Go 完成后再进入 doing
- 2026-08-26：状态变更：ready → doing。原因：TASK-011 与 TASK-012 均已完成并通过各自全量验证，按 PLAN-008 开始交付 Alloy/reference stack 与跨运行时 smoke
- 2026-08-26：新增固定版本 Alloy/otel-lgtm profile、独立 loopback 管理端口与数据卷、受限 Docker
  discovery、host Java file tail、OTLP memory/batch/queue/retry、Grafana dashboard 和自动 smoke；Alloy
  v1.19.0 配置验证、Compose profile 展开和 dashboard JSON 校验通过，reference stack 实际 healthy。
- 2026-08-26：`scripts/observability_smoke.py --host-go` 通过真实 Gateway 与 C++ AC 判题，机器断言 Loki
  requestId→traceId、Tempo judge→sandbox 父子图、标准/领域/collector Metrics、低基数与敏感 canary、
  dashboard provisioning、Alloy 停止时业务不变及恢复后新遥测；真实 Go JSON 另经临时容器 stdout 被
  Docker discovery 收集。默认产品容器构建连续三次被 Docker Hub token endpoint 网络超时阻断，脚本
  正确失败且未把外部依赖故障伪装为通过。
- 2026-08-26：从 Docker Hub pull-through cache 预取并本地标记相同的 Go 1.26.3 bookworm 与 Debian
  bookworm-slim 基础镜像后，未经修改的 `scripts/observability_smoke.py` 默认路径通过：Compose 实际
  构建并启动 judge/sandbox 产品容器，Alloy 从其 Docker stdout 收集真实 JSON 日志，完整关联、指标、
  dashboard、停采 fail-open 与恢复验证全绿。
- 2026-08-26：状态变更：doing → done。原因：Alloy/otel-lgtm reference stack、7-panel dashboard 与跨 Java/Go smoke 已交付；host fallback 全链路及真实 Go JSON Docker stdout discovery、collector fail-open 全部通过，默认产品镜像构建仅受 Docker Hub 外部超时阻断
