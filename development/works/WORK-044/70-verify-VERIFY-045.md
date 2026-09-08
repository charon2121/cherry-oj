---
id: "VERIFY-045"
type: "verify"
title: "题目内自定义输入运行"
status: "approved"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["TASK-085", "TASK-086", "TASK-087", "TASK-088"]
related: []
implements: []
verifies: ["FEATURE-012#REQ-001", "FEATURE-012#REQ-002", "FEATURE-012#REQ-003", "FEATURE-012#REQ-004", "FEATURE-012#REQ-005", "FEATURE-012#REQ-006", "FEATURE-012#REQ-007", "FEATURE-012#REQ-008", "FEATURE-012#REQ-009", "FEATURE-012#REQ-010", "FEATURE-012#REQ-011", "FEATURE-012#REQ-012", "FEATURE-012#AC-001", "FEATURE-012#AC-002", "FEATURE-012#AC-003", "FEATURE-012#AC-004", "FEATURE-012#AC-005", "FEATURE-012#AC-006", "FEATURE-012#AC-007", "TASK-085", "TASK-086", "TASK-087", "TASK-088"]
tags: []
result: "pass"
created_at: "2026-09-07"
updated_at: "2026-09-09"
---

# VERIFY-045：题目内自定义输入运行

## 验证对象

FEATURE-012 的单输入运行、受控 stdout/stderr、工作台草稿与正式提交隔离；实施与真实联调已完成，等待人工验收闸。

## 对应要求

FEATURE-012#REQ-001 至 REQ-012、AC-001 至 AC-007；TASK-085 至 TASK-088。

## 检查与结果

2026-09-08，macOS arm64、Node 24、JDK 21、Go 工具链及 Docker Desktop Linux arm64。

| 检查 | 实际结果与证据 |
| --- | --- |
| 契约 | scripts/contracts_test.py 11 项通过；生成 API 无漂移。 |
| Go | apps/judge-engine 下 go test -race ./... 通过，日志 /tmp/work044-go-final.log。显式 Linux 压力回归默认跳过，不能以此声明压力通过。 |
| Java | apps/server 下 ./mvnw -pl submission-service,judging-service,gateway-service -am test 通过，/tmp/work044-backend-final.log。后续响应必填校验的 6 项测试通过，/tmp/work044-trialtests2.log；真实 trial 凭据/异常映射与控制器测试通过，/tmp/work044-security2.log；完整 Gateway 安全链 CSRF 拒绝通过，/tmp/work044-csrf.log。 |
| Web | Node24 npm run check（40 文件、168 单元测试）与 npm run build 通过；/tmp/work044-webcheck-final.log、/tmp/work044-webbuild2.log。 |
| 浏览器 | workspace/history/custom-run 合计 27 项通过，/tmp/work044-e2e4.log；随后新面板双主题/forced-colors/reduced-motion 截图补充 3 项通过，/tmp/work044-visual2.log。 |
| Redis | 真实 Redis 跨两个 Controller 共享 10/60s 额度；已有租约拒绝不转发；大 JSON 合法正文成功；/tmp/work044-trialtests2.log。 |
| Linux 基础 | 独立 work044-test 容器、127.0.0.1:15051。真实 C++ 输入/空输入、stdout/stderr、CE/RE/TLE、stderr 截断通过；自测并行时正式请求 AC；断连后的请求完成。/tmp/work044-linux-basic.log。 |
| Linux 压力 | 有限约 94 MiB 输出正确得到 OLE，但随后空程序误报 MLE（281927680 bytes）。可重复回归见 apps/judge-engine/internal/judge/api/trial_isolation_test.go；/tmp/work044-linux-isolation.log。此项 FAIL。 |
| 清理 | 独立 sandbox blob store 文件数 0，池保留 2 个工作目录（对应并行度2，不能误记为零目录）；随后测试容器、专用网络/卷已删除，/tmp/work044-cleanup.log。 |

容器构建 Debian 源返回502，未修改仓库 Dockerfile；使用本机已有 judge/sandbox Linux 运行时镜像，覆盖当前源码交叉编译二进制，打独立 work044-test 标签。没有重启现有 OJ 后台，没有启用三个服务默认关闭的 CHERRY_CUSTOM_RUN_ENABLED。

## 未通过项

共享 host sandbox 在大输出后污染后续程序的内存判定。相同 int main(){} 在干净 sandbox 为 RAN，大输出后为 MLE，已在独立容器复现。当前实现从子进程 wait4 的 ru_maxrss 获取峰值；2026-09-08 进一步对照实验确认：Go 的 CLONE_VM 启动路径使 exec 前旧地址空间的历史峰值带入子进程，详见末尾专项排查。不能简单扣除父进程 RSS、放宽题目内存上限或假报 RAN。

