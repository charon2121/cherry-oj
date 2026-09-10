# 原生节点安装准备（TASK-099）

2026-09-10经用户明确“安装”，首站已安装并启动三个原生服务，最终新节点cherry-linux-2通过SSH隧道注册为REGISTERED（cherry-linux-1保留为收敛权限前的历史环境），原环境仍ACTIVE。正式权限组合、C++编译执行、全部线程降权、24项资源限额、缺配置/清单/helper拒绝及恢复已实机验证，证据见WORK-048的VERIFY-049。正式单元在途崩溃、卸载/恢复和权限删减对照已完成；机器重启未授权、未验证，业务校准与验收属于TASK-100。最终结果见VERIFY-049。

## 首次部署清单

| 资源 | 固定值 |
|---|---|
| 节点ID / 发布目录 | cherry-linux-2 / work048-linux-v1（同一二进制/rootfs，权限清单已收敛） |
| sandbox账号 | cherry-sandbox，UID/GID61001 |
| judge账号 | cherry-judge，UID/GID61010；控制面token仅root与此组可读 |
| 预留槽位账号 | cherry-payload-0～3：61002/4/6/8；cherry-init-0～3：61003/5/7/9，均nologin、无home |
| 并发 / 队列 | 1 / 4；预留身份不代表启用4并发 |
| 持久目录 | /etc/cherry-sandbox、/var/lib/cherry-sandbox/releases/work048-linux-v1、service、judge |
| 运行状态 | /run/cherry-sandbox-helper，保留owner与锁供helper受控恢复 |
| 单元 | cherry-sandbox.slice、cherry-sandbox-helper.service、cherry-sandbox.service、cherry-sandbox-judge.service |
| 总限制 | 1536MiB、swap0、384 tasks、CPU200% |
| helper / sandbox / judge | 768/256/384MiB，192/96/96 tasks，CPU100/50/50%，swap均0 |
| helper子树 | supervisor128MiB/32tasks/CPU50%；jobs640MiB/160tasks/CPU100%，swap0、jobs OOM成组终止 |
| 监听地址 | sandbox 127.0.0.1:15050；judge 127.0.0.1:15051 |
| rootfs manifest | ed65f75e0f8f59c48e186a889d73d63b4523d001ec17766e4c27d098b23e29f0，对应已验证56包锁 |

安装器检查root/amd64/systemd>=255、路径/服务/账号与整个身份范围冲突，任何冲突拒绝首次安装。不安装宿主包、不修改全局LSM、防火墙、sysctl或SSH配置；保留云代理。首次安装只写文件与账号，不自动启动、enable或重启机器。中途失败保留installation.json及部分资源供核查，不递归回滚删除数据。

helper的七项capability集合位于唯一真源systemd/cherry-sandbox-helper.service；包含mount/pivot_root、降权、设备节点创建与文件回收需要的权限。systemd255的seccomp准备会丢弃未显式保留的SETUID，因此单独配置AmbientCapabilities=CAP_SETUID；仍受七项bounding集合约束。实测payload/init全部线程最终cap集合为零，NNP=1；未用删除NoNewPrivileges的方式规避启动失败。逐项删除七项能力均实测拒绝启动；CAP_SYS_CHROOT、CAP_FOWNER、CAP_KILL已删除，C++嵌套输入、0600产物读取及后台后代回收对照通过。该结论限于当前实现和首站环境。

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

release-source需要bin/{sandbox-helper,sandbox,judge}、rootfs/、manifest.json、packages.lock.json。构建脚本只构建二进制；rootfs按../rootfs/README.md从锁定包解包，源包许可证保留。先验证原始包摘要与manifest一致，不在安装时重新生成摘要接受偏差。

部署清单绑定sandbox/helper、rootfs manifest、包锁、helper/sandbox配置、单元与bootstrap摘要；Node.New另对真实judge二进制和执行配置计算摘要。Linux节点校验root保护、活动版本与实际cgroup总限额，通过隔离g++ --version探针读取工具链版本。缺清单、后端不匹配、文件/限额变化拒绝注册。部署清单不包含控制面token或endpoint来决定环境兼容性。

## 确认后执行

先把审核目录、安装脚本和完整release-source交付到独占准备目录。TOKEN_FILE必须是项目内私有0600文件，内容匹配当前Java控制面配置，禁止放进命令参数或日志。下列命令需要root；安装前检查实际文件清单与本次批准对象一致。

```sh
python3 install/manage.py install --review REVIEW_DIR --release-source RELEASE_DIR --token-file TOKEN_FILE
python3 install/manage.py status
python3 install/manage.py start
python3 install/manage.py stop
```

安装器将管理工具持久保存到`/var/lib/cherry-sandbox/operations`，摘要绑定installation.json；当前节点可直接用`python3 /var/lib/cherry-sandbox/operations/manage.py status|start|stop|uninstall|restore`。恢复测试工具verify-native.py、verify-lifecycle.py也保存在该目录。后者会短暂停止本项目服务并恢复原文件，只能在节点尚未承担正式业务或完成排空后运行；每次选择一个case，不能并发执行。

安装后先检查systemd-analyze verify，再实际验证helper最小权限、非特权服务、实际cgroup总限额与身份、注册回执、缺配置/策略/rootfs拒绝、服务崩溃恢复。`start`失败停止本次服务；`stop`等待服务组消失。当前不自动enable；机器重启恢复需另行明确授权和验证。

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

本轮完整候选包已生成在项目忽略目录`.local/work048-linux-v1-bundle`：含release/bin、固定manifest/包锁、rootfs.tar.gz、review、install脚本及SHA256SUMS。最终rootfs由已有镜像内的Linux tmpfs构建（无网络、768MiB内存/64进程/CPU100%、512MiB tmpfs），与TASK-098固定摘要一致；不是未核验的新rootfs。错误的macOS共享目录构建仅留作本地差异证据，不进入候选包。

正式授权后，候选包先传到本轮独占`/var/tmp/cherry-sandbox-work048-linux-v1`（root700），校验SHA256SUMS，再`tar --no-same-owner -xzf rootfs.tar.gz -C release`。安装器只接受完整release-source目录。控制面token另存0600私有文件，不打包；准备目录在保留审计后按本项目所有权清理。

本次原22文件归档保留为安装历史；实测修订后的目录现有24文件及新SHA256SUMS，新增两项原生验证脚本，另存`work048-linux-v1-bundle-revised.tar.gz`供后续初装。已装单元变更在首次注册前完成，旧单元/清单/回执及新旧摘要保存在installation-revisions/001-helper-setuid；持久运维工具在002-operations，实测日志在003-install-verification。临时上传token已删除，准备目录其余材料保留供审计；不要把旧归档当成修订版安装包。

正式helper崩溃曾发现遗留socket inode误报就绪，已修正为同时检查/proc/net/unix监听态；不以连接半开协议占用槽位。生产部署不使用测试drop-in。verify-faults.py逐项选择judge/sandbox/helper，verify-uninstall.py验证保留数据并restore/start，verify-capabilities.py只缩小helper权限作对照；它们会短暂停止新节点，只能串行、在排空后运行。权限对照最长120s，其余驱动90s，每次外层128MiB/swap0/32tasks/CPU50%。

最终交付使用`.local/work048-linux-mincaps-bundle.tar.gz`（27文件及SHA256SUMS），节点ID为cherry-linux-2、helper七项能力；较早的v1/revised归档只保留历史。故障恢复、卸载恢复和能力对照的最终日志在installation-revisions/007-task099-final。
