---
id: "PLAN-005"
type: "plan"
title: "校正全局 PRD 与当前 MVP 基线的漂移"
status: "approved"
work: "WORK-007"
owners: ["codex/root"]
depends_on: ["CHANGE-005", "DESIGN-005", "DECISION-005"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# PLAN-005：校正全局 PRD 与当前 MVP 基线的漂移

## 目标

按 DECISION-005 把 `docs/product.md` 重写成稳定产品合同，并用跨文档核对证明没有修改既有产品边界。

## 改动区域

写入 `docs/product.md` 和 WORK-007；只读 architecture、data-model、backend、frontend、contracts 与
WORK-002。不修改业务代码和其它全局技术文档。

## 阶段与顺序

1. 建立旧 PRD 漂移清单和不可变规则映射。
2. 重写产品定位、角色、MVP、概念、流程、规则、质量边界、非目标和长期方向。
3. 与 WORK-002 核对首个 C++ ACM 切片和三个 blocking 问题。
4. 与 data-model §0/§14、architecture 和 contracts v2 核对术语与边界。
5. 运行文档、工作项、链接、关键词与 diff 检查，完成复核和验证记录。

## 并行与依赖

正文重写依赖边界映射，不能并行。链接检查、关键词检查和人工差异复核可以在正文完成后并行执行。

## 迁移与上线

纯全局文档校正，不需要业务部署或数据迁移。合入 Git 即完成文档交付，release/observe 阶段可说明
不适用并由校验结果完成观察。

## 风险

主要风险是漏掉确认规则、把未来方向写成当前承诺、擅自解决未决问题。使用逐条不变量清单和明确的
“当前非目标/待决入口”控制。

## 验证

检查 `scripts/work check`、`python3 scripts/work_test.py`、`python3 scripts/docs_test_test.py`、
`python3 scripts/docs_test.py`、`git diff --check`；使用 `rg` 拒绝旧“当前方向/未来补版本契约”等表述，
并人工核对 product 与 data-model、WORK-002 的不变量。

## 回退

单文件文档变更可直接回退提交。若重写导致关键规则无法追溯，恢复旧 PRD 后改为分章节渐进校正。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：重写顺序、核对真源、验证命令和回退方式已经明确
