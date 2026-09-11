---
id: "VERIFY-055"
type: "verify"
title: "为CI软件包下载增加有界同源地址回退"
status: "review"
work: "WORK-054"
owners: ["codex/root"]
depends_on: ["TASK-118"]
related: []
implements: []
verifies: ["ISSUE-018#AC-001", "ISSUE-018#AC-002", "ISSUE-018#AC-003", "ISSUE-018#AC-004", "ISSUE-018#AC-005", "TASK-118"]
tags: []
result: "fail"
created_at: "2026-09-11"
updated_at: "2026-09-11"
---

# VERIFY-055：连接回退已验证，完整CI仍被下载总期限阻断

## 验证对象

提交68c91d4866dafd3670f164008d2d336dcd117f0c，已推送origin/main；GitHub CI34580313653首次执行。

## 对应要求

AC-001至003通过本地及Linux基础测试与独立复核；AC-004原生任务全量下载成功，但另外两项Linux准备仍失败，整体恢复目标未达成。AC-005源码和已有证据复核通过，人工验收不就绪。

## 检查与结果

CI34571258539的3a74fc7三次尝试均失败在rootfs下载TLS握手；日志分别保存在/private/tmp/cherry-task112-34571258539-build、attempt2-build及attempt3-build目录（后二者使用同一前缀）。既有Go编译/指定Linux单测已完成，但未启动内核/原生/业务套件。最新本地官方源索引HEAD 200，两个IP上的同一cpp-13包HEAD也200。

本地直接解析archive.ubuntu.com并对每个地址进行TCP、SSL默认链/主机名验证、索引HEAD（socket5秒、4并发）：185.125.190.81=200/2.932s；185.125.190.82=TLS TimeoutError/5.251s；185.125.190.83=200/1.292s；91.189.91.81=200/1.079s；91.189.91.82=200/3.492s；91.189.91.83=200/1.578s。公开地址仅为本次诊断，不可抄作长期固定配置。

## 未通过项

本轮内核与业务任务在下载命令240秒总期限终止；没有启动相应套件，不能宣称下载阻断已全面解除。

## 范围检查

经用户意图闸passed及明确实施许可，改动限定下载入口、新transport与测试、CI prepare参数及测试/README。包锁、rootfs构建、安装器、业务与现有服务未改；仅已授权修复与记录提交推送。

## 遗留问题

GitHub旧日志没有失败peer，不能断言它命中了本地异常IP；原浏览器business.io失败也仍未定位。

## 剩余风险

源服务可能变化，多地址回退可能仍全部失败；不预先承诺恢复，失败不能算PASS。

## 结论

意图闸已由用户签署；连接回退确实有效，本轮原生全量下载/构建/10项测试通过，但两个Linux任务耗尽下载总期限。整体结果fail，TASK-118等待重审准备方案，WORK-050仍阻断，不冻结重构基线。

## 实施前文档检查（历史）

462份开发文档通过；在仅供检查的临时Git索引纳入新文档后，531份Markdown入口/链接通过，真实暂存区未变。常规链接检查在新文档未跟踪时会拒绝WORKS的新入口，这是跟踪状态要求，不代表缺少文件；本轮未提交推送。WORK-054 board确认意图闸可以签署，TASK-118仍todo，VERIFY结果pending。

## TASK-118本地实现与验证（2026-09-11）

先以旧标准库HTTPSConnection运行同一可控DNS/socket/TLS场景：首TCP成功、TLS超时、第二地址可用。/private/tmp/cherry-work054-old-transport.log为1FAIL，失败断言明确“TLS timeout prevented trying the next resolved address”。随后只替换连接类为AddressHTTPSConnection，同样期望与夹具通过；其他负例覆盖关闭失败socket、证书/未知异常终止、地址去重/8个上限、共同30秒与TCP+TLS每地址5秒、DNS迟返、HTTP发送/响应失败不重试、来源与重定向/代理边界。

