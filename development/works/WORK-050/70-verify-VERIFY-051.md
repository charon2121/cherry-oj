---
id: "VERIFY-051"
type: "verify"
title: "将沙箱已验收回归固化为重构 CI"
status: "review"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-109", "TASK-110", "TASK-111", "TASK-112", "TASK-113", "TASK-115"]
related: []
implements: []
verifies: ["CAPABILITY-008#AC-001", "CAPABILITY-008#AC-002", "CAPABILITY-008#AC-003", "CAPABILITY-008#AC-004", "CAPABILITY-008#AC-005", "CAPABILITY-008#AC-006"]
tags: []
result: "pending"
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# VERIFY-051：回归 CI 验证记录

## 验证对象

CAPABILITY-008 的用例映射、真实内核/原生部署/业务自动化、汇总可靠性及重构交接。

## 对应要求

AC-001～006分别对应清单、内核、部署、业务、失败/清理汇总以及两次基线。基础与Linux内核Actions已有实际执行证据；已接入的8项CI在测试编译期限调整后通过，原生部署、业务与汇总尚未完成。下方按轮次保留历史状态与失败，不把旧记录视为当前结论。

## 检查与结果

2026-09-10只读盘点现有ci.yml及WORK-048最终证据：当前CI六job已成功，Linux边界测试依赖显式环境，普通Go运行不等于已执行；legacy容器是trusted-host，现有custom-run浏览器测试模拟响应。部署15项/rootfs6项本地Python单测未接CI；真实TASK-100夹具含旧固定ID及手工登录前提，需要独立环境驱动。

已创建分层方案与TASK边界，首轮目标托管Ubuntu24.04/amd64，实际内核与LSM须运行时记录。未连接或修改现有服务器，未安装软件、写workflow或运行压力/部署/业务场景。

## 未通过项

AC-001清单与基础接线已实现；AC-002已有63项内核测试及回收证据，TASK-110仍等待WORK-051独立复核；AC-003～006尚未完整满足，不能从WORK-048旧报告复制PASS。

## 范围检查

TASK-115按用户条件授权完成多轮诊断后，仅将可信语言功能测试编译默认期限设15秒；运行、生产配置、严格资源回归和正式ci.yml不变。诊断保留显式5秒及其负例，JDK预读和JVM候选不进入正式CI；已发布e44f9b4，原ZIP保留且未跟踪。WORK-051的helper修复沿其独立任务记录。

## 遗留问题

93个case已细化，Linux内核套件已运行；语言首次编译5秒不稳定已有复现，测试专用15秒已按条件授权实施并完成本轮验证；仍不承诺所有托管VM永不超时。原生部署、完整业务环境与总汇总尚未实现。

## 剩余风险

共享内核、托管镜像升级、资源波动、下载失效和取消期间证据缺失；详见DESIGN-044，不声称所有Linux支持。

## 结论

result=pending；用户已签署意图闸并允许实施，验收闸未签署；完整CI仍在实现。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：已盘点既有验收与CI缺口，补齐分层方案、边界及验收条件供人工审核；尚未实施

## TASK-109 本地实施证据

- `PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work050-basic-03`：macOS arm64通过，15项安装器、6项rootfs、8项报告自测，加全部非忽略Python AST/shell语法；93项必需case按basic/kernel/native/business分组。
- `ruby -rpsych -e 'p Psych.parse_file(".github/workflows/ci.yml").class'`：YAML解析通过。新增基础job及手动触发，保留原六job；官方Action版本通过GitHub API核验并固定提交。
- 首次自测因CI测试数量下限误写为9而失败，核对实际8项后修正为8重新执行；未改任何沙箱断言。
- 报告拒绝缺项、重复、错误SHA/schema、未执行、取消、清理失败、证据缺失/链接和测试脚本摘要不符。尚无该改动的GitHub执行记录。

## TASK-110 本地实施证据与实机前置

