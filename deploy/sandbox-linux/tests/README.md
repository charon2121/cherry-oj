# TASK-096 最小实机套件

本目录验证静态 Go 与独立 C++ 夹具下的 helper 有界隔离链；连续1000次、并发、完整故障恢复和跨架构支持仍待后续测试。运行前读 WORK-048/TASK-096；远端目录/单元、资源封顶及清理边界见该任务。不得直接用作正式节点 rootfs。

在 `apps/judge-engine` 使用 `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build -o <独立目录>/sandbox-helper ./cmd/sandbox-helper`，同样构建 `./tests/sandbox-linux/probe` 与 `./tests/sandbox-linux/cloneprobe`；`go test -c` 构建 helper/launcher/cgroup/policy 四包 Linux 测试二进制。用 `prepare_fixture.py <独立目录>` 生成 rootfs 和 manifest。静态探针为项目自编；下述C++夹具保留包内许可证与固定来源。

上传仅限 `/var/lib/cherry-sandbox-test/` 内新建的本次独占目录。从 macOS 打包时移除扩展属性；Linux 解包使用 `tar --no-same-owner`，否则会保留本地 UID 导致 helper 拒绝启动。整个测试目录必须 root 所有且非 root 不可写；不要绕过可信性检查。

`bootstrap.py <本次目录> <本次单元名> [cpp] [trace]` 只在 `cherry-sandbox-test-*` 的 Delegate 临时单元内运行。先移自身到 supervisor 叶子，再启用当前单元及 jobs 的 cpu/memory/pids，生成 root 管理配置并执行 helper。数字身份 61001/61002/61003 必须先核对未占用；不创建系统账号。外层使用 MemoryMax=768M、MemorySwapMax=0、TasksMax=192、CPUQuota=100%、RuntimeMaxSec=180、KillMode=control-group。可选 trace 使用已存在的 strace，另设 RuntimeMaxSec=30 和 LimitFSIZE=16M；跟踪结果不能当作未跟踪的安全运行证据。

`smoke.py <本次socket>` 在单独封顶的驱动单元运行，降为 61001 后串行检查身份、1秒CPU、64MiB OOM、8KiB输出、后续正常程序和网络 syscall 拒绝。每次检查 Completion 和 populated=false，标准输出只保留字节数及身份事实，避免大输出淹没报告。

`cloneprobe` 是预写空组 cgroup.kill 与 CLONE_INTO_CGROUP 交互的对照：在独立 Delegate 单元内创建两个临时组，各执行一次宿主 true，分别预写和不预写 kill；完成后删除组。只用于定位内核行为，不是隔离后端降级入口。

结束后停止本次 helper 单元，核对本次 jobs 无执行子组、所有 /proc 的 exe 与 mountinfo 无本次目录引用、system.slice 无本次单元组，再移除本次 state 中 lock/owner-v1 和上传目录。未知条目、未清空组或存活进程应停止清理并记录，不能递归清空系统路径。


第二轮驱动（实际参数以各脚本入口为准）：

- `inspect_threads.py`：从测试jobs读取init/payload，检查全部线程权限、namespace及只读挂载；客户端子进程降权。
- `extended.py`：静态probe的后台后代、pids/线程扩张、墙钟、低pids及断连取消。
- `download_toolchain.sh`、`assemble_toolchain.py`：在本次独占目录内下载/解包g++、coreutils及完整依赖。私有APT配置禁用宿主配置钩子，只执行下载，不安装；核对宿主包数据库摘要。构建新的cpp-rootfs-v2，包锁和许可保留。
- `cpp.py`：隔离编译、流式取产物，再在新组隔离执行。
- `cpp_limits.py`：编译有界C++夹具后，逐组验证多进程CPU、普通SIGKILL、后台后代、pthread上限及触碰内存OOM。
- `crash.py`：仅定位已核验exe的本次helper，用pidfd发送SIGKILL，要求客户端失败关闭并检查整个测试单元cgroup消失。此驱动需要root，不应对已有服务运行。

C++夹具选择bootstrap的cpp参数；所有普通请求客户端降为61001。驱动也要独立封顶，单次墙钟≤5s、内存≤128MiB、pids≤64；保持串行。crash用例后可能留下root所有的socket及空run目录，须核对类型、随机命名、无进程/挂载/组引用才逐项删除。未知条目不得自动清除。完整实测数值、修复历史及支持范围见 WORK-048/VERIFY-049。

