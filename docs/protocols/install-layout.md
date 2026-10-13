# 安装布局协议

规定 cherry-oj 判题节点安装到服务器后，文件放在哪里、归谁、什么权限，以及哪些位置不在家目录里。
安装、运维、judge 和 sandbox 执行器都依赖这份布局，彼此只靠它对齐。

## 快速使用

所有持久文件都在一个家目录 **`/opt/cherry-oj`** 下。想知道节点上有什么，看这里：

```sh
find /opt/cherry-oj -maxdepth 2 -printf '%M %u:%g %p\n' | sort -k3      # 结构、属主、权限
readlink /opt/cherry-oj/current                                          # 当前用的是哪个发布
cat /opt/cherry-oj/etc/executor.conf                                     # 执行器的受信配置
```

## 1. 家目录

```text
/opt/cherry-oj/                      root:root  0755
├── etc/                             root:root  0755   配置，root 管理
│   ├── executor.conf                root:root  0644   执行器受信配置
│   ├── deployment.json              root:root  0644   部署清单：各关键文件与资源上限的期望值
│   ├── judge-start.json             root:root  0644   judge 启动参数
│   ├── judge-start.py               root:root  0644   judge 启动脚本
│   ├── health.py                    root:root  0644   健康检查脚本
│   └── judge.json                   root:cherry-judge  0640   judge 配置（含控制面令牌）
├── releases/                        root:root  0755   发布目录，每个发布一个子目录，装好后不再改动
│   └── <发布名>/                    root:root  0755
│       ├── bin/judge                root:root  0755   judge 服务
│       ├── libexec/sandbox          root:cherry-judge  4754   沙箱执行器（setuid-root）
│       ├── rootfs/                  root:root  只读   用户程序看到的根文件系统
│       ├── manifest.json            root:root  0644   rootfs 逐文件清单
│       └── packages.lock.json       root:root  0644   rootfs 的软件包锁
├── current -> releases/<发布名>     符号链接（相对路径），指向当前使用的发布
├── judge/                           cherry-judge:cherry-judge  0700   judge 的运行数据，唯一的可写区
│   ├── boxes/                       0700   交给执行器的 box 目录根（协议见 sandbox-executor.md）
│   ├── blobs/                       0700   源码、编译产物等中间文件，有保留期
│   ├── testdata/                    0700   测试数据的本地副本（协议见 testdata.md）
│   └── logs/                        0700   judge 日志，按天滚动：judge.YYYY-MM-DD.log
├── operations/                      root:root  0700   运维脚本（安装、启停、卸载、验证），文件 0600
├── installation.json                root:root  0600   安装回执：账号、文件摘要、状态
└── unit-backup/                     root:root  0700   卸载时保存两个 systemd 单元的副本（0600），供恢复使用；安装后不存在
```

## 2. 不在家目录里的东西

只有下面这些，位置由系统规则决定：

| 位置 | 内容 |
|---|---|
| `/etc/systemd/system/cherry-sandbox.slice` | 节点的资源总闸（root:root 0644） |
| `/etc/systemd/system/cherry-sandbox-judge.service` | judge 服务单元（root:root 0644） |
| `/sys/fs/cgroup/cherry.slice/cherry-sandbox.slice/cherry-sandbox-judge.service/` | cgroup v2 子树，由单元委派；下设 `supervisor/`（judge 与执行器）和 `jobs/`（各次执行的组） |
| `/etc/passwd`、`/etc/group` 中的账号 | 见下 |

### 账号

均为不可登录、没有家目录的系统账号，uid 与 gid 同号。

| 账号 | uid | 用途 |
|---|---|---|
| `cherry-judge` | 61010 | judge 服务身份；拥有 `judge/`；是执行器的服务身份 |
| `cherry-payload-0` … `-3` | 61002 … 61005 | box N 里用户程序的身份 |
| `cherry-init-0` … `-3` | 61006 … 61009 | box N 里隔离环境 init 的身份 |

## 3. 规则

1. **家目录及其所有祖先目录必须归 root，且不被组或其他用户写入。** 执行器据此信任 `etc/executor.conf`，不满足则拒绝执行。
2. **程序与配置只读，运行数据只在 `judge/`。** `releases/`、`etc/`、`operations/` 在安装后不再被服务写入；服务能写的只有 `judge/`。
3. **一个发布装好后不再改动。** 要换版本就新增一个发布目录并改 `current`；不原地修改已有发布。
4. **`current` 是唯一的版本选择点。** 单元、judge 配置和部署核对都通过 `current` 访问程序，不写发布名。
5. **除 `libexec/sandbox` 外，发布里没有 setuid 位。** 执行器是 root 所有、`4754`、只有 `cherry-judge` 组可执行。
6. **路径固定。** 家目录路径编译进执行器、写进单元和部署清单，不能用环境变量或命令行参数改变。
7. **部署清单绑定关键文件。** judge 启动前核对清单中的文件摘要与 cgroup 资源上限；与实际不符则拒绝上线。
8. **`judge/` 下的缓存可以清空，日志按需保留。** 清空 `blobs/`、`testdata/` 不影响正确性，只会让下次判题重新取回。

## 4. 安装、卸载与保留

- **首次安装**要求家目录、两个 systemd 单元、上述账号都不存在；任何一个已存在即拒绝，不覆盖、不合并。
- **中途失败**保留已写入的内容和回执供检查，不自动回滚删除。
- **卸载**只停止并移除两个 systemd 单元，**保留**家目录、账号、配置、发布、审计记录和运行数据。需要彻底清理时由运维手工删除家目录和账号，删除前确认没有这些身份的进程。
- **安装后默认不启动、不开机自启**；启动是单独的显式操作。

## 5. 版本与兼容

- 本布局没有版本字段，家目录路径即约定。改动路径、属主、权限或新增必须存在的目录，需要同时改安装器、执行器的受信配置路径（需重新编译）、
  单元文件、部署清单规则、验证脚本和本文，并重装节点。
- 当前安装器不支持原地升级，换发布需卸载后重装。

## 6. 实测记录

2026-10-13，Ubuntu 24.04 / Linux 6.8 / x86_64（节点 `cherry-linux-3`，发布 `cherry-home-v1`）：按本文逐项核对属主与权限一致；
`verify-native`（线程降权、资源上限、无残留进程与 cgroup）、执行器崩溃恢复、judge 崩溃恢复均通过。
