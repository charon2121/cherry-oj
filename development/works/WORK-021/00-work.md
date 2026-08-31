---
id: "WORK-021"
type: "work"
title: "修复 IDEA 错误按叶子工程构建 user-service"
status: "todo"
work: null
owners: ["codex/root"]
risk: "low"
impact: "multi-module"
concerns: ["compatibility"]
depends_on: []
related: ["ISSUE-004", "DESIGN-017", "TASK-029", "VERIFY-021"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "问题说明、复现与预期", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["ISSUE-004"], "checks": ["definition", "scope"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "design", "label": "原因与修复方案", "requirement": "required", "status": "doing", "status_source": "derived", "artifacts": ["DESIGN-017"], "checks": [], "source": "overlay:fix-complexity", "reason": "复杂修复需要独立确认原因与修复方案"}, {"stage": "tasks", "label": "修复任务", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["TASK-029"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["TASK-029"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": ["impact-analysis"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": ["VERIFY-021"], "checks": ["automated-tests", "compatibility"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}]
required_documents: ["issue", "design", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests", "impact-analysis", "compatibility"]
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



# WORK-021：修复 IDEA 错误按叶子工程构建 user-service

## 为什么做

开发者在 IDEA 中尝试构建或启动用户服务时，会立即遇到“找不到共享日志组件”的错误，服务还没进入
编译和启动阶段就失败。仓库本来已经包含这个组件，问题不是依赖缺失，而是 IDEA 把用户服务误当成
一个可以脱离后端整体单独构建的工程。

本工作要提供一个不会选错工程入口的 IDEA 启动方式，并把正确的构建入口写清楚。完成后，开发者不必
先向个人电脑安装一份共享组件，也不需要删除正常的日志能力来绕过错误。

## 成功标准

- [ ] 从 IDEA 的仓库共享启动项可以直接编译并启动 `user-service`，不再先执行错误的单模块 Maven 打包。
- [ ] 只验证用户服务时，共享组件会自动进入同一次构建，不依赖个人电脑中碰巧存在的旧版本。
- [ ] 新开发者能够从后端说明中明确选择正确的 IDEA 导入入口和 Maven 命令。
- [ ] 共享日志能力、服务业务行为、接口和部署产物保持不变。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-021` 查看实际进度。

## 待确认项

请负责人确认 ISSUE-004 与 DESIGN-017 中的最小修复：保留共享组件和现有 Maven 聚合结构，新增仓库共享
的 IDEA 启动项，并修正文档；不尝试把本来属于同一后端工程的模块伪装成完全独立发布的工程。

## 风险点

主要风险是只修复当前电脑的 IDEA 私有设置，其他开发者仍会遇到同一问题。修复必须进入仓库可共享的
启动配置，并用不依赖本地缓存的构建命令验证。若 IDEA 的模块名与 Maven 导入结果不一致，启动项会
直接失效；这可以通过重新导入后端根工程和实际启动检查发现，配置文件也可直接回退。

## 影响面

影响使用 IDEA 开发 Java 后端的人员，以及五个服务共用的构建说明。实施只新增用户服务的共享启动
入口并修正文档，不修改服务源码、共享组件、公共接口、数据库或其他技术栈。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-28：创建工作项并生成初始流程。
- 2026-08-28：确认失败来自 IDEA 以叶子 POM 启动 Maven，补充问题、方案、任务和验收边界，提交审核。
