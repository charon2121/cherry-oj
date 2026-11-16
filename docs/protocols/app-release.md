# 应用发布与升级协议

规定五个 Java 服务与 Web 发布包、已部署应用及运维工具之间的文件约定。本文是真源；
判题节点继续使用独立的[安装布局](./install-layout.md)，不由应用升级器管理。

## 快速使用

```sh
# 本机：从指定提交构建，输出目录必须尚不存在
python3 deploy/app/release.py build --ref HEAD --output /absolute/new-release
python3 deploy/app/release.py inspect --package /absolute/new-release/app.tar.gz --sha256 <打包输出的64位摘要>
# 已部署的测试服务器：沿用现有 MySQL 客户端凭据文件，不在命令行传密码
python3 deploy/app/manage.py upgrade --package /absolute/app.tar.gz --sha256 <同一摘要> \
  --mysql-container cherry-test-mysql --mysql-defaults-file /run/cherry-client.cnf --database-prefix cherry_test_
python3 deploy/app/manage.py rollback \
  --mysql-container cherry-test-mysql --mysql-defaults-file /run/cherry-client.cnf --database-prefix cherry_test_
```

成功退出 0；检查或操作失败退出非 0，输出安全的阶段与错误原因。发布包核验不执行其中的程序。
首次系统安装、依赖容器创建、账号初始化、数据库恢复、清理旧发布与公网开放不属于本协议。

## 1. 参与方与产物

构建工具从指定 Git 提交生成独立源码副本，使用项目既有 Java 21、Node 24 构建与检查入口，
不复用工作区的 JAR、dist、node_modules 或未提交配置；依赖下载缓存可以复用。
编译、测试或构建后源码一致性检查失败时，不发布成功产物。
产物通过文件复制交给 Linux 运维工具，再由 systemd 与 Nginx 消费。
运维工具只接管已存在的 `/opt/cherry-app` 安装，不隐式创建或重装系统服务。

输出目录包含 `app.tar.gz` 和 `package.json`。后者字段为：
`version: 1`、`release`、`sourceSha`、`sha256`（归档整体摘要）与 `sizeBytes`（归档实际字节数）。
用户必须通过 `--sha256` 单独传入期望摘要；归档内自带的摘要不能授权自身内容。
JSON 版本必须为整数 1，布尔不能充当整数；未知版本及未知字段拒绝。

## 2. 发布包与清单

归档根目录包含 `release.json`、五个 `jars/<service>-service.jar`、`web/index.html` 与其余 Web 静态资源，
以及 `ops/service-identity-keys.sh`。服务名固定为 `user`、`problem`、`submission`、`judging`、`gateway`。
附带密钥工具只供运维另行显式使用，升级、回滚和核验不得执行它。

`release.json` 的必填字段：

| 字段 | 约束 |
|---|---|
| `version` | 整数 `1` |
| `release` | `[a-z0-9][a-z0-9-]{0,63}`；构建器生成 `app-<sourceSha前12位>-<artifactSetSha256前12位>` |
| `sourceSha` | 实际构建提交的完整 40 位小写 Git SHA；不能以工作区当前提交替代产物来源 |
| `files` | 相对文件名到 64 位小写 SHA-256 的对象；逐一覆盖发布文件，排除清单自身 |
| `artifactSetSha256` | 对 `files` 执行 Python 标准库 `json.dumps(files, sort_keys=True)` 的 UTF-8 字节取 SHA-256，沿用现有算法 |

除上述字段外拒绝其他字段。普通文件总数（含清单）最多 16384，清单最多 2MiB，归档最多 512MiB，
普通文件实际解包总量最多 1GiB；读取、散列与解包都流式执行，不只依赖 tar 声明值。
相对路径每一分量匹配 `[A-Za-z0-9_][A-Za-z0-9_.-]*`，最长 4096 字符；禁止绝对路径、空分量、
`.`、`..`、反斜线、重复名称及文件/目录冲突。只接受普通文件，不接受归档链接、设备、FIFO 或其他特殊条目。
固定 JAR 与 ops 路径外，仅允许 `web/` 下资源。解包不得穿过符号链接父目录或覆盖已有内容。
缺失、额外、类型不符、摘要不符、越界或大小超限时整份发布拒绝，不通过重算清单接受偏差。

