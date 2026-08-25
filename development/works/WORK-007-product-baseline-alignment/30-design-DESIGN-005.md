---
id: "DESIGN-005"
type: "design"
title: "校正全局 PRD 与当前 MVP 基线的漂移"
status: "approved"
work: "WORK-007"
owners: ["codex/root"]
depends_on: ["CHANGE-005"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-25"
updated_at: "2026-08-25"
---


# DESIGN-005：校正全局 PRD 与当前 MVP 基线的漂移

## 背景

CHANGE-005 已确认问题不是单个错字，而是文档信息类型混杂：稳定产品事实、未来设想、当前进度和
未决问题同时进入全局 PRD，时间一久必然与 architecture、data-model 和 development 漂移。

## 目标与限制

目标是重新建立 PRD 的产品合同角色，不借重写改变既有产品决定。限制包括：不回答 WORK-002 的三个
blocking 问题，不根据代码完成度缩小长期愿景，不修改技术真源，不把未来 backlog 留在全局 docs。

## 整体方案

把 PRD 重构为九个稳定主题：

1. 文档边界与产品定位；
2. 用户、管理员及核心任务；
3. 当前 MVP 定义与交付切片；
4. 核心产品概念；
5. 用户与管理员流程；
6. 产品规则与验收基线；
7. 非功能和安全边界；
8. 当前非目标；
9. 已确认演进方向与待决问题入口。

具体功能的 P1/P2 清单、开发状态和未决选项不再留在 PRD。长期题目工厂和 Agent 只保留方向、进入
条件和不可突破的安全原则。

## 模块与数据

只修改 Markdown。产品概念使用现行名称 Problem、ProblemVersion、ProblemVersionLanguage、
TestDataVersion、JudgeEnvironment、LanguageCalibration、Submission 和 JudgeInput；字段和服务所有权
仍链接 data-model/contracts，不在 PRD 复制表结构。

## 接口与状态

不改变运行时接口或状态机。PRD 只定义用户可观察的 Pending/Judging/Done 与 verdict 语义，以及已
发布版本不可变、正式提交冻结输入等产品规则；技术状态以契约和数据模型为准。

## 安全与失败

保留隐藏数据/模板/凭证不泄漏、系统故障不伪装成 WA、Web 不直连 judge/sandbox、不可信代码只进
sandbox 等边界。若核对发现现行全局文档互相冲突，停止并升级，而不是由 PRD 单方面覆盖。

## 监控与部署

无部署和运行时监控变化。文档验证检查链接、工作项结构、禁止漂移词、核心不变量和变更范围。

## 迁移与兼容

保留 `docs/product.md` 文件路径，仓库内也没有章节锚点引用，因此允许重写标题结构。旧 P1/P2 内容不
直接迁成新承诺；仍有价值的未来方向保留为条件触发，具体定义在启动对应 product WORK 时重建。

## 备选方案

备选一是只修改“当前方向”和第十一章的过期句子，改动小但继续保留 P0/未来能力混杂，漂移会复发。
备选二是把 PRD 拆成 MVP PRD 与长期愿景两份全局文档，边界清楚但增加两个可能再次不同步的真源。
选择在一份 PRD 中用明确层级分隔稳定定位、当前 MVP 和长期方向。

## 风险与重审条件

代价是丢失旧文档作为 backlog 的便利；Git 历史仍保存全文，真正进入开发的能力应建立 WORK。若未来
同时维护多个已发布产品版本或多个独立产品线，再考虑拆分版本化 PRD。

## 变更记录

- 2026-08-25：状态变更：draft → approved。原因：稳定产品合同的信息架构、迁移方式和兼容边界已经复核
