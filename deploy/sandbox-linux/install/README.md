# 原生节点安装

## 首次部署清单

| 资源 | 固定值 |
|---|---|
| 节点ID / 发布目录 | cherry-linux-2 / work048-linux-v1（同一二进制/rootfs，权限清单已收敛） |
| judge账号 | cherry-judge，UID/GID61010；控制面token仅root与此组可读；也是执行器受信配置里的服务身份 |
| box账号 | 按并发数创建：cherry-payload-N 从 61100 起，cherry-init-N 从 61400 起（box N 取基数加 N），各留 256 个编号；均nologin、无home |
| 并发 / 队列 | `render.py --parallelism N`（1～256，默认 1）/ 4；并发数同时决定执行器的 `box_count`、judge 的 `parallelism` 和创建的账号数 |
| 家目录 | **`/opt/cherry-oj`**：本项目在节点上的全部持久文件都在这里，卸载清理只看这一处。例外只有 systemd 单元（`/etc/systemd/system`）与 cgroup（按 cgroup v2 的位置） |
| 目录布局 | `etc/` 配置（root 所有）、`releases/<版本>/` 与 `current` 链接、`judge/{boxes,blobs,testdata,logs}`（服务用户所有，工作区）、`operations/` 运维脚本、`installation.json` 回执 |
| 单元 | cherry-sandbox.slice、cherry-sandbox-judge.service（执行层在 judge 进程内，2026-09-30 起） |
| 资源限制 | 只加在每次执行的组上（由执行器按请求设置）；slice、judge 服务、supervisor 和 jobs 不设总量上限，也不核对 |
| 隔离执行器 | releases/<版本>/libexec/sandbox，root:cherry-judge 4754（setuid-root，仅 judge 组可执行；其他人可读，供部署清单核对摘要） |
| 监听地址 | judge 127.0.0.1:15051 |
| rootfs manifest | ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0，对应已验证56包锁 |

安装器检查root/amd64/systemd>=255、路径/服务/账号与整个身份范围冲突，任何冲突拒绝首次安装。不安装宿主包、不修改全局LSM、防火墙、sysctl或SSH配置；保留云代理。首次安装只写文件与账号，不自动启动、enable或重启机器。中途失败保留installation.json及部分资源供核查，不递归回滚删除数据。

隔离由 setuid-root 的一次性执行器（apps/sandbox）完成，没有常驻特权进程。执行器需要的八项能力（SYS_ADMIN、SETUID、SETGID、SETPCAP、CHOWN、DAC_OVERRIDE、MKNOD、KILL；KILL 用于执行器意外死亡时让 init 的 PDEATHSIG 生效）以 judge 服务单元的能力边界集为唯一真源：setuid 取得的能力不会超出它；judge 进程本身非 root、没有任何有效能力。payload/init 全部线程最终 cap 集合为零、NNP=1。verify-capabilities.py 逐项删除八项能力，每次 judge 都必须在执行层启动冒烟时失败。

代价：为了让 setuid 生效，judge 服务单元不能设置 NoNewPrivileges，也不能设置对非 root 服务隐含它的 seccomp 类硬化（RestrictAddressFamilies、LockPersonality、ProtectKernel*、RestrictSUIDSGID、RestrictNamespaces 等），也不能设置 ProtectControlGroups（cgroupfs 必须可写）。合并前这些让步只落在不联网的 sandbox 服务上，合并后落在要连控制面的 judge 上，这是 2026-09-30 把执行层并入 judge 时明确接受的取舍；judge 仍保留 ProtectSystem=strict、ProtectHome、PrivateTmp 与只允许本机的 IP 访问控制。

回环监听防止外部直接访问；HTTP没有新增应用层鉴权，宿主root/运维及云代理属于可信域。任务network namespace无法访问宿主回环。不能把此配置用于存在恶意宿主普通用户的共享机器并宣称按Judge身份鉴权。

## 本机IDEA后端与SSH隧道

用户确认使用本机IDEA后端。只读确认judging-service监听8084；既有Docker判题节点5051不受影响。新节点通过以下双向隧道联调：

```sh
ssh -N -T -o ExitOnForwardFailure=yes -o ServerAliveInterval=15 -o ServerAliveCountMax=3 \
  -L 127.0.0.1:15051:127.0.0.1:15051 \
  -R 127.0.0.1:18084:127.0.0.1:8084 "$SANDBOX_TEST_SSH"
```

远端ControlPlaneURL=http://127.0.0.1:18084，AdvertiseURL=http://127.0.0.1:15051（由本机Java经本地转发访问）。地址/认证来自项目内被忽略的连接记录；本目录不保存实际服务器地址、密码或私钥。当前隧道已启动，控制socket在项目忽略目录.local/tunnel.sock；断开后注册/心跳失败，不能伪造在线。它是本次联调连接，没有配置开机自启。

## 生成审核材料与构建

在仓库根执行。`install/render.py`不改系统，只写新的输出目录；占位token不能用于安装。

```sh
python3 deploy/sandbox-linux/install/render.py \
  --output deploy/sandbox-linux/.local/work048-linux-v1-review \
  --release work048-linux-v1 --node-id cherry-linux-2 \
  --control-url http://127.0.0.1:18084 --advertise-url http://127.0.0.1:15051 \
  --manifest-sha256 ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0
sh deploy/sandbox-linux/build-release.sh /absolute/project/deploy/sandbox-linux/.local/work048-linux-v1-release
```

release-source需要bin/{sandbox,judge}、libexec/sandbox（执行器）、rootfs/、manifest.json、packages.lock.json。构建脚本构建二进制（执行器需要gcc与libseccomp-dev）；安装器把执行器单独设为setuid-root，其余文件一律清除setuid位；rootfs按../rootfs/README.md从锁定包解包，源包许可证保留。先验证原始包摘要与manifest一致，不在安装时重新生成摘要接受偏差。

部署清单绑定sandbox与执行器二进制、rootfs manifest、包锁、执行器/sandbox/启动配置、单元与启动脚本摘要。judge注册前自检：root保护、current下实际启动的文件与清单一致、实际cgroup限额与清单一致；缺清单、后端不匹配、文件/限额变化拒绝上线。sandbox启动脚本另外逐文件核对rootfs与manifest，不一致拒绝启动。

## 确认后执行

先把审核目录、安装脚本和完整release-source交付到独占准备目录。TOKEN_FILE必须是项目内私有0600文件，内容匹配当前Java控制面配置，禁止放进命令参数或日志。下列命令需要root；安装前检查实际文件清单与本次批准对象一致。

```sh
python3 install/manage.py install --review REVIEW_DIR --release-source RELEASE_DIR --token-file TOKEN_FILE
python3 install/manage.py status
python3 install/manage.py start
python3 install/manage.py stop
```

安装器将管理工具持久保存到`/opt/cherry-oj/operations`，摘要绑定installation.json；当前节点可直接用`python3 /opt/cherry-oj/operations/manage.py status|start|stop|uninstall|restore`。恢复测试工具verify-native.py、verify-lifecycle.py也保存在该目录。后者会短暂停止本项目服务并恢复原文件，只能在节点尚未承担正式业务或完成排空后运行；每次选择一个case，不能并发执行。

安装后先检查systemd-analyze verify，再实际验证执行器最小能力、非特权服务、实际cgroup总限额与身份、注册回执、缺配置/策略/rootfs拒绝、服务崩溃恢复。`start`失败停止本次服务；`stop`等待服务组消失。当前不自动enable；机器重启恢复需另行明确授权和验证。

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