## 3. 已部署应用与回执

```text
/opt/cherry-app/                         root:root 0755
├── releases/<release>/                  root:root 0755，安装后不可变
│   ├── release.json                     root:root 0644
│   ├── jars/、web/                      目录0755，普通文件0644
│   └── ops/service-identity-keys.sh      root:root 0755
├── current -> releases/<release>        唯一程序版本选择点，相对链接
├── deployment.json                      root:root 0644，应用部署回执
└── operations/                          root:root 0700
    ├── lock                             运维操作互斥锁
    └── <operationId>/                   root:root 0700，文件0600
        ├── operation.json               操作阶段与校验事实
        ├── deployment.before.json       切换前的原始回执
        └── databases.sql.gz             成功完成的数据库备份；未完成只保留.partial
```

所有受信目录及祖先必须 root 所有且组/其他用户不可写，`current` 外不接受链接。
保留 `/etc/cherry-app/` 的五个环境文件、其引用的密钥、`/var/lib/cherry-app/` 数据、现有服务账号、
systemd 单元和 Nginx 配置。服务仍只写自己的运行目录。

新 `deployment.json` 字段为 `version: 1`、`status`（`HEALTHY | SWITCHING | RECOVERY_REQUIRED`）、
`release`、`previous`（发布名或 null）、`sourceSha`（旧发布允许 null）、`artifactSetSha256`、
`ports`、`services`、`webListen` 与 `operationId`。`release` 与 `previous` 不含路径。
`ports` 恰含五个服务名，值为 1–65535 的互不重复整数；`services` 恰含对应的五个
`cherry-app-<service>.service`；`webListen` 固定为 `127.0.0.1:18080`；`operationId` 为32位小写十六进制。
新回执不以旧 `judgeRelease` 作为原生节点的当前状态；应用回执只描述应用，实际节点状态另行观察。

## 4. 升级与失败处理

1. 获取互斥锁；存在未终结操作时拒绝新升级/回滚，要求先恢复。核验当前发布、回执、服务、监听、
   外部依赖和新包；新发布名必须未使用。在独立目录完整核验并封存新发布，检查失败不停止服务。
2. 记录原生节点的真实 `current`、服务 PID 与状态，升级全过程不得重启或改写该节点。
3. 停止 Gateway，再停止其余四服务并确认停止。记录数据库 Flyway 历史，创建四个业务 schema
   的一致性 MySQL 备份；失败或超过180秒终止备份进程，保留失败现场，恢复旧应用，不能切换程序。
   备份是升级前的数据库保护点，不等于完整系统备份或已完成恢复验证。
4. 持久化 `SWITCHING` 状态，用同目录临时链接原子替换 `current`。依次启动
   user、problem、submission、judging，Gateway 最后启动；核验每服务本机健康、Web 响应、
   文件摘要与原生节点未变化后，更新回执为 `HEALTHY`，保存原发布为 `previous`。
5. 启动失败时停止新服务并重新读取 Flyway 历史。与停机前完全一致时，切回旧发布和旧回执并启动旧应用；
   历史改变、读取失败或旧应用恢复失败时，保持服务停止，记录 `RECOVERY_REQUIRED`，明确报告阶段、
   发布和备份位置。不能把数据库已迁移的情况伪装成成功回滚，不自动导入 SQL 或执行降级迁移。

每个服务健康等待最多90秒，单次本机 HTTP 最多3秒、响应最多64KiB，不经过环境代理；
systemd 单元已失败立即报错，所有外部命令有界超时。命令失败的原始输出只留在0700操作目录，
控制台不打印凭据、环境文件、私钥、完整数据库内容或请求正文。

## 5. 回滚与中断恢复

`rollback` 只切回已记录的 `previous`，不接受任意外部发布路径。
先核验旧发布及其摘要，并要求当前与目标发布内四服务的 Flyway SQL 文件集合与内容摘要一致；
不一致时拒绝自动回滚，交由后续数据库恢复流程处理。通过后沿用同一停机、备份、切换、健康检查与失败恢复步骤。
完成后交换当前与上一个发布；不删除任何旧发布或备份。

