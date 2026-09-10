---
id: "DESIGN-045"
type: "design"
title: "修复沙箱连续请求完成与容量归还的竞态"
status: "checked"
work: "WORK-051"
owners: ["codex/root"]
depends_on: ["ISSUE-015"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# DESIGN-045：以资源归还完成作为请求收尾边界

## 背景

ISSUE-015记录两轮连接reset；直接证据是helper仍存活，源码存在完成确认先于slots归还的窗口。实施中已完成旧客户端在完成帧后提前成功的旧红新绿对照；服务端受控次序和实际 Linux 整链仍需验证，见 VERIFY-052。

## 目标与限制

修复合法串行请求被误拒绝的问题，同时保留真正超额拒绝、完整资源回收、输出交付和慢客户端上限。只改helper连接生命周期及客户端收尾，不重构整个Container/pool，不修改namespace/cgroup/权限、判题和限额。

## 整体方案

先以真实Unix socket和可控制收尾进度的内部函数测试确定时序；测试不能依赖恰好抢到调度窗口。优先采用连接结束作为完成屏障：server在serveConn所有资源/取消处理返回后先归还执行槽，再关闭连接；client验证原Completion后，继续验证正常EOF才交还上层。客户端现有上下文取消和连接期限必须覆盖新增等待。

该候选保持原JSON字段，但收紧本机协议的完成语义。不得仅把slot提前释放到可能阻塞的输出交付之前，这会允许慢客户端累积无界连接/goroutine；也不得只调换server defer而让client仍在Completion处提前返回。实现前先核验所有return、fatal、cancel与defer顺序，必要时将连接收尾抽成一个有明确职责的内部函数。

## 模块与数据

生产候选文件仅helper/client.go、server_linux_amd64.go及对应测试、README。测试使用现有net/context/Go标准库，不新增依赖或生产测试开关。完整Go测试与CI的Linux必需测试名清单同步增加新的回归项。

实施核对发现测试私有客户端 tests/client.py 和 tests/smoke.py 也在 Completion 处提前返回。为使同一构建的测试客户端遵循相同完成语义，精确补入这两处 EOF 校验；沿用现有 12 秒 socket 期限，额外字节与 reset 均失败，既有资源和安全断言全部保留。它们没有部署或业务职责。

## 接口与状态

本机Completion字段/version不变；成功要求完整事实、产物、Completion和正常连接结束。缺Completion、尾部垃圾、超时、取消或连接重置均不得解释为成功。server正常结束前必须确保资源关闭和slot恰好归还一次；失败时仍正确处理fatal与listener停止。旧新二进制不混用，后续通过同一release安装与正常新环境身份注册。

## 安全与失败

同时检查slot和连接生命周期是否有界。验证占满时仍快速拒绝、新请求不能在旧资源未回收前复用UID/workspace、慢读者不能累积工作线程；不为了通过CI增加Parallelism或放宽deadline。若EOF方案暴露现有协议客户端兼容或无法维持连接上限，暂停该方案并更新DECISION，不自动采用无界等待队列。

## 验证

旧顺序红/修复后绿的确定性测试；缺尾帧/额外字节/不关闭连接/主动取消/写失败/清理失败/容量饱和正反例；完整Go race；当前完整sandbox-kernel（含1000次、并发、故障与清理）。使用精确新SHA，不重新标注已失败运行。

## 监控与部署

本工作只在一次性GitHub VM验证。不得热替换现有节点；修复会改变helper/sandbox摘要，后续WORK-050原生与业务套件正常生成新身份和校准，不复制旧身份。外部业务schema、rootfs与策略不变。

## 备选方案

可将完成确认延后到slot归还后，但需另证传输连接仍有独立总量上限；不作为本轮默认。增加sleep、客户端自动重试、扩大槽位或放宽测试均不选。

## 风险与重审条件

当前根因尚需确定性测试最终归因；新增EOF等待可能暴露旧客户端替身不关闭连接的问题，须根据真实协议修正替身而非跳过测试。若需改公开schema、执行器、cgroup或更多生产文件，先升级边界与设计。

## 迁移与兼容

本机client与helper按同一构建配套交付，不承诺旧新混用；不增加历史兼容分支。公开业务请求和响应字段不变，沿用现有部署摘要验证。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：已形成有界收尾候选与验证计划，尚未实施
- 2026-09-10：结构与内容校验通过，由工具置为 checked。
