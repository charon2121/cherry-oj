---
id: "ISSUE-015"
type: "issue"
title: "修复沙箱连续请求完成与容量归还的竞态"
status: "approved"
work: "WORK-051"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# ISSUE-015：连续请求偶发被错误拒绝

## 为什么做

连续运行程序时，上一项已经告诉调用方“完成”，下一项仍可能被拒绝。自动回归在两台独立测试机器上遇到该问题；需要先修复并证明连续运行可靠，才能继续承诺重构没有改变行为。

## 问题现象

WORK-050 的两轮 Linux CI 在不同位置返回 InternalError，原本应为程序自身的信号或输出超限结果。来源见 WORK-050/VERIFY-051；本轮仅整理修复材料，不修改原已验收 WORK-048。

## 复现方式

当前提交7a66b35fd157272e6dcc0a3a80a7bca0ad8cd690，运行ci.yml的sandbox-kernel，使用原chain_batch smoke。实际两次复现：[34464741702](https://github.com/charon2121/cherry-oj/actions/runs/34464741702)在magiclink请求、[34465164384](https://github.com/charon2121/cherry-oj/actions/runs/34465164384)在zero-output-writer请求。下一步需加入可控制调度的最小回归，当前不能声称每次必现。

## 实际结果

helper Unix连接被reset，返回cpuNs/memoryBytes为0的InternalError。第三轮异常时helper主进程8729及HTTP主进程8755均为active且未换PID；最终任务、挂载与cgroup清理为空。日志中的OOM事件与此前正常MLE用例同时发生，不能把它当成helper主进程崩溃的证据。

## 预期结果

- REQ-001：上一请求被报告完成时，其执行资源和容量均已归还，紧接的合法串行请求能获得执行机会。
- REQ-002：真正同时占满容量时仍拒绝超额连接；不增加槽位、队列、延时或自动重试。
- REQ-003：资源未回收、取消、传输失败不能成为成功；收尾和慢客户端处理保持有界。
- REQ-004：沿用WORK-050的原隔离/计量/状态/回收断言及节点身份生成规则，不修改生产限额或权限模型。

## 影响与条件

涉及sandbox到本机helper的请求收尾；正式提交与自定义运行共用此链路。已复现于Ubuntu24.04.5/6.17 Azure/amd64；尚无证据宣称所有平台或每次执行均失败，也未在当前用户服务器重测。

## 原因

源码已确认存在窗口：serveConn发送Completion后返回，外层goroutine随后才关闭连接并归还slots；接入循环发现slots暂不可用就直接关闭新连接。客户端读Completion后立即返回。窗口与已观察错误吻合，具体归因仍需最小测试和修复前后对照确认。

## 修复方向

先确定完成信号、执行槽归还与连接结束之间的顺序，再做最小修复；若最小测试证明为其他原因，回到设计更新证据，不能机械修改候选位置。

## 回归检查

- AC-001：可控制调度的测试在旧顺序失败，修复后通过，覆盖完成确认与容量归还关系。
- AC-002：真实忙碌、慢客户端、断连、取消、传输/清理失败仍有界且不会成功返回。
- AC-003：新提交完整Go/race及Linux63项、1000次、双并发、故障/容量和最终回收通过；不得用sleep或重试隐藏失败。
- AC-004：源码改动限明确文件，精确SHA与证据可追踪，独立复核后再交回WORK-050；原服务和数据未被修改。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：已整理两轮实测、候选时序和明确修复边界，待用户审核
- 2026-09-10：意图闸通过：review → approved。原因：同意连续请求时序修复方案及实施边界