## TASK-098 HTTP整链

`chain_batch.py <独占work048-chain目录> smoke|repeat|concurrency` 依次启动helper、非特权sandbox HTTP和驱动三个临时systemd单元，批次finally停止服务并检查cgroup消失。前提为已构建上传sandbox/sandbox-helper、bootstrap.py/http_chain.py及锁定cpp-rootfs-v2；HTTP仅监听127.0.0.1:15050，运行前确认端口和测试身份未占用。批次应串行执行，禁止与已有服务共用目录或单元名。

HTTP使用宿主已有setpriv降到61001并清空cap，不安装工具或创建账号。helper上限768MiB/192任务/CPU100%，HTTP256MiB/96任务/CPU50%，驱动128MiB/16任务/CPU50%，全部swap0，运行最长180s。bootstrap新增parallel2选项仅用于双并发批次。

http_chain.py通过公开/run隔离编译C++、取artifact ref并用于新执行，验证状态映射、资源、后台后代、断连及链接syscall拒绝；repeat执行1000次空程序；concurrency验证两个sleep2s重叠与两个独立执行组。每批对比FD目标/数量、jobs、workspace、blob和payload/init进程及挂载快照；finally删除产物ref。链接在创建时已被seccomp拒绝，不能据此宣称覆盖所有恶意文件读取竞态。

服务停止不自动删除上传目录/rootfs与state lock/owner-v1。保存证据后，按本文件前述所有权、类型及引用检查清理本批资源，保留journal。实测环境、二进制摘要、1000次/并发结果和待验矩阵见WORK-048/VERIFY-049；这些测试不注册或切换判题节点。

## TASK-098 故障与恢复

`fault_batch.py <独占work048-fault目录>` 使用prepare_fixture.py生成的静态Go rootfs，需上传sandbox、sandbox-helper、bootstrap.py及本驱动。运行在单独systemd封顶驱动中：128MiB/swap0/16任务/CPU50%/150s；helper768MiB/192任务/CPU100%，HTTP256MiB/96任务/CPU50%，服务swap0/120s。先检查15051端口与61001～61003身份未占用。每批必须使用新目录；脚本从目录名派生精确测试单元。

覆盖不存在命令、错误可执行格式、排队断连、运行阶段namespace init强杀、HTTP/helper强杀后重启及正常停服取消。pidfd发送信号前核验UID/exe/cgroup。bootstrap新增recover选项只供同实例状态恢复；ready检查实际连接，不能以遗留socket存在为准。finally停止两个被测服务；保存日志、核对所有进程/组/挂载引用和state文件类型/标记后清理本次上传及状态目录。不是所有握手点的确定性注入套件，也不执行宿主重启。

## TASK-098 启动协议与文件边界

交叉构建 `go test -c ./tests/sandbox-linux/boundary` 为boundary.test，连同sandbox-helper、静态probe和prepare_fixture.py生成的rootfs/manifest、boundary_batch.py上传全新work048-boundary目录。在独立Delegate单元运行boundary_batch.py：768MiB/swap0/192任务/CPU100%/60s；驱动再封顶supervisor128MiB/swap0/16任务/CPU50%，每个启动子组64MiB/64任务/CPU100%，test.timeout45s。

测试直接控制真实launcher协议，七种中断+合法GO对照；直接OpenOutput检查已创建的恶意链接/特殊文件及1000次路径交换；只在本批空jobs移除并恢复各控制器，验证缺委派拒绝。没有helper累计CPU监测，此夹具仅运行固定静态探针，不能拿去执行外部不可信代码。显式环境变量只由驱动传入，缺少时普通测试skip，不能计为Linux运行验证。每例独立清理组/挂载点，单元退出后仍需保存日志并扫描进程、挂载、组和目录，确认无本次引用再删除独占夹具。

fault_batch.py的可选第三参数capacity切换为并发2/队列4，验证第7个执行请求503、10个有界未完成body占满handler后第11请求503，取消后容量恢复。只在本批空jobs设置96MiB聚合memory.max和oom.group，两个各128MiB请求必须返回平台错误；恢复聚合上限后单任务64MiB OOM仍应MLE。同时读取memory.events.local与memory.events，不能混淆组级压力和后代受害计数。driver TasksMax使用32，其余外层限制不变；测试结束finally恢复jobs属性、关闭连接并停服。
