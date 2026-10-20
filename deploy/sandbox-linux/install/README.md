# 原生节点安装

## 部署清单

| 资源 | 固定值 |
|---|---|
| 节点ID / 发布名 | 由 `render.py --node-id` / `--release` 指定；发布装到 `releases/<发布名>/` |
| judge账号 | cherry-judge，UID/GID61010；控制面token仅root与此组可读；也是执行器受信配置里的服务身份 |
| box账号 | 按并发数创建：cherry-payload-N 从 61100 起，cherry-init-N 从 61400 起（box N 取基数加 N），各留 256 个编号；均nologin、无home |
| 并发 / 队列 | `render.py --parallelism N`（1～256，默认 1）/ 4；并发数同时决定执行器的 `box_count`、judge 的 `parallelism` 和创建的账号数 |
| 家目录 | **`/opt/cherry-oj`**：本项目在节点上的全部持久文件都在这里，卸载清理只看这一处。例外只有 systemd 单元（`/etc/systemd/system`）与 cgroup（按 cgroup v2 的位置） |
| 目录布局 | `etc/` 配置（root 所有）、`releases/<版本>/` 与 `current` 链接、`judge/{boxes,blobs,testdata,logs}`（服务用户所有，工作区）、`operations/` 运维脚本、`installation.json` 回执 |
| 单元 | cherry-sandbox.slice、cherry-sandbox-judge.service（执行层在 judge 进程内，2026-09-30 起） |
| 资源限制 | 只加在每次执行的组上（由执行器按请求设置）；slice、judge 服务、supervisor 和 jobs 不设总量上限，也不核对 |
| 隔离执行器 | releases/<版本>/libexec/sandbox，root:cherry-judge 4754（setuid-root，仅 judge 组可执行；其他人可读，供部署清单核对摘要） |
| 监听地址 | judge 127.0.0.1:15051 |
| rootfs manifest | 由 `../rootfs/build.py` 按锁定包生成；其 SHA-256 经 `render.py --manifest-sha256` 写进部署清单 |

安装器检查root/amd64/systemd>=255、路径/服务/账号与整个身份范围冲突，任何冲突拒绝首次安装。不安装宿主包、不修改全局LSM、防火墙、sysctl或SSH配置；保留云代理。首次安装只写文件与账号，不自动启动、enable或重启机器。中途失败保留installation.json及部分资源供核查，不递归回滚删除数据。

隔离由 setuid-root 的一次性执行器（apps/sandbox）完成，没有常驻特权进程。执行器需要的八项能力（SYS_ADMIN、SETUID、SETGID、SETPCAP、CHOWN、DAC_OVERRIDE、MKNOD、KILL；KILL 用于执行器意外死亡时让 init 的 PDEATHSIG 生效）以 judge 服务单元的能力边界集为唯一真源：setuid 取得的能力不会超出它；judge 进程本身非 root、没有任何有效能力。payload/init 全部线程最终 cap 集合为零、NNP=1。verify-capabilities.py 逐项删除八项能力，每次 judge 都必须在执行层启动冒烟时失败。

代价：为了让 setuid 生效，judge 服务单元不能设置 NoNewPrivileges，也不能设置对非 root 服务隐含它的 seccomp 类硬化（RestrictAddressFamilies、LockPersonality、ProtectKernel*、RestrictSUIDSGID、RestrictNamespaces 等），也不能设置 ProtectControlGroups（cgroupfs 必须可写）。合并前这些让步只落在不联网的 sandbox 服务上，合并后落在要连控制面的 judge 上，这是 2026-09-30 把执行层并入 judge 时明确接受的取舍；judge 仍保留 ProtectSystem=strict、ProtectHome、PrivateTmp 与只允许本机的 IP 访问控制。

回环监听防止外部直接访问；HTTP没有新增应用层鉴权，宿主root/运维及云代理属于可信域。任务network namespace无法访问宿主回环。不能把此配置用于存在恶意宿主普通用户的共享机器并宣称按Judge身份鉴权。

## 控制面地址

`--control-url` 是 judge 注册与心跳要访问的 judging-service，`--advertise-url` 是控制面回访 judge 的地址。
节点只在回环上监听；控制面不在同一台机器上时，用 SSH 隧道把两个回环端口接起来，例如：

```sh
ssh -N -T -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
  -L 127.0.0.1:15051:127.0.0.1:15051 \
  -R 127.0.0.1:18084:127.0.0.1:8084 "$SANDBOX_TEST_SSH"
```