- `PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work050-basic-08`：39项单测通过（15+6+18），包含执行超时/大输出/退出失败、拒绝接管已有资源、拒绝停止未知单元、残留阻止删除、缺Go用例/1000次记录/快照/边界完成记录拒绝。AST与shell语法通过。
- `/private/tmp/cherry-work050-actionlint/actionlint -shellcheck= .github/workflows/ci.yml`：官方1.7.12发行包核验SHA256后静态检查通过；未安装系统工具。
- `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 GOCACHE=/private/tmp/cherry-oj-work048-go-cache go test -c -o /private/tmp/cherry-work050-boundary.test ./tests/sandbox-linux/boundary`：构建通过；未执行，不是跨平台支持证据。
- 93项清单为basic5/kernel63/native10/business15；Linux四包冻结38个实际测试名，普通包成功而缺具体测试仍失败。
- 新增kernel job原生构建本提交及锁定rootfs，在独占VM内按原预算串行执行；finally与always清理只认本轮root所有权标记，最终检查全部存活进程的挂载/身份和执行cgroup。每命令日志最多2MiB，同目录18MiB日志预算，为报告保留空间。
- 尚无该批Actions运行；TASK-110保持doing，TASK-111～113未开始，WORK-049重构尚不能启动。根据CLAUDE提交规则及PLAN-034阶段验证顺序，下一步需要用户授权发布此批CI，以取得真实Linux结果后继续。

- `scripts/work check`：429份文档通过，保留既有WORK-033提示。`refresh WORK-050`因后续TASK仍todo而将开发任务阶段视作未完成，拒绝将WORK推至doing；未绕过工具或提前置后续任务ready。当前TASK-109 done、TASK-110 doing的事实记录保留。

## 首轮 GitHub 实际执行（保留失败）

