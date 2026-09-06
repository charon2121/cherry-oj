---
id: "TASK-072"
type: "task"
title: "实现 Go Judge 节点注册与节点侧数据安装"
status: "done"
work: "WORK-040"
owners: ["codex/root"]
depends_on: ["TASK-071"]
related: []
implements: ["ISSUE-012#AC-001", "ISSUE-012#AC-002", "ISSUE-012#AC-003", "ISSUE-012#AC-004", "ISSUE-012#AC-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/go.md", "docs/engineering/conventions.md", "contracts", "apps/judge-engine", "docs/engine.md", "development/works/WORK-040"]
write_paths: ["apps/judge-engine", "development/works/WORK-040"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "docs/database-design.md", "database migrations", "compose.yaml"]
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# TASK-072：实现 Go Judge 节点注册与节点侧数据安装

## 任务目标

让 Go Judge 以稳定身份自动注册/续租，并在自己的受控目录安全、幂等地安装测试数据。

## 依据

实现 ISSUE-012#AC-001～AC-005，消费 TASK-071 固定的节点控制协议。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- nodeId、control-plane URL、advertise URL、control token、心跳周期和安装限额配置。
- 启动注册、周期心跳、指数退避和正常停止，不阻塞现有 `/judge` 服务启动。
- 受控安装 API：流式接收 ZIP/manifest，二次校验、临时目录、权限、原子 rename、幂等 hash 回执。
- 本地部署清单，用于节点重启后按版本/hash 对账。

## 完成标准

- [x] 控制面暂不可用时 Judge 继续提供健康状态并后台重试，恢复后自动注册。
- [x] 同 nodeId 重启续用身份；服务端拒绝身份/指纹冲突后节点不伪装成功。
- [x] 平面与 Finder 单包装 ZIP 安装成功，非法/超限/摘要不符均清理半成品。
- [x] 相同版本/hash 重试返回同一回执，不重复覆盖；不同 hash 返回冲突。
- [x] token、测试内容和标准答案不进入日志或响应。

## 验证

运行 Go config/contract/API/storage 测试与 race detector；使用临时目录覆盖成功、流中断、重复、冲突、
重启对账和权限，使用假控制面与可控计时器覆盖注册退避和心跳，不用真实等待 35 秒。

## 风险

不能让注册 goroutine 的失败终止 Judge HTTP 服务，也不能从请求路径逃逸 testdata root。若目标文件系统
不支持原子 rename，应明确失败，不降级成覆盖已有目录。

## 执行记录

- 2026-09-06：创建任务。
- 2026-09-06：状态变更：todo → ready。原因：协议与注册控制面完成
- 2026-09-06：状态变更：ready → doing。原因：开始 Go 节点注册心跳及安全安装

- 2026-09-06：Go node/config/contract race 测试及 vet 通过；synctest 覆盖注册失败退避与心跳，真实临时目录覆盖 Finder ZIP、非法路径/链接/重复/UTF-8/超限、摘要与身份冲突、原子安装、重启幂等及请求取消清理。
- 2026-09-06：状态变更：doing → done。原因：节点配置、注册心跳、私有持久目录与原子安装实现完成，定向 race/vet 通过
