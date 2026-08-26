---
id: "WORK-009"
type: "work"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "system"
concerns: ["compatibility", "reliability", "security"]
depends_on: []
related: ["WORK-008", "CAPABILITY-002", "EXPERIENCE-003", "DESIGN-007", "DECISION-006", "PLAN-007", "TASK-009", "VERIFY-009", "MEMORY-007"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-009"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "definition", "label": "能力定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CAPABILITY-002"], "checks": ["definition", "scope"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "experience", "label": "开发体验 / 运维要求", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-003"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-007"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有独立技术方案"}, {"stage": "decision", "label": "技术决策", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DECISION-006"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响需要可长期追踪的技术决定"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-007"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级影响必须有显式实施计划"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-009"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-009"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-009"], "checks": ["automated-tests", "cross-module-regression", "compatibility", "security"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": ["reliability"], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-007"], "checks": [], "source": "overlay:risk-impact", "reason": "高风险或系统级工作必须沉淀长期记忆"}]
required_documents: ["capability", "experience", "design", "decision", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "cross-module-regression", "compatibility", "reliability", "security"]
human_confirmations: []
blocking_items: []
reversible: true
data_change: false
public_api_change: true
security_sensitive: false
user_visible: true
created_at: "2026-08-25"
updated_at: "2026-08-25"
work_type: "infra"
---






















# WORK-009：建立统一的 Web REST 交换协议与请求基建

## 为什么做

WORK-008 证明了浏览器、Vite proxy 与 Gateway 能交换一份 JSON，但它只定义了 `/api/status` 的固定
响应和一个 GET helper，不能作为登录、题目、提交、分页、表单校验和异步创建等未来 API 的统一
基础。若继续在这个局部设计上叠业务，各 feature 会自行决定 envelope、错误码、request ID、重试、
分页和运行时校验，最终形成多个互不兼容的“通用层”。

本工作重新从浏览器边界出发，先冻结一个覆盖请求、成功、失败、元数据、兼容和例外的公共 REST
协议，再分别设计 Gateway 与 Web 的基础设施。协议经人工审阅批准前，不修改或继续扩展实现代码。

## 成功标准

- [x] 人工确认 DECISION-006 中的 wire format、错误模型、Gateway 所有权与版本策略。
- [x] 每一种普通 JSON API 都能使用同一成功 envelope、Problem Details 和 request ID 规则表达。
- [x] 单资源、集合、分页、创建、异步受理、无 body、校验失败、鉴权失败、冲突、限流和系统故障均
  有无歧义的 HTTP 与 JSON 示例。
- [x] OpenAPI 成为浏览器边界唯一真源，Java 实现、TypeScript 类型、运行时校验和契约测试可由它
  对齐，手写类型不得悄悄漂移。
- [x] 前端请求层与后端响应/异常层的职责、禁止行为、迁移顺序和回退方式已经明确并实现验证。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-009` 查看实际进度。

## 待确认项

暂无。2026-08-25 人工确认采用推荐方案 A，并确认 Gateway 所有权、`/api` 兼容演进与安全边界。

## 风险点

- “统一格式”重复 HTTP 已有语义，出现 body `code=OK` 但 HTTP 500 的双真源；以 DECISION-006 明确
  单一判据并用不变量测试防止漂移。
- 通用 envelope 被扩展成万能 `Map<String, Object>`，失去类型安全；公共字段保持小而封闭，业务
  字段只存在于 endpoint DTO。
- Gateway 为统一格式吞掉下游真实故障或暴露内部细节；只做分类与安全映射，保留 request ID 和服务
  端日志关联，不透传堆栈、SQL、内部 URL 或敏感数据。
- 严格 decoder 阻断服务端增加可选字段；公共 parser 校验必需字段但容忍未知扩展，安全敏感 DTO 才
  按契约显式 strict。
- 一次性迁移所有业务服务扩大风险；先迁移未发布的 WORK-008 试验端点，再由后续业务 API 逐项接入。

## 影响面

系统级影响覆盖 Gateway browser-facing API、Web 请求与错误基础层、OpenAPI/JSON Schema 契约、CI
漂移检查、日志关联和未来每个业务 feature 的接入方式。它不改变数据库、Kafka 事件、Java 服务间
内部 DTO、Go judge/sandbox 契约或 verdict 语义。批准后才会把确认结论同步到 `docs/backend.md`、
`docs/frontend.md`、`docs/architecture.md` 和 `CLAUDE.md`；本轮草案只保存在当前 WORK。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：根据人工反馈，将 WORK-008 的局部连通实现升级为独立系统级协议设计；设置“审阅后才
  编码”的 blocking 门禁。
- 2026-08-25：人工审阅通过推荐方案 A，确认错误模型、Gateway 所有权、版本策略与安全边界；解除
  编码门禁。
- 2026-08-25：范围澄清后将 `security_sensitive` 从 true 调整为 false：本工作验证错误脱敏、CSRF 与
  request ID 边界，但不修改认证、授权、凭据或权限模型；`security` concern 与验证门禁继续保留。
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → doing。
- 2026-08-25：OpenAPI、Gateway、Web client、status 迁移、生成漂移门禁和全局文档完成；本地契约、
  聚合后端、Web、浏览器与真实 Vite proxy 验证通过，等待仓库复核后结束 development/verification。
- 2026-08-25：根据文档、任务与验证事实刷新状态：doing → verified。
- 2026-08-25：流程阶段 上线：ready → blocked。原因：当前没有可用生产环境；本次仅提交并推送已验证代码，不代签生产发布
- 2026-08-25：流程阶段 线上观察：pending → blocked。原因：未执行生产发布且当前没有可用生产环境，无法进行线上可靠性观察
