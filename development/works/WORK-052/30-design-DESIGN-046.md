---
id: "DESIGN-046"
type: "design"
title: "修复沙箱启动通信被信号中断时的处理"
status: "checked"
work: "WORK-052"
owners: ["codex/root"]
depends_on: ["ISSUE-016"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# DESIGN-046：仅在控制消息系统调用层处理可恢复中断

## 背景

ISSUE-016记录读取事件过程中真实EINTR失败。实施核对发现日志未标明Poll或ReceiveEvent，撤回对Recvmsg的确定归因；原有通过记录不能排除该失败。

## 目标与限制

只修正本机seqpacket控制消息收发，保持消息结构、namespace/cgroup/身份、预算和判题结果映射不变。EINTR不等于取消或成功；先确认无消息或FD已被部分交付，再决定继续。

## 整体方案

提议在channel_linux.go集中处理unix.Recvmsg/SendmsgN的EINTR，不在业务或测试调用方重跑请求。通过窄的包内可替换调用适配器建立确定性中断序列，优先不用全局可变测试钩子；真实Linux再以受控信号交叉验证。恢复仅限未完成的系统调用，其他错误立即按原语义返回。每次重入必须保持FD所有权和关闭协调，不使用已释放且可能复用的裸FD。不能仅写无条件for循环而忽略调用者取消与期限。

实现前追踪Go运行时、os.File关闭/原始描述符、helper关闭控制通道与init停止的关系。若需引入新的协议字段、改变执行状态机或外层预算，回到设计重新审核；不得借重试“修复”尚未理解的错误。

## 模块与数据

生产仅launcher/channel_linux.go，包内channel_linux_test.go及已有files_linux_test.go负责收发边界；Linux边界夹具增强观测与信号控制，并仅在自己的Poll等待层恢复EINTR，固定原两秒截止时间；不重试生产ReceiveEvent或整个场景。CI cases.json同步必需Go测试名。没有数据迁移或新增依赖。

## 接口与状态

SocketPair/SendEvent/ReceiveEvent签名、协议版本、FD数量和CLOEXEC约束保留。成功仍要求完整合法消息；EOF、短写、非法帧和截断不是可重试中断。

## 安全与失败

验证一次消息附带FD只交付一次，解析失败关闭全部接收FD；关闭/取消能结束等待；连续中断仍受调用链原期限约束，资源和启动错误不能映射成TLE/MLE。不能把系统调用级继续变成用户请求级重试。

## 监控与部署

仅一次性GitHub Linux VM；记录精确SHA、实际内核、信号注入和回收。当前服务器与IDEA不变；未来新构建按正常新节点身份及校准交付。

## 迁移与兼容

同构建helper/init配套交付；外部契约不变，不增加旧内核兼容层。

## 备选方案

让测试捕获ReceiveEvent的EINTR再试会隐藏生产路径失败，不采用；测试自身Poll的中断在独立等待层按剩余期限处理。改成全新网络或启动协议超出最小范围，不作为默认方案。

## 风险与重审条件

若无法确定消息/FD未交付，或不能证明关闭和期限仍有界，暂停候选。具体信号来源尚未确定，不把推断写成事实。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：提出系统调用层有界中断处理及FD关闭约束，尚未实施
- 2026-09-11：结构与内容校验通过，由工具置为 checked。

- 2026-09-11：实施先增加既有边界测试的poll/receive错误标记，并用独立5秒子进程、线程定向待决SIGUSR1及Ppoll原子解屏蔽诊断等待阶段中断；随后检查消息/FD及EOF。这只是候选归因验证，不忽略原场景错误、不增加重试。生产候选暂不实施，待真实Linux证据；当前材料不把原归因当事实。

- 2026-09-11：实施细化：单次sendmsg/recvmsg通过os.File.SyscallConn.Control持有描述符引用，每次EINTR后释放再重取，从而观察Close并避免编号复用；发送同时保护附带FD。仅在无数据/附带FD交付时恢复，其他错误不恢复，接收错误先关闭已收到的FD。现有阻塞控制socket由helper在取消、startup/墙钟期限后shutdown，再回收；单独Close不承诺唤醒尚未返回的阻塞syscall，不能删除既有shutdown链。SO_RCVTIMEO仅用于测试触发真实recvmsg中断，不新增生产socket期限配置。
- 2026-09-11：在修改前同步TASK/PLAN边界，允许测试自身Poll恢复EINTR但始终按原两秒绝对截止时间计算剩余等待；ReceiveEvent仍只调用一次、错误不被测试吞掉。此处与生产收发分别验证，避免混淆历史归因。

- 2026-09-11：b3e5ec0的CI34557663857内核63项/52个必需Go测试通过，但原生安装驱动在128MiB封顶处OOM，服务尚未安装。为完成原定完整CI验收，先增加仅CI的安装资源观测：由单元外驱动读取本次已登记安装cgroup的memory.current/peak/stat/events，固定频率、样本数量与字段，失败仍失败并沿原清理链退出。新增ci/memory_watch.py、memory_watch_test.py及native.py接线；不改安装器、生产、资源预算或重试。观测仅定位匿名内存/文件缓存等来源，轮询值不能冒充准确峰值；仅memory.peak为内核峰值。如发现安装器缺陷，另行冻结修复边界。
