# 沙箱回归 CI

本目录把 WORK-048 的已验收断言组织为 WORK-050 的重构回归。`cases.json` 是必需用例清单，
记录稳定 ID、原断言来源与执行组；人工复核、重启和其他平台在 exclusions 中单列，不能计为通过。

普通开发机可运行（输出目录必须不存在）：

```sh
PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /tmp/cherry-ci-basic
```

基础套件覆盖安装器、rootfs 构建器及 CI 自测、Python AST 和 shell 语法。真实 Linux 套件只面向
一次性、独占的 GitHub Ubuntu VM；普通 Go 测试通过或交叉编译都不能替代它。当前套件实施与
真实 Actions 验证进度以 WORK-050/VERIFY-051 为准。

`report.py` 定义内部报告 schemaVersion=1：顶层严格包含 suite、sourceSha、harnessSha、runId、
startedAt、finishedAt、environment、cases 和 cleanup。每个 case 只含 id/status/evidence/details；
status 为 PASS、FAIL、ENVIRONMENT_ERROR、CANCELLED 或 NOT_RUN，默认 NOT_RUN。
sourceSha 必须匹配本次 checkout，harnessSha 覆盖非忽略的部署/测试脚本内容，case ID 必须与清单
完全相符。首次结果不可覆盖，必需用例全部 PASS 且 cleanup PASS 才能成功。

报告 JSON 不超过 1 MiB，单套件证据总计不超过 20 MiB；日志写入器另在运行时限制输出。
证据必须是非空普通文件，禁止链接、路径逃逸和缺失文件。汇总必须同时检查 job 状态及报告，
取消、前置失败、没有报告均不能成为绿灯。报告只保存测试事实，不保存凭据、Cookie 或私钥。

新增场景需要同时增加清单、执行断言及报告映射。更改测试含义须先更新 WORK 的设计依据；
不能通过删 case、放宽阈值、host 回退或重试整个场景掩盖第一次失败。

## Linux 内核套件

工作流中的 `sandbox-kernel` 先由普通 runner 用户运行 `prepare.py`，构建本提交的二进制、
Go 边界测试和锁定的 56 包 rootfs。它执行 Linux 专属包的 race 测试并拒绝任何跳过，下载器逐包
校验 SHA256；准备日志独立保存。当前使用冷包目录，尚未启用 rootfs 缓存。

随后 `kernel.py` 在一次性 VM 内通过 sudo 编排已有测试，检查实际 systemd/内核/LSM、控制器与
资源冲突。运行入口要求 GitHub-hosted 的环境与运行 ID、Linux/amd64 和 root；不可指向 SSH
服务器。缺能力直接失败。所有脚本的 CPU、内存、线程和时间预算沿用既有测试。

每批单元包含 GitHub run ID 与 attempt。`.ci-owner.json` 以 root 私有权限登记单元后才启动；
`finally` 和工作流 `always()` 都调用同一所有权清理逻辑。只停止已登记单元，检查全部存活进程的
身份、挂载及执行 cgroup，确认无残留后才删除本轮夹具。遇到未知占用或残留时保存事实并失败。
运行时长及 VM 销毁提供最后托底，但不能补造清理通过记录。

`results.py` 要求 Linux 专属 Go 测试、四组实际边界测试及 Python 逐模式标记出现；边界夹具的
`TestExecFailureChild` 在非子进程模式正常返回，不设置跳过例外。旧手动脚本默认参数保持有效，
CI 仅为 `chain_batch.py` 增加可选独立单元名，为零限额场景增加结果记录。

## 原生部署套件

`sandbox-native`在另一台一次性VM上构建同一源码和锁定rootfs，再由`native.py`调用原安装器。
十项case覆盖安装、线程/namespace/24项限额、三种缺文件、三服务在途崩溃、七项capability删减及
卸载/恢复。原`verify-*.py`断言和预算不变，`native_results.py`要求实际结束标记及测量值完整。

`native_control.py`只作为有界loopback协议接收器，核验新节点注册、心跳和恢复后身份不变；令牌每轮
生成并仅留于私有临时目录。它不是Java后端，不能凭此宣称数据部署、校准或真实业务已验证。
完整业务由TASK-112另行实现。

安装前除普通预检外，再拒绝已有固定单元文件、drop-in、账号及目录。`.native-owner.json`先记录
本轮安装声明及单元摘要，再调用安装器，覆盖尚未写完安装回执的失败。清理先停止本轮验证驱动，
检查固定单元来源和已知临时capability配置，再停止原生服务并核验所有进程的挂载与任务身份；
有残留或未知改动即失败。卸载保留数据的语义先由原验证脚本断言，随后CI仅删除本轮创建且身份
仍匹配的账号/目录/单元，保存`native-resources-after.json`。finally和always使用同一入口；
正常报告只有完整清理后才能PASS，VM销毁不算清理证据。没有enable、整机重启或现有服务连接。

