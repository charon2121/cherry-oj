# Linux 沙箱交付准备

TASK-098已完成首站执行后端实机验证。TASK-099正在准备[原生节点部署包与安装清单](install/README.md)，正式安装与服务权限组合仍待实机验证。
设计、支持矩阵及实施任务见 [WORK-048](../../development/works/WORK-048/00-work.md)。

## 只读探针

本地 Linux：`sh deploy/sandbox-linux/probe.sh`。
远端通过已经配置好认证和主机信任的 SSH 连接，将脚本经标准输入交给 `sh -s`：

```sh
ssh -o BatchMode=yes -o StrictHostKeyChecking=yes -o UpdateHostKeys=no \
  -o ConnectTimeout=10 "$SANDBOX_TEST_SSH" 'sh -s' < deploy/sandbox-linux/probe.sh
```

`SANDBOX_TEST_SSH` 由操作者从项目内被忽略的本地连接记录设置，本目录不保存地址或凭据。
脚本不落地到远端，不写 cgroup、sysctl、文件、账号或服务，不调用 unshare、mount、安装器或压力程序。
SSH 本身可能产生正常系统审计记录，不能将“只读”理解为操作系统完全没有日志变化。

输出包括版本、内核、架构、资源、LSM、调用者权限、控制器、接口存在性、服务名称和进程名称计数；
不读取进程参数、环境变量、日志、主机名或凭据。发布前仍应检查服务/进程名称是否含私有信息。

退出码 0 只表示完成盘点，最后一项为 `result=inventory-only`；非 Linux 返回 2。
`absent-or-inaccessible` 表示不存在或无法遍历父路径，不能据此断定内核一定不支持。
`permission-denied` 表示发现文件但当前调用者不能读取；工具缺失也会单独记录。
root cgroup 不具有部分限额文件是正常现象，必须检查非根组；当前会话的 `Delegate=no`
不代表 systemd 不能为未来服务委派。探针不是服务启动自检，也不是安全测试。

## 后续行为测试的边界

创建临时 cgroup、namespace、挂载或运行受限程序会改变系统状态，必须进入后续明确授权的任务。
首站虽已确认为项目专用，系统服务与云厂商代理仍需保留；测试采用独立命名的 systemd 单元和
项目目录，设置外层总资源上限，结束后只回收本项目资源。不得直接向现有 system.slice/user.slice
写入任务限额、清空全机进程或修改全局安全开关。