这会影响复用该 sandbox 的正式提交。用户在阅读排查报告后明确决定：当前沙箱为基础实现，本轮暂不修复，留待后续 cgroup、namespace 隔离建设处理。该项保留 FAIL 事实并作为已知限制，不再作为本轮必须修复的前置条件。后续 WORK-045/046/047 已完成本地配置、真实环境联调与运行恢复；人工验收仍由用户签署。

## 范围检查

修改限于 TASK-085/086/087 所列契约、Go flow/contract/api、Java 三服务、Web 业务模块/生成类型/E2E及 WORK 文档。scripts/contracts_test.py 在动手前已加入 TASK-085 边界。没有修改 sandbox、其他业务服务、共享 UI/设计值、数据库或 Kafka。

回归发现并已修复：空输入 FileSource 被 omitempty 序列化成非法空对象（改为省略 stdin 表示 EOF）；trial 包不在 judging 异常处理范围；运行面板挤掉主代码编辑区（设置底部高度上限与独立滚动）；响应必填事实不能默认成0。均已记录对应测试。

## 遗留问题

1. 用户已决定暂缓 sandbox 内存计量修复，后续 Linux 隔离沙箱工作需承接复现测试。当前不扩大 TASK write_paths、不修改 sandbox。
2. 实际五服务和真实 admin 账号联调已获授权并在 WORK-047 完成，证据见 VERIFY-048。
3. 用户已在自行重启服务后确认功能完成，人工体验复核已收到；正式验收闸 pending，未由实现者签署。

## 剩余风险

大输出后的 MLE 污染是用户已明确暂缓修复的已知限制；单实例 Java semaphore 不保证物理 CPU 隔离；输入/结果不持久化，断线需手动重试。未把测试通过的有限场景扩展成无限输入/所有断连时序保证。

## 结论

pass（按用户已确认的本轮范围）。实现、自动化检查与真实联调完成；sandbox 内存统计失败事实继续保留并延期处理，不代表硬隔离压力验收通过。TASK-088 技术交付完成，人工判断收拢到验收闸，作者未代签。

## 页面自检八问

1. 沿原工作台左右分隔；输入与只读输出复用 TextEditor 自身编辑边框，运行结果各段用间距，不新增卡片嵌套。
2. 状态/资源纵向对齐，资源为短的等宽文本簇；没有新增列表列或长标题 spread 行。
3. 正文 foreground、辅助 fg-2、提示 fg-muted、时间/限制 fg-meta；disabled 只用于不可操作控件，不作为正文层级。
4. 使用既有题目工作台模板，左侧题目/提交记录不变；右侧底部三个 Tabs，底部最多一半高度且可滚动，编辑器高度实测断言大于100px。
5. 新业务代码没有 var(--ds-*)、新增 alias、主题值或共享原语。
6. 耗时/内存使用带单位数字；状态用明确文字，不用红绿区分 AC/WA；饱和品牌色沿既有正式提交主按钮。
7. 双主题、320px、200%等效视口、键盘、长中文、forced-colors、reduced-motion：相关 E2E 通过。窄屏复用题目/代码切换，文字折行，横向溢出断言≤1px。截图 /tmp/cherry-work044-desktop.png、/tmp/cherry-work044-light.png、/tmp/cherry-work044-mobile.png 已查看；主题截图结束过渡动画后再采集。
8. 对照 measurements.md：沿用现有 sm 按钮、Tabs、TextEditor 的控件高度/描边/字号，业务文本使用 xs 等既有刻度，未自造值；代码/输出保留等宽字体。未重新逐元素测量全部既有组件，人工复核仍 pending。


## sandbox 内存统计专项排查（2026-09-08）

用户本回合要求排查；未修改 sandbox 业务实现、部署配置或已签署范围。临时无网络、只读 Linux arm64 容器运行诊断程序后自动删除。Go1.26.3，诊断源码 /tmp/work044-rss-investigation/main.go，完整数据 /tmp/work044-rss-investigation/result.log。

### 根因与证据

host.go:Wait 直接把 ProcessState.SysUsage().Maxrss ×1024 用作 MemoryBytes，runner.classify 据此判定 MLE；单位换算正确。Go 标准库 syscall/exec_linux.go 默认追加 CLONE_VFORK|CLONE_VM，使目标 exec 前共享父进程的旧地址空间。Linux exec_mmap 在更换地址空间时把旧 mm 的 hiwater RSS 留入 signal.maxrss。新程序自己的 /proc/self/status VmHWM 虽然很小，getrusage/wait4 仍可包含那个启动前的峰值。

