---
id: "PLAN-009"
type: "plan"
title: "撤回可观测性实现并保留追溯契约"
status: "approved"
work: "WORK-012"
owners: ["codex/root"]
depends_on: ["CHANGE-007", "DESIGN-009", "DECISION-008"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---


# PLAN-009：撤回可观测性实现并保留追溯契约

## 目标

按保留清单撤回运行时观测与采集栈，保住 Request ID/W3C Trace Context 契约并完成跨模块回归。

## 改动区域

Java/Go 应用与依赖、根 Compose、observability/scripts、contracts/docs、WORK-010/011 状态和本 WORK。

## 阶段与顺序

1. 停止并移除观测容器，不删除 volume或业务容器。2. tracked 应用/Compose 恢复 HEAD，删除新增观测
文件。3. 保留 contracts 追溯字段，收敛 docs。4. supersede 原设计。5. 全量验证和范围复核。

## 并行与依赖

批量回退存在共享工作树依赖，顺序执行，不并行改文件。

## 迁移与交付

尚未提交/发布，无线上迁移或发布动作。本地容器清理只针对 Alloy/otel-lgtm，volume 保留。

## 风险

误回退 WORK-009 Request ID 或误保留 OTel 依赖。通过 HEAD 精确恢复、保留清单和 `rg` 双向检查控制。

## 验证

contracts tests、Java `./mvnw clean verify`、Go gofmt/vet/build/race、Compose config/build、work check、
git diff/check；检查无 Alloy/Grafana/OTel/Metrics 残留且追溯契约仍在。

## 回退

本次是对未提交实现的回退；若发现误删，用原 WORK-010 工作树历史或保留 volume 辅助恢复，再按具体文件
修正，不能重新整体启用已否决方案。

## 变更记录

- 2026-08-26：状态变更：draft → approved。原因：实施顺序、保留清单、容器清理和跨模块验证可执行
