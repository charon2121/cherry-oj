# 独立 Linux rootfs 构建

`build.py` 在有 `dpkg-deb` 的 Linux 构建机离线解包固定 deb 与运行时归档，不运行安装脚本，不安装到宿主，不连接服务器。构建失败保留独立输出目录供检查，不覆盖已有目录。

**每门语言一份包锁、一棵独立的 rootfs**：[locks/](./locks/)`<语言>.lock.json`，文件名就是语言编号，必须与锁里的
`language` 一致，且在 judge 的语言注册表里（`apps/judge-engine/judge/language`；judge 与本目录的测试都会核对）。
包锁由 `lock.py` 从实际选定的快照解析产生，字段如下（示意值不能用于构建）：

```json
{"version":2,"architecture":"amd64","layout":"usr-merged","source":"发行版快照的可追溯 URL/日期","language":"c","roots":["gcc","libc6-dev","coreutils"],"packages":[{"file":"实际文件.deb","package":"实际包名","version":"完整版本","architecture":"amd64","sha256":"实际包 SHA-256"}],"archives":[]}
```

## 包锁 v2 与运行时归档

包锁是下载器、离线构建器、CI 复制校验与安装器之间的文件约定。`version` 必须为整数 `2`；
未知版本拒绝处理。`architecture`、`layout`、`source`、`language`、`roots` 与 `packages` 沿用原义，
新增必填 `archives` 数组（0–16 项）。仓库的 C/C++ 锁也切到 v2，`archives: []`，deb 版本与摘要不变。
下载用的并集锁保留 `archives`，但不带单门语言的 `language` 和 `roots`，不能拿并集锁构建某一门语言。

`packages` 仍是非空 deb 闭包。非 deb 运行时只通过 `archives` 登记，不用安装脚本或在线包管理器。
每项字段如下，未列字段拒绝，布尔不能当整数：

| 字段 | 类型与约束 |
|---|---|
| `file` | 普通文件名，`[A-Za-z0-9_][A-Za-z0-9_.-]*`，以 `.tar.gz` 结尾；与所有 deb/归档文件名不重复 |
| `url` | 固定 HTTPS URL；无用户名、密码、查询参数或片段；跟随跳转时仍须为 HTTPS；不使用 latest 地址 |
| `sha256` | 64 位小写十六进制；下载、缓存复用与构建前都核对 |
| `sizeBytes` | 整数，1–268435456；下载流量必须精确等于它，超过立即中止，不能只信 Content-Length |
| `stripPrefix` | 归档唯一顶层目录的普通名称，不含路径分隔符；移除它后解包 |
| `target` | rootfs 内相对目录，由普通路径分量组成，无空分量、`.` 或 `..`；新建且不覆盖既有内容，祖先必须是真目录 |
| `bin` | 对象，0–32 项，键为 `/usr/bin/` 下普通命令名，值为该归档内相对文件路径；生成指向该归档可执行文件的相对链接，拒绝重名及越界 |

固定的第一份归档是官方 PyPy 运行时，按完整目录保存库与许可证：

```json
{
  "file": "pypy3.11-v8.0.1-linux64.tar.gz",
  "url": "https://downloads.python.org/pypy/pypy3.11-v8.0.1-linux64.tar.gz",
  "sha256": "706965bcf372752ba69dafd59d860e51bc2fadbb0f8a47cec3426125d7058ceb",
  "sizeBytes": 37254555,
  "stripPrefix": "pypy3.11-v8.0.1-linux64",
  "target": "usr/lib/pypy3",
  "bin": {"pypy3": "bin/pypy3"}
}
```

所有 deb 与归档放在同一个下载目录。并集按文件名去重，仅完全一致的归档记录可以合并；
`--resume` 不复用符号链接或内容不符的文件，全缓存命中时不访问网络。下载失败只留下明确的未完成文件，
不把未核验的内容当成缓存；清理工具只删除本目录中锁外的普通 `.deb`/`.tar.gz` 文件。