新增连接适配仅由CI参数显式选择；默认download仍用urllib.request.urlopen。保留原索引/路径/流式大小/SHA256/resume逻辑，并以两种路径读取相同字节、HTTP/响应体/哈希/大小异常及非法文件负例核对。重定向附加64KiB响应体上限，防止标准库重定向处理无界排空；不影响原始未启用路径。

`python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work054-basic-final`：15安装+19rootfs+62CI=96项通过、零skip，Python AST及shell语法和报告校验通过。证据目录及/private/tmp/cherry-work054-basic-final.stdout保存完整结果。真实本地TLS使用临时OpenSSL证书与随机127.0.0.1端口：可信localhost成功，不可信链/主机名错误/过期分别得到SSLCertVerificationError；临时目录、连接、线程测试结束均关闭。默认工具沙箱曾禁止回环bind，允许受控端口后的相同测试通过，不把沙箱权限错误当实现失败。

开发中修正了测试里的Linux专用errno硬编码、OpenSSL过期证书生成方式和代理mock方式；上述最终完整运行已通过，尚未在Linux验证OpenSSL/标准库组合。随后用户已明确授权本工作独立复核、修正后commit/push main及运行处理GitHub CI，见下方复核记录。

新适配真实只读冒烟：以`source_urlopen('https://archive.ubuntu.com/ubuntu/')`请求noble主索引，最多读取2MiB，HTTP200/1401160字节；实际peer91.189.91.81、首次TLS成功。该单次本地读取不证明发生回退，也不等同全部包哈希验证或LinuxCI恢复。

## 独立复核与修正（2026-09-11）

用户本轮授权后，work054_review只读复核发现P2：带巨大Content-Length却截断的重定向响应可能在有界read短读后，仍被urllib默认处理器无界排空。已拒绝剩余声明长度，并在交回标准库前关闭响应；新增实际HTTPResponse和完整http_error_302路径，覆盖正常、chunked、无长度、超限和截断，检查底层读取大小、关闭及失败不跳转。复核者复测10项TransportTests通过，确认P2关闭、无剩余源码发布阻断；真实Linux证据仍待CI。

修正后运行`python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work054-reviewed-basic`，15安装+20rootfs+62CI=97项通过，零skip；Python AST、shell语法、报告检查通过。完整输出为/private/tmp/cherry-work054-reviewed-basic.stdout。代码与证据经过复核后发布，尚不把本地结果计为Linux下载恢复。

## GitHub Linux完整执行（2026-09-11）

