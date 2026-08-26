---
id: "EXPERIENCE-004"
type: "experience"
title: "建立跨语言可观测性基础设施"
status: "approved"
work: "WORK-010"
owners: ["codex/root"]
depends_on: ["CAPABILITY-003"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# EXPERIENCE-004：建立跨语言可观测性基础设施

## 体验类型

开发体验与运维体验。它不新增产品页面；用户只继续看到可安全提供给支持人员的 public request ID。

## 入口与主流程

开发者用一条可选 Compose profile 启动 collector/reference backend，再按现有方式启动 Java 服务或
judge/sandbox。服务默认输出 JSON 日志；配置 OTLP endpoint 后自动发送 Metrics/Trace，不需要在业务
handler 手写 exporter。

排障主流程固定为：从用户提供的 `req_...` 或告警中的 service/route/verdict 开始，在日志中找到完成
事件和 traceId，跳到整条 Trace 判断慢/错的边界，再用该服务的 RED、runtime 和 judge/sandbox 资源
指标判断是单点异常还是持续退化。没有 public request ID 的异步判题从 submissionId/taskId 的日志
字段或事件 traceId 起步。

## 异常状态

- 没有数据：UI/查询说明当前时间窗、服务、采样和 exporter 配置，不把“无 Trace”解释为请求未发生。
- collector/backend 不可用：服务保持健康，日志出现限频的 exporter 状态，collector 恢复后新遥测
  自动恢复；不承诺无限补发。
- 非法/超长 Trace header：创建新 Trace，记录低基数原因，不回显原始 header。
- 查询无权限：collector 与 Grafana 只在运维网络开放；产品 Gateway 不代理管理端点。
- 数据量过高：先降低采样/日志级别或停用高成本自定义 span，不删除 request 完成日志和核心 Metrics。

## 交互与文案

日志 message 使用可读短句，稳定机器字段使用英文 lowerCamelCase；同一事件名跨语言一致。dashboard
至少提供 service、environment、route、status/verdict 与时间窗筛选，不要求值班人员了解 Java/Go
内部 logger 名称。所有“跳到 Trace/日志”的链接使用 traceId 字段，不依赖文本搜索 message。

## 可访问性

不开发新的仓库 UI；本地使用 Grafana 自带界面，其无障碍能力不在本工作定制范围。仓库文档和 smoke
命令必须纯文本可执行，不能只交付颜色图表或截图；告警/说明同时给出状态文字和查询条件。

## 调试与恢复

README 给出最短路径：启动/停止栈、发送样例请求、按 requestId/traceId 查询、确认 Metrics、模拟
collector 不可用和清理本地数据。应用侧通过关闭 exporter 或回退 observability 配置恢复，日志仍留在
stdout；本地栈使用独立 profile/volume，停止它不会停止业务容器。字段或传播语义需要改变时先回到
DESIGN-008/DECISION-007，不能在单个服务里加私有 header 或 label。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：开发与运维的接入、排障、异常和恢复流程已形成
- 2026-08-25：状态变更：review → approved。原因：体验要求与已批准能力一致，不需要新增产品 UI 或人工产品判断
