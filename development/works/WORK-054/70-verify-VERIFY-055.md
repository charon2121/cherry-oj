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

# VERIFY-055：600秒两轮试验仍有慢包超时，整体恢复未通过

## 验证对象

最新试验提交d795c7fb01f6ab412af6e66e31a624c9addea46b已推送main，仅CI下载总期限240→600秒；完成两轮CI34584984978、34586094583。原68c91d4连接回退和d001f03诊断及对应失败证据保留。

## 对应要求

AC-001至003通过本地及Linux基础测试与独立复核；AC-004前轮原生全量成功，本轮内核/业务全量成功但原生下载仍超时，整体稳定恢复目标未达成。AC-005源码和已有证据复核通过，人工验收不就绪。

## 检查与结果

CI34571258539的3a74fc7三次尝试均失败在rootfs下载TLS握手；日志分别保存在/private/tmp/cherry-task112-34571258539-build、attempt2-build及attempt3-build目录（后二者使用同一前缀）。既有Go编译/指定Linux单测已完成，但未启动内核/原生/业务套件。最新本地官方源索引HEAD 200，两个IP上的同一cpp-13包HEAD也200。

本地直接解析archive.ubuntu.com并对每个地址进行TCP、SSL默认链/主机名验证、索引HEAD（socket5秒、4并发）：185.125.190.81=200/2.932s；185.125.190.82=TLS TimeoutError/5.251s；185.125.190.83=200/1.292s；91.189.91.81=200/1.079s；91.189.91.82=200/3.492s；91.189.91.83=200/1.578s。公开地址仅为本次诊断，不可抄作长期固定配置。

## 未通过项

最新轮原生任务下载命令240秒终止，仅一个GCC包没有正文结束/校验终态；业务本轮进入真实页面但io失败。不能宣称下载阻断已全面解除或业务闭环通过。

## 范围检查

经用户意图闸passed及明确实施许可，改动限定下载入口、新transport与测试、CI prepare参数及测试/README。包锁、rootfs构建、安装器、业务与现有服务未改；仅已授权修复与记录提交推送。

## 遗留问题

GitHub旧日志没有失败peer，不能断言它命中了本地异常IP；原浏览器business.io失败现定位到support.ts:45:24，具体失败原因待TASK-112读取源码继续核对。

## 剩余风险

源服务可能变化，多地址回退可能仍全部失败；不预先承诺恢复，失败不能算PASS。

## 结论

用户已批准意图、逐包诊断及方案1期限调整；本轮600秒试验完成，两轮6台VM冷下载4成功、2在600秒仍超时。四个成功样本整体准备均少于240秒，未取得延长期限改善完成结果的证据；方案1单独不足以稳定恢复。整体result=fail，TASK-118等待其他慢响应策略重审，TASK-112业务io及新观察的内核采样问题仍需处理，不冻结重构基线。

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

## 连接回退首次GitHub执行（2026-09-11）

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

## 逐包诊断GitHub实测（2026-09-11）

