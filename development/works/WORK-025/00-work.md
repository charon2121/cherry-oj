---
id: "WORK-025"
type: "work"
title: "交付题库、题目与测试数据管理"
status: "todo"
work: null
owners: ["codex/root"]
risk: "high"
impact: "system"
concerns: ["data", "security", "accessibility", "performance", "reliability", "release"]
depends_on: []
related: ["FEATURE-007", "EXPERIENCE-013", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033", "VERIFY-025", "MEMORY-020", "TASK-034", "TASK-035", "TASK-036", "TASK-037", "TASK-038", "TASK-039", "TASK-040", "ISSUE-005", "TASK-042", "VERIFY-027"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-025"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "definition", "label": "功能定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["FEATURE-007", "ISSUE-005"], "checks": ["definition", "scope"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "experience", "label": "体验设计", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-013"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-019"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-014"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-015"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-033", "TASK-034", "TASK-035", "TASK-036", "TASK-037", "TASK-038", "TASK-039", "TASK-040", "TASK-042"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-033", "TASK-034", "TASK-035", "TASK-036", "TASK-037", "TASK-038", "TASK-039", "TASK-040", "TASK-042"], "checks": [], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis", "independent-review"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-025", "VERIFY-027"], "checks": ["automated-tests", "cross-module-regression", "accessibility", "data", "security"], "source": "profile:product", "reason": "product 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["rollback", "release"], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["performance", "reliability"], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-020"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["feature", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "independent-review", "rollback", "cross-module-regression", "accessibility", "data", "performance", "release", "reliability", "security"]
human_confirmations: ["安全边界与权限影响已经由负责人确认"]
blocking_items: []
reversible: true
data_change: true
public_api_change: true
security_sensitive: true
user_visible: true
created_at: "2026-08-30"
updated_at: "2026-08-31"
work_type: "product"
---






















































# WORK-025：交付题库、题目与测试数据管理

<!--
本文件面向产品经理和不需要了解实现细节的读者。能用日常语言说清楚时不要使用专业词；必须使用时，
第一次出现就解释它对使用者意味着什么。字段、类、框架、协议、表名、路径和命令放到 DESIGN、PLAN
或 TASK。这里优先说明为什么做、完成后有什么变化、怎样算成功和可能影响谁。
-->

## 为什么做

当前站点既没有学习者可用的真实题库，也没有管理员准备题目的入口。即使手工向数据库写入题面，隐藏
测试数据仍缺少安全上传、校验、部署和版本冻结流程，无法形成一条可重复的出题链路。

本工作把两端一起交付：学习者能够找题、读题；管理员能够创建和修订 C++ ACM 题目、管理样例与语言、
上传不可变测试数据、验证发布条件并显式发布。它承担 `WORK-002` 中“管理员准备题目 + 用户找题读题”
的完整前置切片，不实现提交与判题结果页面。

## 成功标准

- [ ] 未登录访客和已登录用户都能从主导航进入题库，浏览当前公开、可用的题目。
- [ ] 用户能按关键字、难度、标签、代码模式和语言缩小结果，并通过稳定分页继续浏览。
- [ ] 用户能用稳定地址打开题目详情，看到题面、输入输出说明、约束、提示、样例和可选语言。
- [ ] 加载、空结果、查询失败、题目不存在和正常内容都有明确且可恢复的页面状态。
- [ ] 草稿、私有题目、历史非当前版本、隐藏测试数据和内部判题模板不会出现在公开接口或页面中。
- [ ] ADMIN 能创建、编辑、预览、删除未发布草稿，并从已发布题目创建下一不可变版本。
- [ ] ADMIN 能上传、检查、下载和绑定成对 `.in/.out` 测试数据版本；上传失败可诊断且不留下可用半包。
- [ ] ADMIN 能把测试数据部署到当前判题环境、填写绝对限制、用临时参考程序验证并执行发布前检查。
- [ ] 发布一次性冻结题面、样例、语言和数据版本并切换公开指针；失败保留草稿，不产生半发布状态。
- [ ] 数据库、公开契约、服务链路和 Web 页面均有自动测试及一次真实端到端验收证据。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-025` 查看实际进度。

## 待确认项

负责人已确认 `DECISION-014` 中三个关键边界：匿名公开读取仍经 Gateway；测试数据首版存入配置的私有
文件存储并以不可变版本管理；题目管理首版只创建 C++ ACM，发布只面向当前 ACTIVE 判题环境，环境注册/
切换仍属于运维和后续管理范围。

## 风险点

- 最严重的风险是普通题目详情复用内部判题快照，导致隐藏数据或 `judgeTemplate` 泄漏；通过独立公开
  DTO、字段白名单、负向接口测试和端到端响应检查防护。
- 测试数据是隐藏判题资产；压缩包穿越、解压炸弹、半写文件、错误配对、跨题绑定或下载越权都可能
  破坏判题或泄题，必须流式限额、临时目录校验、原子封存、hash/manifest 和 ADMIN 反例共同防护。
- 发布跨 problem-service、judging-service 和文件存储，但没有分布式事务；所有远程部署/验证先完成，
  最终只在 problem-service 本地短事务冻结版本和当前指针，重试必须幂等。
- 首个题目数据库 migration 一旦进入真实环境便不能改写或删除；上线前用临时 MySQL 完整验证，回退
  应关闭新入口并恢复应用版本，保留兼容空表。
- 组合筛选可能随题量增长变慢；首版限制查询长度和每页数量、使用稳定游标，并记录查询耗时。达到
  重审阈值后再引入专用搜索索引，不提前建立第二份数据真源。

## 影响面

影响所有浏览题目的访客与登录用户、ADMIN 出题人员、浏览器公开契约、Gateway、problem-service、
judging-service 的数据部署/就绪能力、私有测试数据存储和 Web 双端页面。现有身份能力保持不变；
submission-service、正式提交、Kafka 生命周期、Go judge/sandbox 职责与设计系统合同不在改动范围内，
但 judging-service 会调用现有 Go judge 完成受控的发布前参考程序验证。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-30：创建工作项并生成初始流程。
- 2026-08-30：按公开题库列表与详情的独立纵向切片补全范围、风险、方案与实施任务，提交人工审核。
- 2026-08-30：根据负责人反馈扩展为题库、题目管理和测试数据管理一体化切片，增加部署、限制、验证与
  发布边界，重新提交审核。
- 2026-08-30：根据 WORK Type、风险、影响面和关注项重建流程。
- 2026-08-30：负责人批准文档并允许实施。
- 2026-08-31：题目管理默认列表出现 500；根据 request ID 确认为 Web `ALL` 筛选值越过
  API 边界并触发 Gateway 未映射的方法参数校验异常，新增 ISSUE-005、TASK-042 和
  VERIFY-027 提交审核。
