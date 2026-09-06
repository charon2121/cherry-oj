---
id: "ISSUE-012"
type: "issue"
title: "重构判题节点注册与测试数据交付"
status: "approved"
work: "WORK-040"
owners: ["codex/root"]
depends_on: []
related: ["WORK-025", "WORK-038", "WORK-039"]
implements: []
verifies: []
tags: []
created_at: "2026-09-06"
updated_at: "2026-09-06"
---

# ISSUE-012：重构判题节点注册与测试数据交付

## 为什么做

管理员完成测试数据上传后仍无法部署。五个 Java 服务和 Go Judge 都显示健康，页面却只能显示笼统的
“服务暂不可用”。当前系统需要开发者先手工声明“哪台判题机存在”，再保证两个独立进程恰好共享同一
文件目录；任何一步遗漏都会在操作链路中很晚才失败。

## 问题现象

请求 `req_3ba0c3bb0b264e9d9d4ffcd005cb79b3` 中，user-service 的授权验证返回 200，problem-service 成功
调用 judging-service，judging-service 的部署接口在 19 ms 内返回 503。数据库中
`judge_environment` 行数为 0，内部错误为 `ACTIVE_ENVIRONMENT_MISSING`。

即使通过 `dev` profile 补上一条环境记录，当前 judging-service 默认写入
`apps/server/data/judge-testdata`，而 Compose 中 Go Judge 默认读取另一条宿主机挂载路径。数据库里的
“环境存在”和节点真实在线、可访问、能读取已部署数据并不是同一事实。

## 复现方式

1. 启动 Go Judge Compose 和五个 Java 服务，不为 judging-service 手工 provision 环境。
2. 上传并绑定 READY 测试数据。
3. 点击部署，观察 judging-service 返回 `ACTIVE_ENVIRONMENT_MISSING`。
4. 仅启用 dev fixture 后再次部署，再核对 judging-service 写入目录与 Go Judge 容器实际挂载目录；
   两者需要额外人工配置才能一致。

## 实际结果

静态配置和数据库 seed 冒充了节点发现；本地文件路径冒充了跨进程数据交付。健康检查只证明进程存活，
不能证明当前 ACTIVE 环境有在线节点，也不能证明目标节点已经安装对应测试数据。错误经过两层代理后又
被压缩成通用 503，管理员无法恢复。

## 预期结果

判题环境和数据可用性必须来自真实节点报告：Go Judge 上线后自动注册并持续报告在线状态；测试数据由
目标节点安装并返回摘要回执。judging-service 只向在线、环境兼容且已经持有该数据的节点调度。本地与
生产使用同一链路，不再依赖 profile seed 或共享宿主机目录。

## 影响与条件

涉及 Go Judge、judging-service、内部契约、judging 数据模型、本地 Compose、problem/Gateway 错误映射
和管理工作台的可操作状态。题目测试数据原包、公开题目数据、用户 Session/JWT 和 sandbox 执行协议不
改变。

## 原因

WORK-025 首版把“当前唯一 ACTIVE 环境”作为静态前置条件，并用启动期 provision 创建；部署实现则运行
在 judging-service 本地文件系统，默认假设 Go Judge 能看到同一路径。这在单机手工联调时可工作，但缺少
节点注册、租约、逐节点部署回执和一致的错误语义，因此不能覆盖进程重启、容器挂载变化和新增节点。

## 修复方向

- Go Judge 使用稳定 nodeId 向 judging-service 注册环境指纹、版本、语言能力和可访问 endpoint，并以
  可配置心跳租约报告在线状态。
- `judge_environment` 保留为不可变兼容性分组；首个真实环境可自动成为 ACTIVE，不同指纹只注册为
  REGISTERED，不能静默切换现有 ACTIVE。
- 新增逐节点事实，区分“环境已登记”“节点在线”“该节点已安装某份数据”。
- judging-service 把 ZIP 与 manifest 流式发送给目标 Go Judge；Go Judge 在自身受控目录二次校验并原子
  安装，返回包含 nodeId、environmentFingerprint 和 sha256 的回执。
- 删除 judging-service 与 Go Judge 共享目录的正确性前提；本地 Compose 和生产部署走同一注册、心跳、
  安装链路。
- 将可恢复的节点缺失、离线和安装失败作为受限的可操作错误贯穿到管理工作台。

## 回归检查

- AC-001：空 judging 数据库与在线 Go Judge 启动后，无需 profile seed 或手写 SQL，节点自动注册，首个
  环境成为 ACTIVE；相同 nodeId 重启幂等，不产生重复环境。
- AC-002：心跳租约内节点为 ONLINE；心跳停止超过期限后变为 OFFLINE 且不再接收部署/判题，但环境和
  历史回执不被删除；恢复心跳后同一节点恢复 ONLINE。
- AC-003：部署请求只发送给 ACTIVE 环境中的在线节点；Go Judge 在自己的 testdata root 校验并原子安装，
  judging-service 只有收到匹配 nodeId、指纹和摘要的回执才记录 READY。
- AC-004：同一摘要重复部署幂等；摘要、manifest、环境指纹或节点身份不匹配均失败且不留下 READY 或
  半成品目录。
- AC-005：本地 Compose 不再要求 `application-dev.yaml` provision 或 `TESTDATA_PATH` 共享目录；直接启动
  后可完成上传、部署和参考程序校准。生产使用相同协议，不自动切换不同指纹环境。
- AC-006：没有在线节点、节点失联和节点拒绝数据分别返回稳定、可操作且不泄漏内部正文的错误；工作台
  在 readiness 已知不可用时禁用部署并显示原因。
- AC-007：现有环境、部署和校准事实可迁移；切换期间旧本地部署实现可回退，已发布题目和历史 JudgeInput
  不被改写。
- AC-008：Go、Java、Web 定向测试、真实 MySQL/文件系统集成、Compose 端到端和全仓回归通过；JWT 2 小时、
  提前刷新 5 分钟、Session 30 天以及测试数据 ZIP 兼容规则保持不变。

## 变更记录

- 2026-09-06：从 default-profile 局部修复升级为真实判题节点驱动的系统级根治方案。
- 2026-09-06：状态变更：draft → review。原因：已确认静态环境 seed 与共享目录是同一架构缺口，根治目标和 8 条跨模块验收标准完整
- 2026-09-06：意图闸通过：review → approved。原因：确认采用判题节点自注册、心跳和节点侧测试数据安装的系统级重构方案