[CI34582727482](https://github.com/charon2121/cherry-oj/actions/runs/34582727482)，attempt1，source=d001f03b21622dd68dc2bf2f390d12d6b567105c，harness=beb989c1501987770d77b533364733162133109e0214d46ca44dd03fe2e83edf。最终8job成功、2job失败；本轮只增加诊断与输出互斥，没有更改下载预算、并发、读取语义或HTTP重试。不能将本轮部分成功归因于诊断提高了传输能力。

| 任务 | 下载诊断 | 后续结果 |
|---|---|---|
| basic | Linux上15安装+27rootfs+62CI=104测试通过，零skip | 5项报告PASS，源码/harness/证据核验通过 |
| kernel | 82,283字节日志，56个包唯一verified、两个索引index_ready；58CONNECTED/10TIMEOUT | 63项内核用例PASS，包括1000次执行、并发、故障与回收；cleanupPASS |
| native | 78,433字节日志，55个包verified、两个索引index_ready，仅request16没有完成终态；58CONNECTED/8TIMEOUT | 外层240秒下载期限终止，没有rootfs构建和原生套件报告 |
| business | 80,241字节日志，56个包唯一verified、两个索引index_ready；58CONNECTED/18TIMEOUT | Java/Web/Chromium准备通过；环境/部署/校准3PASS、ioFAIL、11NOT_RUN；最终cleanupPASS |

三份下载日志每行可解析，无截断标记，requestId和包名一致；进度符合每请求16次/至少5秒。kernel与business所有完成包名匹配原56包锁，build.json packageLock均与本地4dbcf4dd7025146ff44782024354b1868eacaaa313d53a57ded5fab247fb129c匹配。完整报告由validate/verify_files核对；失败业务报告使用successful=False校验结构/源码/证据，不将其算为成功套件。

### 原生失败已定位的事实

request16=`gcc-13-x86-64-linux-gnu_13.3.0-6ubuntu2~24.04.1_amd64.deb`：首次TLS到91.189.92.24约2.033秒成功，4.073秒收到HTTP200。正文进度持续增加，84.299秒记录11,141,120bytes后达到16次上限；没有verify/verified或固定异常事件。原生外层日志明确download命令deadline exceeded，prepare.py保持240秒，因此是唯一仍未完成的包占用到总期限。

相同锁定包完整大小21,084,546bytes：kernel先经历一次5秒TLS超时，再连接91.189.91.83，总5.525秒完成；business总0.731秒完成。同一轮各VM耗时差异显著，但此对照没有控制VM或网络路径，不能认定特定IP永久慢或唯一网络原因。可确认失败发生在收到响应头后的正文处理阶段，非纯TLS握手问题；body仍包含读取/摘要更新/文件写入。进度达上限后未继续输出，11,141,120不是最终读取量，不表示84秒后完全停止。kernel另一约2.46MB binutils包headers2.525秒、verified103.557秒，中间持续有进度，进一步说明正文阶段可明显慢于建连。

### 业务诊断交回

本轮真实MySQL认证8项全部PASS；部署与校准后浏览器首个io用例失败。脱敏browser-diagnostic.json为phase=io、status=failed、support.ts:45:24；报告business.io=FAIL，剩余11项NOT_RUN。最终cleanup.json confirmed=true，原生任务进程/挂载/cgroup/工作文件/账号快照为空，dependencies-after的container/volume均为空。报告中business.cleanup用例因浏览器提前失败保持NOT_RUN，与finally实际回收PASS分开记录。当前TASK-118未读取或修改apps源码，不凭源码位置猜测业务原因，交给TASK-112后续核对。

### 证据与后续边界

证据根/private/tmp/cherry-work054-34582727482：sandbox-basic、kernel/sandbox-kernel及sandbox-kernel-build、native-build、business/sandbox-business及sandbox-business-build。外层失败日志/private/tmp/cherry-work054-34582727482-failed.log，最终状态同前缀-status.json。本轮诊断已达到定位未完成包与阶段的目标；完整CI仍失败。下一步应单独审核慢响应处理（包含预算、是否允许有界重取等明确取舍），不能把现有“HTTP发送后不重试”约束自行改掉，也不自动提高240秒。准备缓存/集中构建可减少重复下载，但冷下载依然需要明确处理，不能直接宣称它解决本次慢响应。

## 方案1实施准备（2026-09-11）

用户已阅读四种处理方案并明确选择方案1，先在上游记录仅CI下载总期限240→600秒的有限例外再改代码。prepare.py一处期限和prepare_test.py既有断言/命名更新，README同步；来源、锁、TLS、并发、读超时、HTTP不重放及其他预算保持。work054_review独立复核通过，确认外层15/20/40分钟仍是限制，不保证所有步骤同时达到各自上限。

`python3 -B deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work054-600-basic`：15安装+27rootfs+62CI=104项通过、零skip，AST/shell/报告检查通过。462开发文档、531Markdown入口与git diff --check通过。本批按本WORK已有发布/CI授权提交后进行两轮同SHA冷下载试验，保留全部成功与失败；此前下载与business.io失败不覆盖。

方案1提交d795c7fb01f6ab412af6e66e31a624c9addea46b已推送，harness=f00b22736dc1317958a4c5a3e61e14240e4d187ed66c77586fc61a55107679d0。首轮CI34584984978的104基础检查通过；kernel全部56包与rootfs完成，最慢包50.182秒，但后续static-identity读取/proc/8376/ns/mnt时FileNotFoundError退出。报告6PASS/4FAIL/53NOT_RUN，cleanup与资源快照通过；独立复核确认不能由此推出namespace越界，也不能忽略该失败。具体进程消失原因由后续拥有测试源码边界的任务调查，本批不修改测试。其余结果与第二轮待完整取证后补充。

方案1首轮34584984978最终7job成功/3失败。原生download命令明确deadline exceeded，prepare.py本SHA为600秒；55包verified、两个索引index_ready，仅GCC request16正文未完成。peer91.189.92.24首次TLS2.114秒成功，4.145秒HTTP200，100.659秒最后一条受限进度2,883,584bytes，此后已达16次记录上限，不等同最终读取量。600秒没有让该样本完成，已证伪“仅延长到600即可稳定恢复”的承诺；kernel/business全56包完成不改变此结论。第二轮按原计划以同SHA独立workflow_dispatch34586094583继续，不覆盖首轮结果，不因失败继续抬高期限。

## 方案1两轮最终结果（2026-09-11）

两轮同source=d795c7fb01f6ab412af6e66e31a624c9addea46b、harness=f00b22736dc1317958a4c5a3e61e14240e4d187ed66c77586fc61a55107679d0，均attempt1；第二轮是预先约定的独立workflow_dispatch，不覆盖首轮。未启用包缓存/resume，各VM均从空包目录下载。

| 轮次及CI | VM | 包校验 | 整个构建准备步骤耗时 | 后续结果 |
|---|---|---|---|---|
| [第一轮34584984978](https://github.com/charon2121/cherry-oj/actions/runs/34584984978) | kernel | 56/56 | 115秒 | 6PASS/4FAIL/53NOT_RUN；static-identity读取/proc路径消失；cleanupPASS |
| 第一轮 | native | 55/56 | 631秒，下载命令600秒到期 | 原生套件未运行 |
| 第一轮 | business | 56/56 | 189秒 | 3PASS/1FAIL/11NOT_RUN；io support.ts:45:24；cleanupPASS |
| [第二轮34586094583](https://github.com/charon2121/cherry-oj/actions/runs/34586094583) | kernel | 56/56 | 142秒 | 63PASS，包含1000次/并发/故障；cleanupPASS |
| 第二轮 | native | 55/56 | 632秒，下载命令600秒到期 | 原生套件未运行 |
| 第二轮 | business | 56/56 | 102秒 | 3PASS/1FAIL/11NOT_RUN；io support.ts:45:24；cleanupPASS |

耗时来自GitHub步骤startedAt/completedAt，包含构建、Linux单测、下载和rootfs；不是纯下载耗时。600秒来自相同源码prepare.py的download命令及两个外层deadline exceeded证据，不是根据job总时长估算。首轮所有job为7成功/3失败，第二轮8成功/2失败。两轮104项基础测试均通过；四份完整build.json的source/harness/包锁与原锁一致，56个唯一verified与锁匹配，两份失败日志均55个唯一verified、一个没有终态的request16及两个index_ready，日志未截断。

两次唯一未完成包均gcc-13-x86-64-linux-gnu_13.3.0-6ubuntu2~24.04.1_amd64.deb（其他VM完整内容21,084,546bytes）：第一轮peer91.189.92.24，TLS约2.114秒/HTTP200约4.145秒，最后受限进度100.659秒/2,883,584bytes；第二轮peer91.189.92.23，TLS约1.873秒/HTTP200约3.808秒，最后受限进度91.643秒/4,456,448bytes。均已进入正文处理后未完成；16次进度上限后的字节量未知，不能把最后进度当最终读量，或断定其后完全停滞。没有证据把问题唯一归因于网络或磁盘，也不能硬编码/屏蔽这些IP。

四个下载成功VM的整个准备步骤均小于240秒，因此本轮没有“超过240秒但在600秒内成功”的正例。两次600秒超时反例已足够说明仅增加等待不能稳定解决；4/6只是这六个样本的观察数，不是总体成功率估计。不再追加第三轮或继续抬高预算。

业务两轮认证/MySQL准备通过，均在同一浏览器io位置失败且最终清理通过；内核第二轮通过不能撤销第一轮进程采样失败。第一轮四个关联身份/namespace/挂载/限额用例因检查未完成记FAIL，不代表四种隔离分别已被证明失效。相关测试源码修复不在本批范围，留WORK-050继续调查。

证据根/private/tmp/cherry-work054-34584984978、/private/tmp/cherry-work054-34586094583：首轮kernel、business、native-build和sandbox-basic；第二轮completed、business和native-build。每轮外层失败日志和最终状态保存在同根前缀-failed.log/-status.json。已发布的600秒配置保留，验证记录更新本地；本轮不自动回退用户已选择的配置，也不扩展缓存、源或HTTP重试。下一方案另行审核，人工验收保持未通过。