[CI34580313653](https://github.com/charon2121/cherry-oj/actions/runs/34580313653)，attempt1，sourceSha=68c91d4866dafd3670f164008d2d336dcd117f0c，harnessSha=4e0df20a6145653ed28b9a93a0544af0b0e7544665895dfd4e25ba785ef15762。10个job中8PASS/2FAIL，无重跑；基础97测试与Go竞态、Web、契约、文档、legacy和原生任务通过。全部下载保持原锁、同源证书检查与240秒总期限。

| Linux任务 | 连接证据 | 下载与后续结果 |
|---|---|---|
| native | 58次CONNECTED、4次TLS TIMEOUT、4次attempt2成功 | 全部56包verified与锁文件名集合一致；rootfs构建和原生10项PASS，资源回收确认 |
| kernel | 58次CONNECTED、7次TLS TIMEOUT、7次attempt2成功 | 下载命令240秒终止；13条按输入顺序输出的verified；未构建rootfs、未执行内核/1000次/故障套件 |
| business | 58次CONNECTED、8次TLS TIMEOUT、6次attempt2与1次attempt3成功 | 下载命令240秒终止；8条按输入顺序输出的verified；未构建Java/Web或执行浏览器 |

native的packageLock=4dbcf4dd7025146ff44782024354b1868eacaaa313d53a57ded5fab247fb129c，与本地锁文件摘要匹配；rootfsManifest=ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0，与rootfs.log匹配。basic/native报告经过validate与verify_files核对源码、harness、用例和有界正规证据；native cleanup确认，进程、挂载、cgroup、文件和账号快照为空。

原生失败peer185.125.190.83一次、185.125.190.82三次，但二者在同轮也有成功；不能称为永久故障地址。并发连接日志没有requestId，不能把每次失败与紧邻的成功逐请求配对。ThreadPoolExecutor.map按输入顺序输出verified，13/8只表示已输出的证据数，不能作为实际完成包数。58次TLS成功不等于58次HTTP或完整下载成功；缺少逐包响应/字节进度，不能断定剩余慢包、响应阶段或吞吐原因，也不能归因旧三轮具体peer。

本地证据根/private/tmp/cherry-work054-34580313653：sandbox-basic、native/sandbox-native、native/sandbox-native-build、failed/sandbox-kernel-build、failed/sandbox-business-build/sandbox-build/logs。外层失败日志/private/tmp/cherry-work054-34580313653-failed.log明确两个download命令deadline exceeded；最终状态/private/tmp/cherry-work054-34580313653-status.json。失败job缺少套件报告的上传错误是准备未完成的后果，未执行套件不计PASS；只确认workflow清理步骤成功，不将缺失报告当完整业务清理证据。

## 下一步提议（未实施）

依据PLAN-038风险及DESIGN-048重审条件，当前策略未全面解除准备阻断，暂停扩张和盲目重跑。建议先审核一批仅诊断的改动：为每个锁定包记录固定包名/请求序号、HTTP响应头到达、读取字节数与单调耗时、哈希校验完成或固定失败分类；日志次数/字节有界，不输出正文、认证头或环境。保持同源、证书/哈希、包锁、4并发、30秒读取及240秒命令预算，不重放HTTP。取得逐包证据后，再决定是否需要下载调度或预算方案；不预先把增大超时当成修复。该提议需用户审核后另行明确实施边界。

## 变更记录

- 2026-09-11：状态变更：draft → review。原因：本地及Linux97项、独立复核与原生56包/10项通过；完整CI仍2项下载总期限失败，证据与归因限制记录完成，整体未恢复

## 本轮收束

work054_review独立核验本轮基础/原生完整证据及两个失败job，确认上述计数、240秒终止及归因限制。462份开发文档、531份Markdown链接和git diff --check通过。代码68c91d4已发布，CI后的失败/重审记录保留本地，未在红色CI基础上叠加发布。TASK-118已由工具置blocked；WORK状态置todo后校验通过，但refresh推导doing会因blocked任务使“修复任务”阶段未完成而被工具拒绝，保持合法状态并记录该工具限制，不手改flow或扩展本任务修改管理工具。

## 逐包诊断实施（2026-09-11）

用户已审核上节诊断提案并批准实施，设计/计划及TASK先补写diagnostics.py与diagnostics_test.py的精确边界。新增请求关联、阶段与有界进度；默认输出保持。新增正文超时用例在旧实现失败（missing request stage diagnostics，/private/tmp/cherry-work054-diagnostics-old.log），实现后通过。首次完整基础回归15安装+25rootfs+62CI=102项通过，证据/private/tmp/cherry-work054-diagnostics-basic及同名前缀.stdout；正在独立复核，本批尚未发布。旧失败仍保留，不把诊断完成当下载问题已解决。

独立复核work054_review发现并确认旧verified文本可与并发JSON交错，已在download.py共用诊断锁保持完整行；新增8包混合输出测试，复核者在独立进程仅去掉锁后同一测试失败，当前实现通过。新增索引解析成功/失败诊断测试。最终`python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work054-diagnostics-reviewed-basic`：15安装+27rootfs+62CI=104项通过、零skip，AST/shell/报告校验通过；462开发文档/531Markdown链接和git diff --check通过。复核无未解决阻断，按既有本工作发布与CI授权提交本批诊断；未修改原下载策略及限额。