构建器先核验全部来源，再在独立目录解包。归档最多 65536 个条目、普通文件实际解压总量最多 512 MiB；
拒绝绝对路径、父目录穿越、重复条目、硬链接和特殊文件。仅接受普通文件、真目录及归档内部的相对符号链接；
禁止通过符号链接父目录写入，所有链接最终必须落在该归档目录内。归档不能覆盖 deb 内容或其他归档。
随后沿用统一的权限收紧、许可证保留与 manifest 封存，不执行归档中的任何代码。

构建输出、部署清单、安装目录与执行器请求格式不变。安装器仍按已审核的锁与 manifest 摘要接收发布，
不重新计算摘要来接受偏差。旧发布保持原文件与原摘要，可以原地升级和回滚；构建器不改写旧发布里的 v1 锁。
受影响的消费者是包锁生成、下载、构建、合并缓存、CI 来源复制与校验；每端都必须识别 v2 后才能使用新锁。

发布里每门语言装在 `toolchains/<语言>/{rootfs,manifest.json,packages.lock.json}`；节点能判哪些语言就是装了哪些工具链。
judge 启动时逐门编译运行冒烟程序，全部通过才以这些语言注册，任何一门失败就拒绝上线。每门的 manifest 与包锁摘要
都由部署清单绑定；加一门语言、换一门的工具链，都是出一个新发布。

每门 rootfs 只装这门语言要的编译器或解释器、运行库、动态加载器、提供 `/usr/bin/true` 的 coreutils 及其完整依赖，
同时保留包内 `usr/share/doc/*/copyright`，不得删除来源许可证。当前 `cpp`（56 包）、`c`（51 包）与
`pypy`（10 个 deb + 上述完整运行时归档）已通过 Ubuntu 24.04 / Linux 6.8 / amd64 的启动冒烟。
2026-11-15，三语言节点通过既有 Benchmark 判题 72 项、攻击样本 56 项；PyPy 另通过标准库、JIT、CORE、
语法错误、TLE/MLE/OLE 与网络拒绝共 9 项原生验证，以及原地升级和回滚后的复查。
尚未通过完整安全验收，不能作为正式节点版本承诺。

组装发布里的全部工具链（下载与缓存只需要所有锁的并集，同一个包在不同锁里摘要必须相同）：

```sh
python3 deploy/sandbox-linux/rootfs/toolchains.py union --output UNION.lock.json
python3 deploy/sandbox-linux/rootfs/download.py --lock UNION.lock.json --output PACKAGES_DIR
python3 deploy/sandbox-linux/rootfs/toolchains.py build --release RELEASE_DIR --packages PACKAGES_DIR --work WORK_DIR
python3 deploy/sandbox-linux/rootfs/toolchains.py digests --release RELEASE_DIR    # 交给 render.py --toolchain
```

单独构建一棵 rootfs：

```sh
python3 deploy/sandbox-linux/rootfs/build.py \
  --lock /absolute/project-local/packages.lock.json \
  --packages /absolute/project-local/debs \
  --output /absolute/project-local/rootfs-build-unique
```

输出含 `rootfs/`、`manifest.json`、原始锁文件。构建器清除 setuid/setgid 和组/其他用户写权限，将硬链接转换为独立 inode，拒绝特殊文件；预建执行器要用的挂载点（`work`、`tmp`、`proc`、`dev`、`.oldroot`）。manifest 覆盖全部条目、模式、链接目标和文件摘要，单独打印其 SHA-256。时间戳不参与身份，包内容与权限参与。

运行时将整个版本目录安装为 root 所有、非 root 不可写，judge 的启动配置固定每门语言的 rootfs、manifest 路径与 manifest 摘要。启动脚本（install/judge-start.py）逐门逐项校验，额外文件、缺失文件、摘要或权限不符均拒绝启动；不通过重新生成摘要来掩盖部署偏差。执行器每次把请求语言的那棵树只读挂进隔离环境；请求只能给语言编号，挂载来源由受信配置的 `toolchains` 决定。

