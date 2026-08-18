---
id: "TASK-0005"
title: "为 judge flow 清理失败增加可观测性"
type: "tech-debt"
area: "judge/flow"
priority: "P2"
status: "todo"
assignee: null
depends_on: []
related: ["TASK-0002"]
created_at: "2026-08-18T19:10:35+08:00"
updated_at: "2026-08-18T19:10:35+08:00"
claim_branch: null
claimed_at: null
lease_until: null
completed_at: null
review_required: true
---

# TASK-0005：为 judge flow 清理失败增加可观测性

## 背景

`internal/judge/flow` 的 `deleteRef` 不让清理失败改变判题结论是正确的，但错误目前完全没有
日志，sandbox 短暂不可用时只能通过残留文件间接发现。

## 目标

清理失败时记录引用、资源类别和错误，同时保持已经得出的判题结论不变。

## 范围

包含：

- 为 flow 注入最小 logger 或 cleanup reporter 接口。
- 覆盖源码、编译产物和大输入的删除失败路径。
- 日志中避免泄漏源码、标准答案或其他敏感内容。

不包含：

- 在本任务中实现 store TTL。
- 因清理失败改变 verdict。

## 验收标准

- [ ] 每类引用删除失败都有包含 ref 和底层原因的结构化记录。
- [ ] 清理失败不覆盖原判题结果。
- [ ] 正常清理不产生错误级日志。
- [ ] 假替身测试覆盖失败和成功路径。

## 执行记录

- 2026-08-18：从本地 `docs/backlog.md` 迁入任务中心。

## 阻塞信息

无。

## 完成结果

尚未完成。

