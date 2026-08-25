---
id: "PLAN-006"
type: "plan"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "approved"
work: "WORK-008"
owners: ["codex/root"]
depends_on: ["CAPABILITY-001", "EXPERIENCE-002", "DESIGN-006"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# PLAN-006：建立 Web 到 Gateway 的 REST 基础连通模块

## 目标

按 DESIGN-006 交付可测试、可构建、可本地联调的 Web → Gateway REST 基础链路。

## 改动区域

Gateway 新增 status API 与接口测试，并更新 server 工具链现状；Web 新增共享 API client、status
feature、首页组合与组件测试。开发工作项只维护 WORK-008 自身文档。

## 阶段与顺序

1. 先冻结 endpoint 与 JSON 形状并写 Gateway controller/test。
2. 实现前端 unknown JSON 请求边界、decoder 与 Query options。
3. 在首页组合三态状态面板并用 MSW 覆盖成功、失败和恢复。
4. 运行格式、lint、类型、测试、构建与实际 `curl`，再记录验证证据。

## 并行与依赖

后端接口与前端共享请求层可独立编写，但前端 decoder 和测试必须以冻结响应为依据。首页接线依赖
feature 完成；验证依赖两侧实现完成。

## 迁移与上线

无数据迁移。代码可随 Gateway 与 Web 常规制品发布；本任务只完成仓库实现与本地验证，不代签部署、
上线和线上观察阶段。

## 风险

主要风险是契约漂移、错误状态无恢复入口以及将 Gateway 连通误报为全系统健康；分别用双侧测试、
显式重试和限定文案控制。

## 验证

后端运行 Maven `clean verify`；前端运行 `npm run check`、`npm run build` 和 Playwright smoke；启动
Gateway 后实际 `curl /api/status`。最后运行 `scripts/work check` 检查工作项链路与范围。

## 回退

删除新增 controller、feature 和请求层，首页恢复静态占位即可；没有数据库、消息或配置需要回退。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：实施顺序、验证与回退路径已明确
