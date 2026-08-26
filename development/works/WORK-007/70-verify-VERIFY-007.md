---
id: "VERIFY-007"
type: "verify"
title: "校正全局 PRD 与当前 MVP 基线的漂移"
status: "approved"
work: "WORK-007"
owners: ["codex/root"]
depends_on: ["TASK-007"]
related: []
implements: []
verifies: ["CHANGE-005", "TASK-007"]
tags: []
result: "pass"
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# VERIFY-007：校正全局 PRD 与当前 MVP 基线的漂移

## 验证对象

CHANGE-005 的 PRD 职责校正、TASK-007 的全文重写，以及与现行全局技术基线和 WORK-002 的兼容性。

## 对应要求

- CHANGE-005 / AC-001：移除过期“当前方向”和未来架构缺口表述。
- CHANGE-005 / AC-002：区分首个 C++ ACM 切片、完整 ACM/CORE MVP 与长期方向。
- CHANGE-005 / AC-003：保持角色、模式、版本、快照、限制、环境、异步判题、边界和 Agent 非 MVP 规则。
- CHANGE-005 / AC-004：工作项、链接和差异检查通过。
- TASK-007：完成全部五项完成标准，且不越过写入范围。

## 检查与结果

- macOS / Python 3.12：`python3 scripts/work_test.py`，19 个测试通过。
- macOS：`scripts/work check`，49 份开发文档通过，0 个进行中提示。
- macOS / Python 3.12：`python3 scripts/docs_test_test.py`，3 个链接校验器测试通过。
- macOS：`python3 scripts/docs_test.py`，77 份 Markdown 入口和本地链接通过。
- `git diff --check`：通过，无行尾空白或冲突标记。
- 漂移拒绝检查：旧“判题基础设施（当前方向）”“当前测试数据按 problemId”“产品化后需要版本”与
  “未来由 server 解析”等表述均不存在。
- 产品不变量检查：C++ ACM/CORE、ProblemVersion、JudgeInput、LanguageCalibration、环境指纹、绝对限制、
  Kafka、轮询、WORK-002 入口和 Agent 非 MVP 均存在。
- 影响检查：仓库无 `product.md#...` 章节锚点引用；`docs/product.md` 路径保持不变。

## 未通过项

无。

## 范围检查

语义变更仅在 `docs/product.md`；其它修改均为 WORK-007 过程文档和单调 ID 索引。没有修改 apps、
contracts、其它全局技术文档或 WORK-002，符合 TASK-007 边界。

## 遗留问题

WORK-002 的账号开通方式、WA 信息公开粒度和内部发布环境口径仍为 blocking，按设计保留。

## 剩余风险

PRD 不再作为未来 backlog。后续若有人直接把未确认 P1/P2 清单写回全局文档，可能重新产生漂移；
MEMORY-005 记录长期防线。

## 结论

通过。PRD 已恢复为稳定产品合同，当前 MVP、长期方向、非目标和 development 待决边界清晰。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：工作项、链接、差异、漂移关键词、产品不变量和影响范围检查全部通过
