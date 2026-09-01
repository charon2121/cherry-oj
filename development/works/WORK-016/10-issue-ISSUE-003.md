---
id: "ISSUE-003"
type: "issue"
title: "修复设计系统发布后的文档 CI"
status: "approved"
work: "WORK-016"
owners: ["codex/root"]
depends_on: []
related: ["WORK-015", "TASK-021", "VERIFY-015"]
implements: []
verifies: []
tags: []
created_at: "2026-08-28"
updated_at: "2026-08-28"
---

# ISSUE-003：修复设计系统发布后的文档 CI

## 为什么做

设计系统发布之后，主干上有一项自动检查没有通过。产品本身没有受影响，但检查一直是红的，后来的
开发者就无法快速判断自己的新提交是否安全；文档里还有几处指向已经删掉的页面，会继续误导读者。
本次只把这些发布收尾的问题整理干净，不改变已经批准的设计、产品行为或线上代码。

## 问题现象

提交 `eb3c7a3` 推送到 `origin/main` 后，GitHub Actions run `33137410351` 中只有“开发文档系统”job
失败；Web、Go、contracts、go.mod 整洁度和容器联调均通过。

## 复现方式

在该提交的干净工作区依次运行：

```text
scripts/work check
python3 scripts/docs_test.py
```

## 实际结果

`scripts/work check` 首先拒绝 `development/roles/`：开发文档顶层只允许全局入口、模板、Schema 和
`works/`。因此 CI 后续两步被跳过；本地继续执行链接检查还会发现 5 个失效引用，分别指向已删除的
`ui-system.html`、`frontend-architecture.html` 和 `submission-judging-chain.html`。

## 预期结果

开发文档目录仍遵守既有模型；已删除页面不以空白兼容页或恢复旧真源的方式回流；所有文档入口指向
现有且受维护的 Markdown 或设计系统组件参考，并且 CI 的开发文档 job 完整通过。

## 影响与条件

问题只在包含 `eb3c7a3` 的分支出现。它不影响应用运行，但会阻断主分支健康状态，也会让未来提交在
同一红色基线上继续叠加，无法区分新增回归。

## 原因

发布提交同时收录了两个原本不属于 WORK-015 的未跟踪角色 JSON，并删除三个旧 HTML 页面却保留了
对它们的文字引用。两个角色文件没有消费者、Schema 或工具配置；直接放宽 allowlist 会把不受校验的
新类型引入开发文档体系，不符合现有边界。

## 修复方向

1. 删除 `development/roles/` 中两份误收录文件，不修改 `scripts/work` 的 allowlist。
2. 保持三个旧 HTML 页面删除，不创建 stub；将 5 个引用分别收敛到 `architecture.md`、当前
   `frontend.md`、`design-system.md` 或 `design-system/components.html`。
3. 更新 WORK-015 的 VERIFY/MEMORY 中“roles 仍阻断检查”等已经失效的现状描述。
4. 运行完整开发文档、设计系统与差异检查后再提交并推送。

## 回归检查

- AC-001：`scripts/work check` 退出码为 0，且未扩展 development 顶层 allowlist。
- AC-002：`python3 scripts/docs_test_test.py` 与 `python3 scripts/docs_test.py` 均通过，不存在旧 HTML 引用。
- AC-003：三个旧 HTML 页面保持删除，两个角色 JSON 不再存在于仓库。
- AC-004：设计系统生成/合同检查继续通过，主题数值与运行时代码无差异。
- AC-005：改动只落在 TASK-022 的 write paths，CI 其余既有 job 不受影响。

## 变更记录

- 2026-08-28：状态变更：draft → approved。原因：用户已审核问题定义与最小修复方向，并明确允许执行
