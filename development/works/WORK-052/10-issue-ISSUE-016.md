---
id: "ISSUE-016"
type: "issue"
title: "修复沙箱启动通信被信号中断时的处理"
status: "approved"
work: "WORK-052"
owners: ["codex/root"]
depends_on: []
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# ISSUE-016：启动通信遇到短暂中断时不应误报失败

## 为什么做

同样的程序和隔离配置，有时在启动消息交接时失败。需要分清短暂中断与真正的执行故障，让正常请求可靠完成，同时保持取消、超时和资源回收规则。

## 问题现象

WORK-050的CI34503720792在Linux边界wrong-go场景读取启动事件时报interrupted system call，未得到预期go-handshake错误事件。不是用户程序的TLE/MLE，也不能因之后某轮通过而关闭此问题。

## 复现方式

提交a0aea384a810c4f2aa81d4176fbc11cf62ceb7d2，运行ci.yml的sandbox-kernel。失败位于start_linux_test.go:166调用event()；event内部依次调用unix.Poll及launcher.ReceiveEvent，两者错误均经t.Helper归到该行。尚无每次必现的最小复现，实施第一步须建立确定性旧红新绿，不以重复运行碰运气代替。

## 实际结果

TestStartupBoundaries/wrong-go失败；kernel批次中止，后续场景未运行，最终清理通过。源码channel_linux.go:43直接将Recvmsg错误返回；尚不能从日志确认是哪一个信号触发，也未证明生产正常请求必然能遇到相同触发条件。

## 预期结果

- REQ-001：确认收发消息被信号中断的语义，在可安全继续时继续当前消息，不能误判用户结果。
- REQ-002：继续保留取消、退出、超时、短写、截断、非法帧及FD检查；不得无界重试整请求或绕过隔离。
- REQ-003：当前源码的完整工程CI、内核及原生部署重新通过，保留失败和回收证据。

## 影响与条件

本机helper与可信init的seqpacket控制通道；实测Ubuntu托管VM/Linux6.17.0-1022-azure/amd64。其他平台和用户服务器未验证。

## 原因

实施核对确认：生产ReceiveEvent直接返回EINTR，但原日志不能区分测试Poll与生产Recvmsg，不能认定该生产路径导致这次CI失败。x/sys v0.46.0的Poll直接委托Ppoll且没有中断恢复；两处均需分阶段验证，具体信号来源未知。

## 修复方向

先固定中断时序与错误语义，再只修改消息系统调用的中断处理。不能在测试外层忽略EINTR或重新执行整个场景。

## 回归检查

- AC-001：确定性中断用例旧实现失败、修复后成功，核验消息与FD恰好一次交付。
- AC-002：正常错误、取消/关闭、期限、截断和非法FD均保持失败与完整回收，中断序列不延长外层预算。
- AC-003：本地race/vet、Linux必需Go、63项内核/1000次/并发/故障、10项原生及完整工程CI在对应SHA通过。
- AC-004：独立复核有界性、FD生命周期及精确边界，原节点/身份/业务未修改，人工验收后交回WORK-050。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：记录真实EINTR失败、待验证原因与最小修复验收标准，供人工审核
- 2026-09-11：意图闸通过：review → approved。原因：确认控制消息中断修复范围及验证方案，允许实施

- 2026-09-11：核对原始失败日志、t.Helper调用链和x/sys源码，纠正先前把EINTR确定归因为生产Recvmsg的过度推断；需求与验收标准未变。
