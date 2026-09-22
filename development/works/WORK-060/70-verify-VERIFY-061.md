---
id: "VERIFY-061"
type: "verify"
title: "收敛个人 Agent 开发的审核材料与默认文档"
status: "review"
work: "WORK-060"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: ["WORK-060#AC-001", "WORK-060#AC-002", "WORK-060#AC-003", "WORK-060#AC-004"]
tags: []
result: "pass"
created_at: "2026-09-22"
updated_at: "2026-09-22"
format: "compact"
---

# VERIFY-061：开发文档系统精简格式验证

## 实际结果

新工作默认生成主文档与证据两份 Markdown，风险检查仍保留。board 直接展示变化、边界、取舍、未知和验收；证据写完后展示交付结果。context 支持直接用 WORK 交接，无需创建空 TASK。

## 承诺差异

沿用 00-work.md 与 70-verify-VERIFY-xxx.md 命名，未改成示意方案中的 work.md/evidence.md，以保留既有编号与引用方式。旧工作继续按原格式校验，不批量迁移。
本轮未取消两道闸、未增加常设实施授权；本工作按用户明确的直接改动授权实施，正式签署记录仍留给人。

## 验证情况

51 项端到端测试通过，包含原格式 45 项回归与精简格式 6 项测试。真实仓库 506 份工作文档校验通过；582 份 Markdown 入口与本地链接在临时 Git 索引中校验通过。未运行产品服务、浏览器或部署测试，因为本次只改开发工具和规范。

## 遗留问题

原 WORK-033 已具备历史验收事实但进度仍为 implemented，工具继续给出原有提示，本次不改历史状态。
真正的阅读负担改善需要在后续三类实际需求中试用；测试只能证明工具行为，不能证明用户认真理解了材料。
初次验证时新文件未暂存，标准链接校验报告 WORK-060 未被跟踪。2026-09-22 用户授权提交后已将本工作单独暂存，该问题已消除；独立暂存快照的 582 份 Markdown 链接校验通过。

## 检查与结果

环境：本机 Python 3.12，所有生命周期测试使用 TemporaryDirectory，不对真实仓库签闸。

- AC-001：`python3 scripts/work_test.py`，51 tests / OK。默认高风险系统级 product 仍仅两份 Markdown，并保留 independent-review、rollback、cross-module-regression。
- AC-002：精简 board 默认内容、--all 详细视图、context WORK 的边界输出断言通过；实际运行 `scripts/work board WORK-060` 核对主文档原文展示。
- AC-003：无 TASK 的完整生命周期、撤回验收、重新签署、重建流程、未授权开工、缺少路径、失败检查、缺少 AC 证据、失败 VERIFY、附件 checked 语义、TASK 越界与未完成任务阻止 implemented，均由临时目录测试覆盖。
- AC-004：`scripts/work check`，506 份开发文档通过，仅 WORK-033 原有提示。原分层测试显式使用 --format layered；真实历史 WORK-001 至 WORK-059 未改动。`git diff --check` 通过。
- 链接：临时 GIT_INDEX_FILE、GIT_OBJECT_DIRECTORY 及只读 GIT_ALTERNATE_OBJECT_DIRECTORIES，read-tree HEAD 后将两份新模板和 WORK-060 三个文件 intent-to-add，再运行 `python3 scripts/docs_test.py`，582 份入口与本地链接有效。临时目录退出后删除，无真实暂存、提交或推送。
- 范围复核：仅修改 AGENTS.md、开发文档规则/模板/Schema/编号/总览、本工作记录及 scripts/work、scripts/work_test.py。工作区原有 deploy 两处修改与测试资产删除未触碰。

## 变更记录

- 2026-09-22：状态变更：draft → review。原因：51项测试与新旧格式、真实文档及链接校验通过，限制已记录
- 2026-09-22：提交前重跑 51 项工具测试与 3 项链接校验器测试，均通过；导出独立暂存快照，506 份工作文档和 582 份 Markdown 通过校验。TASK-133 编号增量与 WORK-058 修复留给独立提交。
