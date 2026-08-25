---
id: "WORK-008"
type: "work"
title: "建立 Web 到 Gateway 的 REST 基础连通模块"
status: "verified"
work: null
owners: ["codex/root"]
risk: "medium"
impact: "multi-module"
concerns: ["reliability"]
depends_on: []
related: ["WORK-009", "CAPABILITY-001", "EXPERIENCE-002", "DESIGN-006", "PLAN-006", "TASK-008", "VERIFY-008", "MEMORY-006"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "clarify", "label": "需求澄清", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["WORK-008"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "definition", "label": "能力定义", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["CAPABILITY-001"], "checks": ["definition", "scope"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "experience", "label": "开发体验 / 运维要求", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["EXPERIENCE-002"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "design", "label": "技术方案", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["DESIGN-006"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "plan", "label": "开发计划", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["PLAN-006"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "tasks", "label": "开发任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-008"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-008"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "verification", "label": "验证", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["VERIFY-008"], "checks": ["automated-tests"], "source": "profile:infra", "reason": "infra 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "blocked", "status_source": "manual", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "线上观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["reliability"], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["MEMORY-006"], "checks": [], "source": "profile:infra", "reason": "infra 基础流程"}]
required_documents: ["capability", "experience", "design", "plan", "task", "verify", "memory"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "reliability"]
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















# WORK-008：建立 Web 到 Gateway 的 REST 基础连通模块

## 为什么做

Java Gateway 与 React Web 都已有可启动骨架，但浏览器还没有一个真实 REST 资源可调用，首页也只能
展示静态占位内容。后续登录、题目和提交模块若各自重复建立请求、错误与状态边界，会产生不一致的
调用方式。本工作先交付一个不依赖业务决策和数据库的最小纵向切片，证明 Web 能通过 `/api` 与
Gateway 交换经过校验的 JSON。

## 成功标准

- [x] Gateway 通过 `GET /api/status` 返回稳定、无敏感信息的 JSON 表示。
- [x] Web 通过统一请求函数与 TanStack Query 调用接口，并明确展示加载、失败和成功状态。
- [x] 后端接口测试、前端组件测试、静态检查和构建全部通过。
- [x] 本地启动 Gateway 后可以用 `curl` 获得与前端运行时校验一致的响应。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-008` 查看实际进度。

## 待确认项

暂无。

## 风险点

- API 名称或响应形状过早承载业务语义：只暴露连通状态，不加入用户、题目或部署信息。
- 前端把不可信 JSON 直接当成 TypeScript 类型：在 feature 边界显式校验字段和值。
- 查询失败被静默隐藏：页面使用可访问的错误提示和显式重试按钮。
- 该端点只表示 Gateway 进程可以响应，不代表其它服务或数据库健康；接口文档和页面文案均明确边界。

## 影响面

修改 `gateway-service` 的公开只读 API、`apps/web` 的请求基础层和首页状态展示，并补充两侧测试与
服务工具链说明。不访问数据，不引入权限，不修改四个业务服务、跨语言契约、judge 或 sandbox。
开发环境继续使用现有 Vite `/api` 代理；生产仍要求静态站与 Gateway 同源部署。

## 关联文档

WORK-009 接收人工复核后的系统级重设计。WORK-008 的 verified 只表示 status endpoint 连通性试验在
当时范围内通过，不表示其 DTO 与 GET helper 已获批成为未来通用基建；该实现不得进入 release。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → ready。
- 2026-08-25：完成 Gateway、Web 与 Vite proxy 的真实 REST 联调。
- 2026-08-25：根据文档、任务与验证事实刷新状态：ready → doing。
- 2026-08-25：流程阶段 复核：ready → done。原因：已完成接口边界、依赖方向、信息暴露和禁止路径影响复核
- 2026-08-25：根据文档、任务与验证事实刷新状态：doing → verified。
- 2026-08-25：人工复核认为当前设计不通用；建立 WORK-009 重新设计统一协议，并阻止本试验发布。
- 2026-08-25：流程阶段 上线：ready → blocked。原因：人工复核认为现有局部实现不具备通用性，需等待 WORK-009 设计获批后迁移或替换
