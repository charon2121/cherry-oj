---
id: "DESIGN-037"
type: "design"
title: "题目内提交记录与代码回看"
status: "checked"
work: "WORK-043"
owners: ["codex/root"]
depends_on: ["FEATURE-011", "EXPERIENCE-019"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# DESIGN-037：题目内提交记录与代码回看

## 背景

上游 [FEATURE-011](./10-feature-FEATURE-011.md) 与 [EXPERIENCE-019](./20-experience-EXPERIENCE-019.md)。当前 submission 表已有 user_id、problem_id、created_at、read_model、source；View 是安全结果模型，不含原始代码。既有 API 只支持创建、按 id 查询和幂等键恢复。本轮在已打开的题目工作台查询，直接使用当前 problemId；不需要新增题目 id 解析接口。

## 目标与限制

新增当前用户提交查询和原始代码读取，不改提交受理/冻结/判题逻辑。原代码仅来自 submission.source，不读取 judge_input.payload，不把源码加到列表或既有结果轮询 DTO。既有行不需回填，不修改 V1 migration。

## 整体方案

契约先行，在 contracts/web-api.openapi.json 增量定义：

- GET /api/submissions：page 默认 1、size 默认 20 范围 1..100；必填 problemId UUID、可选 verdict（已有九种判定）。data 为 SubmissionData[]，meta.pagination 复用现有分页约定，total 为当前账号且匹配条件的计数。按 created_at DESC,id DESC；改变筛选重置页码。参数异常 400，SQL 全部绑定参数，不接受客户端 userId；本轮不增加 q。
- GET /api/submissions/{id}/source：返回受 ApiSuccess 包装的 {submissionId,problemId,problemVersionId,languageId,source}，只读原始 source；最大范围沿用创建时 256 KiB 约束。账号取已验证身份，不接受客户端 userId；SQL 同时约束 id 和 user_id，无记录一律 404。DTO 的 toString 脱敏。

两个新增公开 GET 均复用错误信封和 requestId；提交列表、结果和源码 Cache-Control:no-store。前端 Query key 带 userId、参数/id；读请求核对发起时账号，登出清理私有缓存并取消请求，迟到响应不得渲染。源码按需读取，结果继续使用既有 getSubmission 的终态轮询。

## 模块与数据

submission-service：新增列表、count、按 owner 读取源码，复用安全 View；每页查询不能 SELECT source 或 judge_input。状态/判定从既有 read_model 中读取，保证与已写入结果同一真源。单次列表的 count 与 rows 在只读一致性事务中完成；跨翻页新增提交导致页位置移动属于实时列表语义，手动刷新回第一页，不承诺跨请求冻结历史。

使用现有 submission_owner(user_id,created_at) 索引先限定账号，id 作为稳定次序，SQL LIMIT 在数据库执行；用 MySQL 集成测试及 EXPLAIN 检查 owner 查询路径。首版不新增冗余 verdict 列或迁移；若实测无法支撑数据量，先更新设计/边界再追加索引，禁止先改 V1 或全表 Java 过滤。

problem-service 不修改。题目上下文与当前版本来自现有题目工作台；本人记录跨版本查询，但不能跨 problemId 混入 panel。

Gateway：新增显式类型客户端与控制器，沿用 SubmissionGatewayAccess 的身份预校验和受限错误映射。Web：扩展 submissions 功能目录与现有题目工作台，在左侧嵌套描述/记录 Tabs；列表与详情均为 panel 内状态，右侧编辑器组件身份保持稳定。历史载入沿用 CodeDraftController 的冲突/保存机制。

## 接口与状态

既有 POST /api/submissions、结果与恢复键查询结构不变。现有 /problems/$slug 增加互不冲突的 search 状态：tab=statement|submissions、historyPage、historyVerdict、historySubmissionId；保留原 submissionId 给正在提交的结果恢复，不混用“正在提交”与“正在浏览的历史”。URL 不带源码。任何历史详情先验证 owner 与 source.problemId==当前 problemId，再显示或载入；属于本人但来自别题的 URL 引用不在当前 panel 展示，提示返回本题记录。

左侧 Tab 切换不改变父工作台或编辑器 key，不把右侧编辑器置于随 Tab 切换卸载的子树。历史详情退出保留列表页码/筛选；切题重置历史查询上下文。窄屏外层仍切题面区域/代码，内层仅切左侧内容。现有正式提交成功后使当前账号/题目的列表缓存失效，不强制切换用户正在看的 Tab。

## 安全与失败

每次读都按本人身份限制，ADMIN 无例外；source 和结果未授权统一 404，不泄漏是否存在。只读源码渲染为文本，不解释 HTML，不上报日志或埋点。后端本人历史读权限不因题目下架改变，但本轮不承诺下架题目重新进入时的历史找回 UI；已有题目页继续遵循公开可见性。缓存/请求/代码缓冲的跨账号清理须专门验证。历史代码载入不绕过 expectedProblemVersionId 与 X-Expected-User-Id 提交约束。

## 监控与部署

沿用请求编号和现有日志，只记录非源码元信息。先交付后端增量接口再启用题目内记录 Tab；本轮不部署用户现有环境。测试栈采用隔离数据库与测试凭证，不读取真实他人源码。

## 迁移与兼容

无数据库迁移；旧记录 source 已存在，直接支持。回退题目内 Tab 及增量接口不会删除记录或影响原正式提交链路。原本机恢复键与草稿键不迁移。

## 备选方案

把源码加到 SubmissionData 会使所有轮询与列表带源码，扩大暴露面和流量，故采用按需独立端点。题目内已知 problemId，无需新增题目解析或全局搜索；数据查询按 owner+problem 组织，可供未来个人简介页扩展复用，暂不新增无调用方的全局 API。首版采用有总数的页码分页，后续数据量增长可另立游标分页改进。

## 风险与重审条件

最大风险是越权/迟到响应泄漏与草稿覆盖，其次是 JSON 条件筛选性能。多账号、大源码、同时间提交、历史题目不可见及存储失败都是必要测试；无测量不宣称性能达标。任何跨服务协议、历史版本阅读或索引迁移的额外改动先升级当前设计。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：已完成现有能力核对、本人源码边界、历史版本与草稿保护方案及任务拆分，提交意图审核

- 2026-09-07：按用户澄清收敛为题目工作台左侧 Tabs；撤回全局页面、独立详情页、题目解析及导航修改范围，未来个人简介页仅记录为候选方向。
- 2026-09-07：结构与内容校验通过，由工具置为 checked。