测试：`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/sandbox-linux/rootfs -p '*_test.py'`。这些测试只验证清单和文件处理，实际 g++ 编译/链接及隔离运行由 Benchmark 工作流在真实节点上验证。

judge 开放 HTTP 端口前，进程内执行层会通过执行器运行 `execution.probeToolchain` 那门语言 rootfs 中的 `true`，要求正常退出、真实资源计量和完整清理成功。此冒烟会实际使用 namespace/cgroup；部署时需先授权并准备独立节点资源，不能把启动 judge 当作只读探测。

`layout: "usr-merged"` 显式要求构建器为存在的 usr/bin、usr/sbin、usr/lib、usr/lib64 建立根目录固定相对别名。已有冲突或未知布局拒绝构建；省略 layout 不创建别名。布局计入锁与清单摘要。

## 生成包锁

```sh
python3 deploy/sandbox-linux/rootfs/lock.py --language c --root gcc --root libc6-dev --root coreutils \
  --output deploy/sandbox-linux/rootfs/locks/c.lock.json [--address-failover] [--index-dir <预先下载的索引目录>]
```

`lock.py` 从与 `download.py` 相同的快照读 noble、noble-updates 的 `Packages` 索引（默认 main），沿 Depends 与
Pre-Depends 解析根包的闭包：同名包取最高版本（dpkg 版本比较），一组「A | B」已有可满足的就不加、否则取第一个，
虚包取按名排序的第一个提供者；这些选择都打印出来供审阅，版本约束满足不了直接报错。快照不稳定时可以先用 curl 把
`<套件>-<组件>-Packages.xz` 下载到一个目录，用 `--index-dir` 离线解析，同一份索引可重复得到同一份包锁。

对照：以 `g++`、`coreutils` 为根，只用 noble 索引解析出的包集合与现有手工挑选的 56 个包完全一致（2026-11-02）。

## 取得锁定包

```sh
python3 deploy/sandbox-linux/rootfs/download.py --lock <锁文件> --output <新目录> [--resume]
```

下载器按包锁从 Ubuntu 快照 `https://snapshot.ubuntu.com/ubuntu/20260910T000000Z/`（生成包锁时的时间点）
取索引和包，逐个核验锁里的 SHA256，不改版本、不运行安装器。不再直连官方归档：它只保留每个包的当前
版本，锁定版本被更新替换后就从 pool 删除（2026-10 实测 glibc、OpenSSL、linux-libc-dev 共 5 个包已 404）。
快照地址写在 `download.py` 的 `SNAPSHOT` 常量里，更换包锁时一起更换。

索引仅用于定位 pool 目录，授权内容仍是锁文件摘要；`--resume` 只复用哈希一致的已有文件，锁里的包都已在目录里时不访问快照。apt 本地文件名
中的 epoch 与 pool 文件名不同，下载器明确处理这一规则。

长期留存暂不处理：Ubuntu 说明快照预期至少保留两年但不承诺永久，快照取不到时构建会失败，届时再定方案。
包锁不定期轮换，满足任一条件才升级：安全通告波及沙箱内会被用户代码或编译直接利用的组件；需要换编译器或增加
语言运行时；距上次升级约半年（快照日期接近两年保留期时必须升级）。步骤：选新快照重新生成包锁并同步
`download.py` 的 `SNAPSHOT` → 构建 rootfs 并记下 manifest 摘要 → 在测试服务器上走正式安装与判题回归（含 benchmark）
→ 编译器或运行库有变化时重新标定 → 用安装器的 `upgrade` 逐个节点切换，失败时回退；命令与限制见[安装器说明](../install/README.md#升级回滚与清理)。

**构建输出必须在原生Linux文件系统。** macOS共享目录即使由Linux容器写入，也可能改变symlink模式或合并大小写不同的文件。本轮直接写macOS bind mount所得manifest与已验证版本不同；改在有界Linux tmpfs组装后，manifest恢复为上述固定摘要。导出tar归档后直接在目标Linux解包，不先在macOS展开rootfs再复制。跨架构解包不算执行验证。
