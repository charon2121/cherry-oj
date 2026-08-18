---
id: "TASK-0001"
title: "为 sandbox store 增加上传大小上限"
type: "tech-debt"
area: "sandbox/store"
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

# TASK-0001：为 sandbox store 增加上传大小上限

## 背景

`internal/sandbox/store/disk.go` 当前没有上传大小上限。store 使用的 `/dev/shm` 是内存盘，
超大上传可能直接耗尽宿主机内存。

## 目标

为 store 增加明确的 `maxBytes` 限制，超过限制时拒绝上传并清理未完成内容。

## 范围

包含：

- 使用流式限制，不能先把整个上传读入内存。
- 覆盖恰好等于上限和超过上限的边界。
- 返回包含限制和目标信息的可定位错误。

不包含：

- 用户级配额。
- store 总容量治理。

## 验收标准

- [ ] `Put` 接受或读取明确的字节上限。
- [ ] 恰好等于上限的内容可以成功写入。
- [ ] 超过上限时返回错误，且不留下不完整文件。
- [ ] 相关单元测试和竞态测试通过。

## 执行记录

- 2026-08-18：从本地 `docs/backlog.md` 迁入任务中心。

## 阻塞信息

无。

## 完成结果

尚未完成。