`manage.py recover` 在显式调用后读取未终结操作、真实链接、服务状态与数据库历史。
尚未切换且真实链接仍指向完整原发布时，恢复原服务；已切换则仅在数据库历史未变且原发布仍完整时恢复，否则保持服务停止并报告需要人工恢复；
不能因进程中断或命令再次执行就跳过核验、误报成功或自动恢复数据库。

## 6. 操作记录

`operation.json` 版本为1，字段为 `operationId`、`action`（`UPGRADE | ROLLBACK`）、
`state`（`PREPARED | STOPPED | BACKED_UP | SWITCHED | HEALTHY | ROLLED_BACK | RECOVERY_REQUIRED`）、
`fromRelease`、`toRelease`、`fromManifestSha256`、`toManifestSha256`、`packageSha256`（回滚为null）、
`schemaBefore`、`schemaAfter`、`databaseBackupSha256`、`mysql`、`nodeBefore` 与 `errorCode`（无错误为null）。
尚未取得的备份摘要或数据库历史为null，不能填空摘要伪装已备份。
`mysql` 只含 `container`（容器名）、`defaultsFile`（客户端配置文件绝对路径）、`databasePrefix`；不含密码。
`nodeBefore` 只含 `release`（原生节点发布名）、`mainPid`（正整数）、`activeState`（systemd活动状态）。
数据库历史读取四个 schema 的 `flyway_schema_history`，按 `installed_rank` 排序，
保留 installed_rank、version、description、type、script、checksum、success；JSON紧凑排序编码后取SHA-256，
以服务名到摘要的对象写入 `schemaBefore/After`。历史缺失、失败记录或读取失败均不能当作空历史。
容器名、配置文件绝对路径、数据库前缀在拼接命令或标识符前分别校验；通过参数列表调用 Docker/MySQL，
不使用 shell 拼接，不读取或输出客户端配置文件内容。

## 7. 旧发布兼容与真源

2026-11-15已有无版本 `release.json`：最早只含 `sourceSha/files`；后续还有
`sourceBaseSha/sourceTreeSha256/artifactSetSha256`，其中 `sourceSha` 可为null。
只在现有 root 所有安装中读取这两类旧清单；仍核验全部文件、权限与安全路径，有摘要集合时一并核验。
没有的提交身份不能推断出来，历史清单不补版本、不重算、不改写。
旧回执中的 `previous: releases/<name>` 只在兼容读取时校验并解析；成功操作后才写新格式回执。
最早清单没有集合摘要时，可以在新操作记录中记录所观察的集合摘要，不把它回写成旧发布的承诺。
旧格式不作为新上传发布包的入口；未知的未来格式拒绝。消费者为构建器、核验器、升级器、回滚与恢复工具。

## 8. 实测依据

2026-11-15，测试服务器现有五服务、Web、C/C++/PyPy 原生节点已部署并通过既有真实链路验证。
本次只读核对：当前应用发布103个文件的摘要、root属主与不可被非root写入的权限全部通过；
旧回执中的判题版本为v7，实际原生发布为 `cherry-home-pypy-gc-20261115`。
同日，新工具从 `31e60df07ee068e6a72341464cf3406987589694` 独立导出源码，完成 Java `clean verify`、
Web 安装锁定依赖、`check` 与 `build`，生成版本1发布 `app-31e60df07ee0-209b5d088b57`。
新工具实际完成从旧格式发布升级、回滚到旧发布、再切回新发布；数据库备份与迁移历史核验通过。
另在真实链接已切换、操作记录仍为 `BACKED_UP` 时终止临时运维进程，`recover` 成功恢复到操作前发布，
五个服务健康，原生节点版本与主进程未变。Java 的 IPv4 映射回环地址已纳入监听核验，通配和公网地址仍拒绝。
部署工具30项隔离测试在 macOS 与目标 Ubuntu 上均通过，包含迁移历史改变或无法读取时拒绝自动回退；
未向真实业务库注入破坏性迁移。
数据库备份没有执行恢复导入，不等于完整系统恢复已验证。
