---
id: "PLAN-039"
type: "plan"
title: "分离CI软件包准备与回归测试"
status: "checked"
work: "WORK-055"
owners: ["codex/root"]
depends_on: ["IMPROVEMENT-005", "DESIGN-049", "DECISION-033"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# PLAN-039：先实现包准备，再接入CI

## 目标

满足IMPROVEMENT-005；修改仅限TASK-119/120明确路径。新WORK承接WORK-054的失败证据，不篡改其验收状态，不提前开启WORK-049重构。

## 阶段与顺序

1. 本轮完成材料与结构检查；用户审核并签署WORK-055意图闸、允许实施。
2. TASK-119：实现CI curl编排、完整包集校验及prepare显式包输入；先受控失败、恢复、文件污染与取消测试，再基础回归。
3. TASK-120依赖TASK-119：修改ci.yml统一准备/缓存/artifact，新增独立冷workflow及路由检查，更新harness身份和README。
4. 独立复核文件信任边界、HTTP重取与workflow权限。新工作委派及提交推送按用户单独授权执行，不套用WORK-054旧批次授权。
5. 发布后在一次性Ubuntu24 amd64 VM：同实现一次无缓存和一次有缓存日常workflow，两次独立冷检查；保留所有失败，不追加刷绿。首次新精确键未命中可作冷准备样本，否则显式绕过缓存并记录。
6. 逐项记录结果，用户验收后将依赖准备证据交WORK-050。业务和proc检查红色时只能报告本工作准备结果，不称完整CI通过。

## 并行与依赖

TASK-119再TASK-120串行实施，独立复核在实现后进行。WORK-054保持历史状态，不伪造完成；本WORK验收可作为TASK-112依赖获取的新交接依据，届时先更新WORK-050计划的旧交接约束再继续业务修复。

## 验证

受控HTTP/TLS、模拟慢流与真实子进程取消，测试次数/单次与总期限；缓存污染及校验后替换负例；workflow路由/依赖/权限/摘要检查。基础命令python3 -B deploy/sandbox-linux/ci/basic.py。远端核对冷/热源请求数、消费者三份当前SHA构建身份、包锁/rootfs摘要和清理。公网结果不以mock替代。

## 迁移与交付

只GitHub一次性VM，不安装个人或原服务器配置，不变更部署节点。新包准备15分钟，原测试job上限保持；增加独立冷检查的资源成本在本次意图审核中明确接受。

## 风险

新网络路径效果未知，缓存服务失败仍会阻断。回退本批workflow/prepare接线恢复每job下载，不删除缓存或用户资源；原600秒配置保留。失败后不自动换源、加预算或扩大业务源码边界。

## 改动区域

TASK-119限定包获取/prepare与测试；TASK-120限定两份workflow、工作流测试、report身份与README。未授权apps或安装器修改。

## 回退

恢复原workflow及prepare调用；不用删除远端缓存。用户服务与数据不涉及迁移。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：完整方案与任务边界已整理，供人工意图审核，未实施
- 2026-09-11：结构与内容校验通过，由工具置为 checked。