用户于本轮明确允许提交推送，已发布7cd13146925664965749769a6773d4cd2bfa3f3b。运行：[34464207491](https://github.com/charon2121/cherry-oj/actions/runs/34464207491)，8个job中6成功、2失败，不是完整通过。

- basic通过5/5，39项Python测试、43个Python文件和4个shell入口；下载报告核验sourceSha/harnessSha/证据和cleanup均通过。
- kernel实际环境Ubuntu24.04.5、Linux6.17.0-1022-azure、x86_64、4CPU、约16GiB内存及3GiB swap，LSM含AppArmor；本轮未关闭安全策略。锁定rootfs构建及38个Linux包测试通过。
- 四组真实边界通过：缺控制器拒绝、11类exec阶段/errno、9类文件拒绝与1000次路径交换、8类启动握手；静态线程采样因/proc/8333/task/8336/status在枚举后消失而失败。后续用例NOT_RUN，不能算通过；最终tasks/mounts/cgroups全部为空，cleanup PASS。
- 修正测试采样：线程消失时丢弃整份样本，在原2s观察期限内再次采样，不能保留部分线程绕过全线程权限断言。
- 原有Go job的TestEndToEndCpp与TestEndToEndJavaWithInnerClass各在5.01s编译退出-1；未出现race诊断。两者使用未修改的host开发夹具。包间资源竞争只是待验证推断；CI改为-p=1限制测试包间调度，保留包内并发、race、完整包集合及原5s期限，并保证失败时仍打印工具链版本。
- 本次失败报告和日志保留在原运行产物中。修正后使用新提交完整运行，不将首轮覆盖或登记为成功。

## 第二轮：通过采样后暴露连续请求异常

[34464741702](https://github.com/charon2121/cherry-oj/actions/runs/34464741702) 对应54e728a9e3b3ac380b930e82432d5cbfb886360a：完整Go/race按包串行通过。Linux已通过边界、11线程身份/六namespace/只读挂载/资源限额、静态smoke和extended；C++链已通过编译、输入输出、CPU及进程树、OOM、OLE后空程序、普通SIGKILL、线程/后台回收及网络/mount/ptrace拒绝。

magiclink请求在helper连接上被reset，结果InternalError，未达到原Signalled/SIGSYS断言，不能通过。CPU与整树本轮分别约1.002/1.003秒CPU、1.058/1.063秒墙钟；空程序峰值约8MiB。后续1000次等未执行，失败后最终资源仍为空。

只读源码发现helper发Completion后才由外层defer归还槽，而接入处无可用槽时立即关闭连接，可能存在连续请求窗口。连接reset本身不足以证明该归因；新增失败时、停服前的有界systemctl/journal证据，以区分容量拒绝与服务退出。保持原断言，不添加请求sleep/重试或提高并发槽，也未修改禁止范围内的生产Go代码。

## 第三轮最终结果与阻塞交接

[34465164384](https://github.com/charon2121/cherry-oj/actions/runs/34465164384) 对应已推送7a66b35fd157272e6dcc0a3a80a7bca0ad8cd690：8个job中7成功，sandbox-kernel失败；Go包间串行后连续两轮完整通过。

本轮magiclink/hardlink与显式零CPU/clock/memory/pids已达到原预期，连接reset出现在zero-output-writer。停服前helper主PID8729、HTTP主PID8755仍与初始快照一致，均ActiveState=active、Result=success、ExecMainStatus=0；helper未因该reset退出。journald中的任务OOM发生于此前正常MLE阶段，不能当成主helper崩溃证据。最终resources-after.json显示tasks/mounts/cgroups均为空，cleanup PASS。

C++整组中断后没有运行1000次、双并发和后续故障组；整组失败不表示前面已打印事实的断言都失败，也不能把这部分事实当作该组全部通过。准确部分结果保留在chain-smoke.log。

TASK-110已置blocked，依赖新建WORK-051/TASK-114。该工作只有待审修复材料，尚未修改任何生产Go文件；候选是修正完成确认/容量释放/连接结束的顺序，先用确定性回归确认，禁止加请求sleep/重试或扩大槽位。WORK-050仍不能验收，WORK-049仍不能开始源码重构。

## helper 修复后的结果与新失败

WORK-051 提交 946e528 的 [34470867753](https://github.com/charon2121/cherry-oj/actions/runs/34470867753) 8 job 全绿，Linux 63 项和 45 必需 Go 测试无跳过，1000 次/并发/故障/容量及完整清理通过。

随后仅文档提交 b03e6bd 的 [34471753900](https://github.com/charon2121/cherry-oj/actions/runs/34471753900) 为 7/8 成功；Linux 63 项及清理再次通过，Go job 的 TestEndToEndJavaWithInnerClass 在 5.00 秒报“编译失败 exit=-1”。Go 1.26.3 / linux-amd64，JDK 17.0.20.1；没有 race 检测报告。原测试继承 host 默认 5 秒编译期限，却不打印 Wait error，故仅能判断高度疑似期限触发。先前 -p=1 不能保证该功能测试稳定，保留此前通过和本轮失败，不重跑覆盖。

TASK-115 仅提出测试专用编译期限和诊断方案，未实施；WORK-051 独立复核也尚待授权。WORK-050 尚未完成原生/真实业务/总汇总，不宣称 CI 基线稳定。

## TASK-115 原期限诊断准备

用户要求先确认和优化，尚未批准测试专用期限。语言测试只增加 Usage/Wait 日志，没有传入新 Limits。独立手动 language-diagnostic workflow 限 3 台一次性 VM、每 job 10 分钟，默认测试和候选启动参数分开采样，所有原始失败保留。分段 javac/jar 各自 5 秒仅作测量，不把相加后的结果算正式编译通过。候选 TieredStopAtLevel=1 只作用于工具 JVM，不作用于被测 Java 程序，尚未采纳。参数依据为 [OpenJDK 17 flags](https://github.com/openjdk/jdk17u/blob/master/src/hotspot/share/compiler/compiler_globals.hpp) 与 [javac -J](https://docs.oracle.com/en/java/javase/17/docs/specs/man/javac.html)。

本地 Darwin/arm64 Go1.26.3：语言真实集成、完整 Go race 与 vet 通过；基础入口43项单测（15安装+6rootfs+22CI）通过，actionlint通过。新增4项验证缺失/重复观察拒绝、nil error 下 wall 原因保留、内部类夹具及候选仅包装编译工具。真实 Linux 诊断尚待执行，不据本地约1秒编译推断 CI 必须在5秒内。

只读核对 Go1.26.3 构建调度源码：-p=1 时 action worker只有一个，Act完成后才推进后续action；不能继续把同一 go test 命令的包间并发当成当前失败的已确认原因。

## TASK-115 Linux 实测与最终建议

用户明确授权本批推送后，451b4d6f47ee3626032eb4d2b6d299c8ce9e0316已推送main。手动诊断[34476105471](https://github.com/charon2121/cherry-oj/actions/runs/34476105471)只运行一次，三台VM均完成测量、产物比对、超时负例和清理；第1/3台保留原始失败并使job失败，第2台成功。没有重跑刷绿。环境均为Ubuntu24.04镜像20260907.300.1、Linux6.17.0-1022-azure、x86_64、4CPU、Go1.26.3、Temurin17.0.20.1；未注入Java options。cpu.max根路径未读到，不据此声称没有CPU限制。

下表为真实Go测试返回的Java编译墙钟秒数；首次在任何Java版本查询或预热之前，后续都是新JVM进程，但可能复用宿主页缓存。每次完整编译仍受原5秒期限约束。

| VM | 首次原配置 | 后续原配置两次 | 候选参数两次 |
|---|---|---|---|
| 1 | 5.006583，wall/SIGKILL | 0.948837 / 0.650359 | 0.499713 / 0.519692 |
| 2 | 3.399449，通过 | 0.627940 / 0.628320 | 0.575565 / 0.504291 |
| 3 | 5.004481，wall/SIGKILL | 0.527393 / 0.500869 | 0.397872 / 0.396899 |

- 原配置9次中7次通过、2次首次超时；候选6次通过。真实运行都验证内部类、跨工作间产物交付及输出3。候选只给javac/jar传入`-J-XX:TieredStopAtLevel=1`，运行Java不变，三台的两组class SHA256全部相同、jar包含对应class。
- 后续原配置墙钟中位数0.628130秒，候选0.502002秒，约降低20.1%。这只是6对后续样本的描述统计，不是稳定性保证；候选未取得独立首次样本，不能宣称解决首次超时。
- 分段原配置javac约0.403～0.557秒、jar约0.086～0.118秒；候选javac约0.317～0.415秒、jar约0.073～0.092秒。分段在上述样本之后且每命令独立5秒，只说明后续阶段成本，不用于证明首次完整管线满足期限。
- 首次整个三语言样本期间，系统I/O PSI `some total`分别增长5.147/1.942/4.618秒；同期CPU PSI仅0.033/0.028/0.019秒，memory PSI为0。后续原配置I/O压力增长大幅下降。该接口是[系统资源等待统计](https://docs.kernel.org/accounting/psi.html)，不是Java独占计时，窗口也包含C++/Python；它支持首次工具链加载/I/O等待的解释，但尚未定位到具体文件、系统调用或存储设备，不能从中扣出Java的精确等待时间。
- 三个故意延迟javac的负例均在约5.000～5.005秒返回`Reason=wall, Signal=9, waitError=<nil>`；正好证明nil Wait error不代表成功。失败样本同样返回这组事实。host的`GroupAccounting=false`，失败后的wait4 CPU值也不能冒充全部后代的完整CPU计量。
- 三台报告`diagnosticCompleted=true, cleanup=true, identicalClasses=true`，分别回收3/2/3个归属本诊断的后代；临时工作区已删除。候选没有写入生产或正式测试配置。

报告来源SHA和五个样本已逐份核对，报告SHA256按VM序号为：`fec678f08f2b3eec84bfaaed632f7b0f4f685b7d5f69b014d38af20bfd47b849`、`6fc4f306bf516447335eb156ac78da3a83be8f318dbf66851d5e56f28807a05a`、`fd2c41413f64b536fc5e110a8a0f9df6533e010015a92bce09735cb76c98dc91`。原始日志随该运行的三个language-diagnostic产物保存14天；表格与摘要保留长期结果。

同SHA的正式[CI 34476081108](https://github.com/charon2121/cherry-oj/actions/runs/34476081108)最终7/8成功，Go job中C++与Java都在约5.004/5.007秒返回wall/SIGKILL，stderr为空；没有race报告。Go job镜像亦为20260907.300.1。本轮Linux内核job实际为较早镜像20260831.293.1、Ubuntu24.04.4、相同内核架构，不能把所有job说成同一镜像；其63项必需case、45个冻结顶层Go用例（含子例共157个pass，零fail/skip）及1000次/并发/故障全部通过，最终tasks/mounts/cgroups为空。下载后report.validate、verify_files及harness SHA核验通过。

结论：当前直接失败原因已复现并确认是语言功能夹具继承的5秒墙钟；更深层原因与首次工具链加载时的I/O等待高度相关，旧轮次缺少事实字段，不能反推所有历史失败的唯一根因。Java参数在后续样本有性能收益，但无法解决C++且没有首次稳定证据，不建议为此更改生产语言命令。

建议后续经用户决定，为这类语言功能集成测试显式配置独立编译墙钟上限，覆盖C++与Java，保留原命令、首次样本、产物和运行断言；运行期限、生产host默认值、Linux资源限额及CPU/墙钟终止回归保持原值。原30秒提案可作为保守的测试等待上限，但不是测得的最小值、也不保证所有托管机器必能完成；到时仍失败并保留Usage。当前没有实施任何新期限，TASK-115诊断完成不等于完整CI通过或WORK-050验收。

## TASK-115 追加对照准备

本地vet、完整race通过；首次受限执行因临时socket bind被禁止失败，改以获准的正常权限执行后全包通过，未改测试断言。Python诊断5项自测、basic入口、actionlint、438文档及diff检查通过。测试二进制编译期限参数初始仍默认5秒，诊断明确传5秒；预读对照只读固定9个JDK文件，256MiB/30秒上限，无JVM执行或cache eviction，两组构建后同等30秒窗口。准备交替even/odd两轮，后续是否改15秒取决于实际结果。

## TASK-115 追加两轮交替对照结果

源码cc0f59cf04382569b03e0c8f437a302650da2c3d；[第一轮34478016543](https://github.com/charon2121/cherry-oj/actions/runs/34478016543)与[第二轮34478204311](https://github.com/charon2121/cherry-oj/actions/runs/34478204311)各3台新VM，交换预读组；每台首次前窗口均30.000秒左右。系统与镜像均Linux6.17.0-1022-azure、x86_64、镜像20260907.300.1。两轮共6台均完成测量、class比对、原5秒负例与清理，所有30个主样本保留，未重试覆盖失败。

| 轮次/VM | 只读JDK文件 | 首次Java秒 | 后续原配置Java秒 | 首次C++秒/结果 |
|---|---|---|---|---|
| 1/1 | 否 | 3.934436 | 0.639763 / 0.630776 | 5.005789，wall |
| 1/2 | 是，2.358039秒 | 0.948708 | 0.487468 / 0.468034 | 1.978081，通过 |
| 1/3 | 否 | 0.668869 | 0.446680 / 0.421692 | 0.554904，通过 |
| 2/1 | 是，16.882251秒 | 1.366622 | 0.549220 / 0.534979 | 5.005257，wall |
| 2/2 | 否 | 5.006796，wall | 0.612978 / 0.603719 | 0.486586，通过 |
| 2/3 | 是，4.171764秒 | 1.197398 | 0.578849 / 0.611286 | 0.376920，通过 |

三次预读都只读固定9个文件、154966286 bytes，不运行JVM；预读区间系统I/O PSI分别增长1.413068/13.640698/4.091166秒，CPU PSI仅0.001178/0.007687/0.002917秒。未预读组存在0.669秒的快速首次，这是保留的反例：首次不必然慢。两轮未预读首次Java中位数2.301652/5.006796秒，预读组0.948708/1.282010秒；第二轮5秒为被截断的失败观察，不是自然完成时长。两轮后续原配置12次均成功，0.421692～0.639763秒。

两轮满足运行前约定的同方向判据；与首轮3台观察合看，支持工具链文件加载/I/O等待对部分首次延迟的贡献。样本量小、各VM负载不同，不能推导总体超时概率，也没有定位所有历史失败的唯一系统调用。JDK预读未解决C++超时，且额外读取有实际成本，因此不将它加入正式CI。两个诊断工作流分别1/3和2/3 job失败，原因是原始C++/Java wall；其测量与清理均完成。六台报告SHA、样本数量、identicalClasses、cleanup和负例均已本地核对。

同源码正式[CI34477990150](https://github.com/charon2121/cherry-oj/actions/runs/34477990150)8/8通过，保留这次5秒通过和之前失败，不将偶然通过认作稳定。依据用户已给的条件授权，接下来只将语言功能测试编译默认上限设15秒，用第三轮3台未预读新VM及正式CI验证；运行期限、生产与严格资源回归不变。15秒仍需实测，不能由预读耗时推导其一定充分。


## TASK-115 条件实施后的未预读验证

源码e44f9b495e1f179c0bc7c49a590f73f7bd166ed6；[第三轮34478686080](https://github.com/charon2121/cherry-oj/actions/runs/34478686080)三台新VM均未预读、未等待额外30秒，JVM版本查询仍在首次实际编译之后，编译上限15秒。三台完整诊断全部成功，原配置9个Java编译与三个C++/Python首次功能链都成功，候选6次也成功；没有用后续成功覆盖首次失败。

| VM | 首次Java秒 | 后续原配置Java秒 | 首次C++秒 | 固定5秒负例实际秒 |
|---|---|---|---|---|
| 1 | 10.559792 | 0.464799 / 0.472216 | 2.081113 | 5.002696，wall/SIGKILL |
| 2 | 2.046633 | 0.607314 / 0.642642 | 1.857626 | 5.004536，wall/SIGKILL |
| 3 | 0.828988 | 0.608166 / 0.598277 | 0.440822 | 5.004556，wall/SIGKILL |

VM1的实际完整编译在10.56秒正常结束，产物迁移/内部类/输出断言均通过；它提供了旧5秒截断观测之外的自然完成耗时。该首次三语言窗口系统I/O PSI增长7.626097秒、CPU PSI0.026549秒、memory PSI为0；不能将整个窗口I/O时间直接归属Java，但与前两轮文件预读对照方向一致。当前15秒容纳了这批样本，样本量有限，不能保证未知负载、镜像或未来全部机器都满足；到期仍按原机制终止，不重试刷绿。

三台source SHA、15秒配置、preloadJdk=false、preSampleSeconds<1、全部样本、class一致性、原5秒负例和cleanup已核验，分别回收2个归属本诊断的后代。报告SHA256为`da209c43ae09738af368d013beeee10789ab41e6fe47b423269032dd8d7c537a`、`4044d46367564d888683be10b907a70a5c40c1a9b990357e9492af3a4c576fbb`、`5620f8686ab75f786a2ef99b57bb4580548884aa876f01d5dca34807fd428077`。

同SHA正式[CI34478647561](https://github.com/charon2121/cherry-oj/actions/runs/34478647561)8/8成功，实际使用测试默认15秒，无JDK预读或候选参数。生产/运行/严格资源限额与之前源码diff为空。TASK-115已完成用户追加的验证和条件实施；WORK-050仍需原生部署、真实业务、总汇总及独立复核，不能据此签署整项验收或宣称所有93项已通过。

该15秒版本内核报告下载后通过report.validate、verify_files和linux_units；63项case、45个冻结Go用例、1000次/并发/故障及cleanup通过，resources-after中的tasks/mounts/cgroups均为空。harness SHA为`a92c0348f189c6e443b293831ffa15af8baf71fbd14d575f5a5a3bfbd91c68b8`；本轮实际内核镜像20260907.300.1。文档检查438份、链接507份通过；refresh仍受既有后续任务未完成的阶段推导限制，未手工改状态或代签闸。
