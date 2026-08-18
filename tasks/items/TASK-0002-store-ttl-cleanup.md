---
id: "TASK-0002"
title: "为 sandbox store 增加 TTL 和启动清扫"
type: "tech-debt"
area: "sandbox/store"
priority: "P2"
status: "todo"
assignee: null
depends_on: []
related: ["TASK-0001", "TASK-0005"]
created_at: "2026-08-18T19:10:35+08:00"
updated_at: "2026-08-18T19:10:35+08:00"
claim_branch: null
claimed_at: null
lease_until: null
completed_at: null
review_required: true
---

# TASK-0002：为 sandbox store 增加 TTL 和启动清扫

## 背景

store 目前只依靠 judge 显式调用 `Delete`。judge 异常退出时，上传引用会永久残留。

## 目标

根据文件修改时间清理过期引用，并在进程启动时处理上次异常退出留下的内容。

## 范围

包含：

- 可配置并经过校验的 TTL。
- 启动清扫和运行期清扫的生命周期管理。
- 清扫失败的可观测性和并发边界测试。

不包含：

- 为 store 引入内存索引。
- 分布式锁或多节点共享存储。

## 验收标准

- [ ] 过期文件会被清理，未过期文件不会被误删。
- [ ] 启动时能够发现并清理上次运行留下的过期文件。
- [ ] 清扫循环能够随进程上下文停止，不泄漏 goroutine。
- [ ] 清扫与正常 `Get`、`Put`、`Delete` 并发时通过竞态测试。

## 执行记录

- 2026-08-18：从本地 `docs/backlog.md` 迁入任务中心。

## 阻塞信息

无。

## 完成结果

尚未完成。