此时远端用 `--control-url http://127.0.0.1:18084 --advertise-url http://127.0.0.1:15051`。隧道断开后注册与心跳失败，
节点不会被当成在线。地址与认证来自项目内被忽略的连接记录，本目录不保存服务器地址、密码或私钥。

## 生成审核材料与构建

在仓库根执行。`install/render.py`不改系统，只写新的输出目录；占位token不能用于安装。

```sh
python3 deploy/sandbox-linux/install/render.py \
  --output REVIEW_DIR --release RELEASE_NAME --node-id NODE_ID \
  --control-url http://127.0.0.1:18084 --advertise-url http://127.0.0.1:15051 \
  --manifest-sha256 MANIFEST_SHA256 --parallelism 2
sh deploy/sandbox-linux/build-release.sh /absolute/path/to/RELEASE_DIR
```

release-source需要bin/judge、libexec/sandbox（执行器）、rootfs/、manifest.json、packages.lock.json。构建脚本构建二进制（执行器需要gcc与libseccomp-dev）；安装器把执行器单独设为setuid-root，其余文件一律清除setuid位；rootfs按../rootfs/README.md从锁定包解包，源包许可证保留。先验证原始包摘要与manifest一致，不在安装时重新生成摘要接受偏差。

部署清单（`etc/deployment.json`）绑定八项摘要：执行器、rootfs manifest、包锁、执行器受信配置、judge 启动参数、slice、judge 单元与启动脚本；必须声明哪些项由 judge 代码规定（`judge/node/preflight/deployment_spec.go`），不由清单自己决定。judge 注册前自检：清单受 root 保护、`current` 下实际启动的执行器正是清单里那一份、各项摘要一致；缺清单、后端或架构不符、文件变化都拒绝上线。资源限额只加在每次执行上，自检不核对。启动脚本 `judge-start.py` 另外逐文件核对 rootfs 与 manifest，不一致拒绝启动。

## 安装与启停

先把审核目录、安装脚本和完整release-source放到节点上的独占准备目录。TOKEN_FILE必须是项目内私有0600文件，内容匹配当前Java控制面配置，禁止放进命令参数或日志。下列命令需要root；安装前核对审核目录里的文件清单。

```sh
python3 install/manage.py install --review REVIEW_DIR --release-source RELEASE_DIR --token-file TOKEN_FILE
python3 install/manage.py status
python3 install/manage.py start
python3 install/manage.py stop
```

安装器将管理工具持久保存到`/opt/cherry-oj/operations`，摘要绑定installation.json；当前节点可直接用`python3 /opt/cherry-oj/operations/manage.py status|start|stop|uninstall|restore`。恢复测试工具verify-native.py、verify-lifecycle.py也保存在该目录。后者会短暂停止本项目服务并恢复原文件，只能在节点尚未承担正式业务或完成排空后运行；每次选择一个case，不能并发执行。

安装后先检查systemd-analyze verify，再实际验证执行器最小能力、非特权服务、cgroup 身份、注册回执、缺配置/策略/rootfs拒绝、服务崩溃恢复。`start`失败停止本次服务；`stop`等待服务组消失。当前不自动enable；机器重启后的恢复尚未验证。

初始版本无可回退的已硬化版本，回退为`stop`。未来版本切换需要新发布清单、身份与校准，当前安装器拒绝覆盖已有安装。

```sh
python3 install/manage.py uninstall
```

卸载校验所有权/摘要并拒绝未知drop-in，停止本项目单元及空slice、disable并移除所登记unit文件；保留账号、发布内容、配置、审计和题目数据。不是抹盘卸载。原单元备份在unit-backup且摘要纳入回执，`restore`只恢复原单元，不启动或enable，之后显式`start`。恢复拒绝文件/账号/备份变更、外来单元或drop-in冲突。已有文件被改动时先审查，不强制覆盖或删除。

## 本地验证

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/sandbox-linux/install -p '*_test.py'
# 在apps/judge-engine执行：
go test -race ./...
go vet ./...
CGO_ENABLED=0 GOOS=linux GOARCH=amd64 go build ./...
```

systemd行为参考官方[委派说明](https://github.com/systemd/systemd/blob/main/docs/CGROUP_DELEGATION.md)与[服务执行选项](https://github.com/systemd/systemd/blob/main/man/systemd.exec.xml)。发行版名称不代替运行验证；首轮部署只准备Ubuntu24.04/systemd255/amd64，其他配置仍须独立验证。
