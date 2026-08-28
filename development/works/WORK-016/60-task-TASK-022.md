---
id: "TASK-022"
type: "task"
title: "修复设计系统发布后的文档 CI"
status: "doing"
work: "WORK-016"
owners: ["codex/root"]
depends_on: ["ISSUE-003"]
related: ["TASK-021", "VERIFY-015"]
implements: ["ISSUE-003"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", ".github/workflows/ci.yml", "scripts/work", "scripts/docs_test.py", "development/README.md", "development/roles", "development/works/WORK-015", "development/works/WORK-016", "docs/README.md", "docs/architecture.md", "docs/backend.md", "docs/design-system.md", "docs/frontend.md"]
write_paths: ["development/WORKS.md", "development/index.json", "development/roles", "development/works/WORK-015/70-verify-VERIFY-015.md", "development/works/WORK-015/80-memory-MEMORY-012.md", "development/works/WORK-016", "docs/README.md", "docs/backend.md", "docs/design-system.md", "docs/frontend.md"]
forbidden_paths: [".github", "scripts", "development/schema", "development/templates", "development/works/WORK-001", "development/works/WORK-002", "development/works/WORK-003", "development/works/WORK-004", "development/works/WORK-005", "development/works/WORK-006", "development/works/WORK-007", "development/works/WORK-008", "development/works/WORK-009", "development/works/WORK-010", "development/works/WORK-011", "development/works/WORK-012", "development/works/WORK-013", "development/works/WORK-014", "apps", "contracts", "docs/design-system"]
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# TASK-022：修复设计系统发布后的文档 CI

## 任务目标

在不改变设计系统方案、运行时代码或开发文档模型的前提下，清理发布提交误收录的角色文件并修正
已删除旧页面的引用，使本地提交门禁和 GitHub CI 的开发文档 job 恢复通过。

## 依据

只依据 ISSUE-003 的复现、根因、修复方向和 AC-001～AC-005。若发现角色文件存在真实消费者、必须
恢复旧 HTML 才能保留信息，或需要修改校验器/CI 配置，应停止并升级方案，不在本任务中扩大范围。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。`development/roles` 只允许删除现有两个文件；WORK-015 只允许
校正 VERIFY/MEMORY 中与本次检查结果不再相符的陈述。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。尤其不得修改 `scripts/work`、CI workflow、应用代码、
公共契约、设计系统 token/主题/组件包或其他既有 WORK。

## 依赖

ISSUE-003 经人工审核并明确允许执行后，任务才可从 todo 推进到 ready/doing。

## 产出

- 删除两份无消费者的 `development/roles/*.json`。
- 四份全局文档中的 5 个链接改为仍受维护的语义目标，并保留三个旧 HTML 的删除状态。
- WORK-015 VERIFY/MEMORY 中关于真实根目录校验阻塞的陈述按修复后事实更新。
- VERIFY-016 记录本地门禁、设计系统回归、范围和远端 CI 结果。

## 完成标准

- [ ] ISSUE-003 的 AC-001～AC-005 全部有实际证据。
- [ ] `development/roles/` 不存在，allowlist、Schema 与开发工具均未修改。
- [ ] `docs/backend.md` 的判题链路引用指向 `architecture.md` 的正式提交流程。
- [ ] README、设计系统和前端文档不再声称已删除页面是兼容入口或架构总览。
- [ ] 不恢复旧 HTML、不新增空白 stub，也不改变设计系统数值或运行时代码。
- [ ] 本地 pre-commit 同等检查通过后使用独立修复提交推送，远端 CI 全绿。

## 验证

在仓库根目录运行：

```text
python3 scripts/work_test.py
scripts/work check
python3 scripts/docs_test_test.py
python3 scripts/docs_test.py
node docs/design-system/tools/build.mjs --check
node docs/design-system/tools/check.mjs
git diff --check
```

再检查 `git diff --name-status` 与 `git diff -- docs/design-system/ apps/ contracts/ .github/ scripts/`，确认
删除、链接映射和路径边界符合任务；推送后读取本次提交的 GitHub Actions 结果。

## 风险

主要风险是误删未来可能有用的角色想法，或把旧链接改到语义不相符的位置。现有两份 JSON 没有消费者
且可从 Git 历史恢复；未来若确需角色体系，应另建工作项定义工具、Schema 与正式目录。链接目标必须
在上下文中人工复读，不能只以“文件存在”为通过标准。

## 执行记录

- 2026-08-28：创建任务；记录已确认的最小修复边界，等待文档审核和明确执行授权。
- 2026-08-28：状态变更：todo → ready。原因：用户已批准 TASK-022 的读写边界与完成标准
- 2026-08-28：状态变更：ready → doing。原因：开始执行已批准的文档 CI 修复
- 2026-08-28：删除误收录角色文件并迁移 5 个旧 HTML 引用；本地开发文档、链接、设计系统和差异
  检查全部通过，等待提交、推送与远端 CI。
