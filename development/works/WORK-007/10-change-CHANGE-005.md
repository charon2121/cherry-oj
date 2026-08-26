---
id: "CHANGE-005"
type: "change"
title: "校正全局 PRD 与当前 MVP 基线的漂移"
status: "approved"
work: "WORK-007"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# CHANGE-005：校正全局 PRD 与当前 MVP 基线的漂移

## 当前状态

`docs/product.md` 有 632 行，包含完整 OJ 愿景、P0/P1/P2 功能清单、题目工厂、Agent 工作台、成功
指标、路线、旧架构缺口和未来问题。它仍把判题基础设施称为“当前方向”，把 ProblemVersion、
JudgeInput、环境标定和 contracts 更新描述成未来工作，同时把当前 MVP 与后续运营能力都标成 P0。

## 当前问题

全局产品真源无法清楚回答“现在要交付什么”：

- 已落地的架构基线仍被写成未来影响，形成事实漂移；
- C++ ACM 首个纵向切片、ACM/CORE 完整 MVP、题目工厂与 Agent 的阶段边界混乱；
- P1/P2 和未来选择留在全局 docs，与 `development/` 承载未决需求的规则冲突；
- 登录方式、WA 明细和发布环境仍在 WORK-002 阻塞，PRD 却使用了可能被误读为已确认的宽泛需求。

## 目标状态

- REQ-001：PRD 只描述稳定产品定位、当前 MVP 边界、核心用户流程、产品规则、质量边界和已确认演进顺序。
- REQ-002：明确第一交付切片是 C++ ACM，完整 MVP 再加入 C++ CORE；交付状态只在 development 中记录。
- REQ-003：运营侧确定性题目流水线与受控 Agent 保留为长期方向，不列为当前 MVP/P0。
- REQ-004：删除“当前尚未有版本/契约”等已过期叙述，以现行 architecture、data-model 和 contracts 为实现边界。
- REQ-005：未确认的产品选择必须指向对应 WORK，不得在全局 PRD 中代签。

## 不变条件

- REQ-006：不改变 cherry-oj 同时服务答题用户和题库运营者的长期定位。
- REQ-007：不改变单工作空间、USER/ADMIN、ACM/CORE、C++ 优先、不可变版本、可追溯提交、绝对限制和
  Agent 不属于 MVP 等已确认规则。
- REQ-008：不修改任何运行时代码、契约、数据模型、接口和 WORK-002 的 blocking 项。

## 影响范围

`docs/product.md` 全文；只读核对 `docs/architecture.md`、`docs/data-model.md`、`docs/backend.md`、
`docs/frontend.md`、`contracts/` 和 WORK-002。过程记录位于 WORK-007。

## 风险

主要风险是把“产品愿景”误当“当前 MVP”，或反向把“当前实现”误当“产品规则”。以三层边界（稳定
定位 / 当前 MVP / 条件触发的长期方向）重排，并逐条核对不变条件。

## 回归检查

- AC-001：PRD 不再出现“判题基础设施（当前方向）”和把既有版本/契约描述成未来缺口的段落。
- AC-002：PRD 明确 C++ ACM 首个切片与 ACM+CORE 完整 MVP 的区别，并链接 WORK-002 的未决项。
- AC-003：现行九项产品不变量与 data-model §0/§14 及 contracts v2 一致。
- AC-004：`scripts/work check`、`python3 scripts/docs_test.py` 和 `git diff --check` 通过。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：PRD 漂移、目标边界、不变条件和验收检查已经明确