权限删减对照会主动造成连续启动失败。systemd阻止新启动时可能仍保留上一次`Result=exit-code`，
因此不能仅以“不是start-limit-hit”证明实际执行。每个对照前仅清除本轮helper的失败计数，
不修改部署的启动频率配置、不重试当前对照；逐次要求新的InvocationID和主进程启动时间，
报告必须含8个不同启动实例（完整权限正例+7个删减）。恢复原配置并核对摘要后同样清除主动
失败历史，再运行原启动与恢复断言。该处理仅属于测试夹具，不进入生产管理器。

## 真实业务套件（TASK-112）

`sandbox-business`在第三台独占VM运行真实全栈：`business_prepare.py`按Maven wrapper/JDK21和
Node24构建五个Java服务与生产Web；打包时跳过Java单测只作为构建准备，不计作测试证据。
`business.py`通过正常安装器注册新Linux节点，调用正常bootstrap、登录/改密/CSRF、题目、
数据部署、校准及发布接口，然后运行`apps/web/playwright.live.config.ts`的真实页面测试。
不使用`native_control.py`协议接收器，不启动旧node-e2e或用户Compose。

- `business_config.py`只生成本轮凭据、独立四库初始化与显式test配置。
- `business_resources.py`管理带运行标签的三个Docker容器/卷；`business_stack.py`管理有界非root Java/浏览器systemd单元。
- `business_api.py`生成六对公开A+B数据，通过API准备。`business_evidence.py`用固定只读事务核对节点会话、数据摘要、独立校准和Kafka两端Outbox/Inbox。
- `business_observer.py`只读观察CPU/MLE同次Main及执行组；1ms目标采样、记录实际最大间隔。缺OOM事实、采样间隔达到100ms或缺执行退出记录直接失败；它不把HTTP耗时算用户程序墙钟。
- `business_results.py`核对15项清单中11项页面结果；缺项、普通SIGKILL误映射、OLE后峰值串用、非6/6 AC、重复正式提交或历史草稿丢失都不能通过。

每轮私有配置和应用日志只在`/var/lib/cherry-sandbox-test/business`，不进入产物。Playwright
单worker/零重试/无trace或截图，单次自定义观察60s，正式提交只创建一次。原生后端限额不变；
整轮业务驱动20分钟期限，服务/容器/浏览器分别限制CPU、内存、swap和线程，详细预算见DESIGN-044。
所有已登记资源即使某部分清理失败也继续分别回收；未能回收时保留所有权记录，报告失败，不能靠VM销毁补造PASS。

本地只运行`basic.py`、类型/lint和`playwright --list`，不在Mac启动资源耗尽夹具。完整业务结果
必须来自审核后本批GitHub运行；当前实跑状态见WORK-050/VERIFY-051，不把用例已编写当作闭环已通过。

### 认证期限数据库回归（WORK-053）

`business_prepare.py`在打包前显式clean并运行AuthenticationServiceTests与UserPersistenceIntegrationTests。后者以带纳秒尾数的固定时钟执行实际authenticate→MySQL→validate/exchange，覆盖原有过期/撤销/固定期限行为；本地只运行无数据库单测和编译，数据库只在一次性Linux VM执行。MySQL夹具1GiB/swap0/1CPU/256进程，日志1MiB×2；测试JVM堆512MiB，Maven堆768MiB，600秒命令期限。

两份Surefire XML仅解析固定测试名及状态，不上传properties、system-out或失败正文；摘要保留当前source/harness。缺文件、重复、缺方法、计数不一致、skip、失败及命令非零都阻断业务准备，失败摘要仍保留。构建清单携带摘要，由业务入口再次核验。不能把打包的-DskipTests或Docker不可用时自动跳过计为数据库通过。生产精度修复须先获得真实MySQL旧红，实测进展见WORK-053/VERIFY-054。

浏览器失败时，`browser-diagnostic.json`只导出固定阶段、状态和测试源码行列；不导出Playwright错误正文、动态标题或调用参数。`reporter-check.mjs`在业务构建准备中验证过滤与数量上限；损坏诊断拒绝导出，原失败及清理仍保留。

WORK-054：CI准备显式传入`rootfs/download.py --address-failover`。仅在HTTP发送前，对同一HTTPS源解析出的不同地址尝试连接/TLS，每地址最多5秒、最多8个，共享原30秒连接期限；下载命令按用户批准的WORK-054方案1由240秒调整为600秒，验证冷下载等待预算是否足够。证书拒绝、HTTP错误、响应体错误与摘要不符不重试。默认部署调用不启用，不修改系统DNS/代理/包锁；连接日志只含公开域名/peer、阶段、固定结果和耗时，不能据此将最终失败或未运行套件算PASS。

启用路径还为索引和每个包记录本轮requestId、包名、连接尝试、响应头到达、正文进度与摘要验证阶段。时间为单调ns、读取量为bytes；不输出响应头内容、正文或异常文本。共享诊断预算256KiB，超限后只输出一次截断标记；每请求首次有进度及其后至少5秒记录，最多16次，固定阶段与终态单列。默认部署路径不产生这些事件。逐包verified事件在实际完成时输出，旧verified文本仍按锁顺序输出。被命令期限终止且缺少终态的请求只能判断已到达的最后阶段；body阶段包含读取、摘要更新和文件写入，不能只凭停在body宣称是网络吞吐问题。
