---
id: "WORK-016"
type: "work"
title: "修复设计系统发布后的文档 CI"
status: "doing"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: ["compatibility"]
depends_on: []
related: ["WORK-015", "ISSUE-003", "TASK-022", "VERIFY-016"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "问题说明、复现与预期", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["ISSUE-003"], "checks": ["definition", "scope"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "design", "label": "原因与修复方案", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "tasks", "label": "修复任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-022"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["TASK-022"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["VERIFY-016"], "checks": ["automated-tests", "compatibility"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}]
required_documents: ["issue", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "compatibility"]
human_confirmations: []
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-28"
updated_at: "2026-08-28"
work_type: "fix"
---

# WORK-016：修复设计系统发布后的文档 CI

## 为什么做

设计系统发布后，主分支有一项自动检查没有通过。产品本身没有受到影响，但红色检查会让后续开发者
无法快速判断新提交是否安全，也会让文档中已经失效的入口继续误导读者。

问题来自两类随发布提交进入仓库的内容：两份没有实际用途的角色说明放进了受严格管理的开发文档
目录；三个旧参考页已删除，但仍有文字指向它们。本工作只把这些发布收尾问题整理干净，不改变已经
批准的双主题设计、产品行为或运行时代码。

## 成功标准

- [ ] 主分支的开发文档检查恢复通过，不再需要跳过提交校验。
- [x] 已删除的旧参考页保持删除，所有入口改为指向仍受维护的文档。
- [x] 不为两份无消费者、无格式约束的角色说明扩大开发文档体系。
- [x] Cherry 黑色、pure-white、主题合同与 Web 运行时代码均不发生变化。

## 当前流程

由 WORK Type 基础模板与风险、影响面和 concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-016` 查看实际进度。

## 待确认项

暂无。用户已审核 ISSUE-003 的根因与 TASK-022 的文件边界，并明确批准“删除无消费者角色文件、保留
旧 HTML 删除并修正链接”的方案及执行推送。

## 风险点

角色说明虽然当前没有消费者，但删除后若未来确实需要角色配置，必须先确认目标工具、存放位置和格式，
再单独建立受校验的能力，不能直接放宽现有目录规则。链接替换必须指向语义相符的现有内容，不能仅为
通过检查而制造空白兼容页。

## 影响面

只影响开发流程健康状态和文档导航。不会修改前端、后端、判题引擎、跨语言契约、CI 配置、开发文档
校验器或设计系统数值；现有用户界面和服务行为保持不变。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-28：确认提交 `eb3c7a3` 的唯一失败 job 及被提前退出掩盖的失效链接，创建低风险修复工作项。
- 2026-08-28：根据文档、任务与验证事实刷新状态：todo → ready。
- 2026-08-28：根据文档、任务与验证事实刷新状态：ready → doing。