内核依据：[Linux v6.12 fs/exec.c 的 exec_mmap](https://github.com/torvalds/linux/blob/v6.12/fs/exec.c#L954)，其中 setmax_mm_hiwater_rss 保存旧地址空间峰值；本机 Go 源码 /opt/homebrew/opt/go/libexec/src/syscall/exec_linux.go:309 确认 CLONE_VM 路径。使用固定版本源码解释机制，实际行为由以下当前 Linux 容器实验验证。

| 对照条件 | 目标自身 VmHWM（KiB） | wait4 返回 Maxrss（KiB） |
| --- | ---: | ---: |
| 父进程未分配大缓冲，直接启动目标 | 6048 | 6012 |
| 父进程分配并触碰300MiB，直接启动同一目标 | 6044 | 313452 |
| 经全新中间进程再次启动目标，读取目标的 wait4 | 6056 | 5908 |
| 父进程 FreeOSMemory 后直接启动目标 | 6052 | 313608 |

VmHWM 与 wait4 是不同时间点的采样，正常行的小幅差异不作为逐字节相等断言。关键差异是目标保持约6MiB，而污染行出现约306MiB。释放后父进程实际 VmRSS 已降为6448KiB，但历史峰值仍在，所以强制 GC/释放缓冲不是可靠修复。中间进程自己的 wait4 仍是313452KiB，必须由中间进程回传其子进程（真正目标）的资源事实。

大输出是触发源：runner.capWriter 在 sandbox 进程中用 bytes.Buffer 捕获输出，judge 默认允许 stdout64MiB、stderr16MiB；缓冲增长、字符串化和响应编码会抬高常驻服务的历史 RSS。此前约94MiB有限输出→OLE→空程序 MLE 的实际回归与最小实验相互印证。问题属于执行后端统计路径，正式 submit 和 trial 都可能触发，不是 Web 显示错误或 trial 私有字段导致。

### 修复方向（建议，尚未实施）

近期可研究轻量启动/计量进程：sandbox 服务保留输出收集，全新启动器启动目标，通过独立私有控制通道回传目标的 CPU、RSS、退出码和信号；不能读取启动器自身的污染 rusage，也不能让用户 stdout/stderr 混入计量通道。原有整个进程组取消、超时、编译器子进程回收和池复用必须回归。最小实验仅证明切断历史峰值传递可行，还不证明启动器协议或生产实现完成；轻量启动器自身仍带有小的启动基线，不能声称已获得按任务进程树精确计量。

长远方案为每次执行独立 cgroup v2，获得进程树计量及真正的内存限制；需要明确内核/权限/挂载、memory.peak的计量口径、OOM状态和清理策略。它与当前单进程Maxrss并非完全相同口径，不在这次排查中直接落地。

不建议扣减父进程RSS、提高题目内存上限、强制GC或降低输出额度作为根治：这些做法不能可靠区分真实内存使用与历史污染，或改变已经签署的题目限制。只轮询 /proc RSS 也可能漏掉短命程序的峰值。

### 后续验收条件

同一正常程序在输出压力前后均为RAN，正式提交仍为AC；真正高内存程序仍能判MLE；CE/RE/TLE/OLE、取消、并发和子进程清理不回归。以上为未来沙箱修复工作的验收条件；用户已决定暂缓，不作为 WORK-044 的修复前置条件。未来实施前再确认具体范围。

## 用户处置决定

用户在阅读专项排查后明确表示：当前沙箱只是基础实现，未来会建设 cgroup、namespace 等 Linux 隔离能力，暂时不处理本问题。按此决定停止进一步修复调查，保留失败证据与显式复现测试；不将失败改写成通过，不将该决定视为 WORK-044 验收闸签署、现有服务重启或 commit/push 授权。


## Gateway单点开关重构验证（TASK-089）

2026-09-08，用户明确确认 DESIGN-039 并允许实施。submission-service、judging-service 已删除重复 enabled 属性、构造器参数、禁用分支与YAML项；CHERRY_CUSTOM_RUN_ENABLED 的生产配置读取仅保留在Gateway。保留变量名及默认false；本节替代此前“三服务都设置开关”的部署说明，历史测试记录不改写。

执行 apps/server 下 `./mvnw -pl submission-service,judging-service,gateway-service -am test`，BUILD SUCCESS，115通过、1跳过（合计116；其中identity-security-support8、Gateway65、submission10、judging33含1跳过）。未启用的 RealLinuxJudgeIntegrationTests 被明确跳过，本轮不将其视为通过；本次代码未涉及Go/sandbox，先前已记录的隔离缺陷按用户决定延期。日志 /tmp/work044-toggle-tests.log。

行为证据：Gateway关闭返回503/CUSTOM_RUN_DISABLED，Redis/身份准入无调用且下游请求数0；Gateway开启的既有真实Redis与HTTP测试通过；submission无需功能开关即可向HTTP测试下游转发冻结输入并返回结果；judging无需开关即可运行既有真实HTTP节点fixture，未配置开关的完整应用路由仍拒绝匿名/管理员JWT，合法服务凭据进入执行配置检查而非关闭分支。既有CSRF、版本、错误投影及正式提交回归通过。

README已明确仅Gateway设置变量；此次更新二进制仍需重启两个修改过的下游，之后切换开关只涉及Gateway。未重启现有服务、未改运行环境变量、未commit/push。TASK-089完成；TASK-088真实环境验收及人工独立复核仍待完成，整体VERIFY保持partial，不代签验收。

## 2026-09-08 本地 RUN_BUSY 排查

- 用户请求 `req_6490581e76e740299f8c6dc5cae20303` 在 14:21:12 被 Gateway 返回 429；其前一次请求 `req_f8d6798bf256454194aee7751e3d5c06` 在 14:20:33 返回 503。后者 trace `9daa6e253c2a4dbc94ddf2fd58290204` 在 judging-service 有 401 鉴权拒绝记录。前后相隔约 39 秒，仍在 Gateway 对结果不确定请求保留的 65 秒租约内；429 是前一次失败的后续表现。
- 只读检查监听 8083/8084 的本地 Java 进程环境变量存在性（未输出值）：submission 已有 `CHERRY_SUBMISSION_JUDGING_TOKEN`，judging 缺少 `CHERRY_SUBMISSION_JUDGING_TOKENS`。当前内部安全链在接收端未配置凭据时拒绝访问，与日志一致。
- 本地忽略的 `apps/server/.idea/runConfigurations/Local_JudgingServiceApplication.xml` 已包含接收端变量，与 `Local_SubmissionServiceApplication.xml` 的发送端凭据匹配；另外存在不带该配置的 `JudgingServiceApplication` 启动项。应停止占用 8084 的当前实例，改用 IDE 的 `Local JudgingServiceApplication` 启动项；不应同时启动两个实例。
- 本轮未改业务代码、未清除 Redis、未重启用户服务、未打印凭据。现有运行请求鉴权尚未恢复验证，不把定位结果记成端到端通过。功能开关仍仅由 Gateway 控制；服务身份凭据属于正式判题已有配置。

## 收束更新（2026-09-09）

后续 WORK-045/046/047 完成本地启动入口统一、application-local.yaml 配置、真实节点环境切换、数据部署和 v3 校准发布。证据真源为 ../WORK-047/70-verify-VERIFY-048.md：真实用户页面输入 40 2 输出 42、stderr debug；顺序再次运行成功；死循环约 10 秒墙钟终止后再次正确运行成功；授权的唯一正式提交 01a081ed-e4ce-7d36-952d-1eb3601cc7bb 为 AC、6/6。用户已明确确认“自定义运行终于完成了”。

WORK-047 明确取代本工作最初的 10/60s 频率与失败冷却要求；真实 Redis 12 次连续请求、失败/取消清理及 owner 保护测试已通过。其他已通过自动化场景沿用上文记录，没有虚构重新执行。大输出后 MLE 失败保留，是用户已决定延后的基础 sandbox 已知限制。

技术复核见 VERIFY-048 的实现复核记录；原文中的“人工独立复核 pending”指人的最终产品与风险确认，此处仍交由验收闸完成，不声明独立人员做过未发生的代码审查。

## 变更记录

- 2026-09-09：状态变更：draft → review。原因：补齐 WORK-047 真实用户运行与正式提交证据，保留已明确延期的沙箱缺陷，提交人工验收

人工体验复核：用户自行重启 IDEA 服务后确认“自定义运行终于完成了”；这项记录只表明真实使用者的功能确认，不等同独立安全代码审计。正式接受延期风险和签署仍由用户执行验收闸。
- 2026-09-09：验收闸通过：review → approved。原因：确认自定义输入运行完成，沙箱内存统计问题按既定决定延期
