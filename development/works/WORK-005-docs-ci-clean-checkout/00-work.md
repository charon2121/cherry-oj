---
id: "WORK-005"
type: "work"
title: "修复开发文档 CI 的 clean checkout 链接校验"
status: "implemented"
work: null
owners: ["codex/root"]
risk: "low"
impact: "local"
concerns: []
depends_on: []
related: ["ISSUE-001", "TASK-005", "VERIFY-005"]
implements: []
verifies: []
tags: []
workflow: [{"stage": "definition", "label": "问题说明、复现与预期", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["ISSUE-001"], "checks": ["definition", "scope"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "design", "label": "原因与修复方案", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "tasks", "label": "修复任务", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-005"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "development", "label": "开发", "requirement": "required", "status": "done", "status_source": "derived", "artifacts": ["TASK-005"], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "review", "label": "复核", "requirement": "required", "status": "done", "status_source": "manual", "artifacts": [], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "verification", "label": "回归验证", "requirement": "required", "status": "ready", "status_source": "derived", "artifacts": ["VERIFY-005"], "checks": ["automated-tests"], "source": "profile:fix", "reason": "fix 基础流程"}, {"stage": "release", "label": "上线", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "工作包含交付或上线影响"}, {"stage": "observe", "label": "观察", "requirement": "required", "status": "pending", "status_source": "derived", "artifacts": [], "checks": [], "source": "overlay:delivery", "reason": "上线后必须观察实际结果"}, {"stage": "memory", "label": "项目记忆", "requirement": "optional", "status": "skipped", "status_source": "derived", "artifacts": [], "checks": [], "source": "profile:fix", "reason": "fix 基础流程"}]
required_documents: ["issue", "task", "verify"]
required_checks: ["definition", "scope", "automated-tests"]
human_confirmations: []
blocking_items: []
reversible: true
data_change: false
public_api_change: false
security_sensitive: false
user_visible: false
created_at: "2026-08-25"
updated_at: "2026-08-25"
work_type: "fix"
---







# WORK-005：修复开发文档 CI 的 clean checkout 链接校验

## 为什么做

开发文档链接校验在本地读取完整工作区，却在 CI 的 clean checkout 中只看到 Git 跟踪文件。被
`.gitignore` 排除的本地教程因此让本地检查误报通过，直到推送后才暴露失效链接，破坏了“本地绿则
CI 应同样通过”的反馈约定。

## 成功标准

- [x] 全局文档不再链接到新克隆不保证存在的本地教程文件。
- [x] 链接目标即使存在于本地，只要未进入 Git，文档校验也会拒绝。
- [ ] 工作项、文档链接校验及其单元测试在本地和 GitHub Actions 中通过。

## 当前流程

由 WORK Type 基础模板与风险、影响面、concern 增量规则生成。front matter 的 `workflow` 记录阶段
必需性、实际进度、artifacts、检查与规则来源；使用 `scripts/work flow WORK-005` 查看实际进度。

## 待确认项

暂无。

## 风险点

风险仅限文档链接判定：若目录链接的判断过严，可能误伤指向已跟踪目录的链接。通过文件、目录和
未跟踪目标三类单元测试覆盖；出现回归可回退本提交。

## 影响面

只影响 `docs/engine.md` 的教程说明、文档链接校验脚本、对应 hook/CI 步骤和本工作项记录；不改变
业务代码、契约、数据、部署以及 `tutorial/` 的本地材料策略。

## 关联文档

由 `related` 维护，不在正文复制状态。

## 变更记录

- 2026-08-25：创建工作项并生成初始流程。
- 2026-08-25：确认 Git 跟踪状态是本地与 clean checkout 一致性的判断边界。
- 2026-08-25：本地工作区与暂存区 clean-checkout 模拟均通过，等待远端 CI 证据。
- 2026-08-25：流程阶段 复核：ready → done。原因：已复核实现 diff、任务边界、错误消息及 clean-checkout 行为
- 2026-08-25：根据文档、任务与验证事实刷新状态：todo → implemented。
