# 独立 Linux rootfs 构建

`build.py` 在有 `dpkg-deb` 的 Linux 构建机离线解包固定包集合，不运行安装脚本，不安装到宿主，不连接服务器。构建失败保留独立输出目录供检查，不覆盖已有目录。

包锁文件由实际选定的 Ubuntu/Debian 快照产生，必须包含以下字段（示意值不能用于构建）：

```json
{"version":1,"architecture":"amd64","layout":"usr-merged","source":"发行版快照的可追溯 URL/日期","packages":[{"file":"实际文件.deb","package":"实际包名","version":"完整版本","architecture":"amd64","sha256":"实际包 SHA-256"}]}
```

准备 C++ 编译器、binutils、运行库、动态加载器、提供 `/usr/bin/true` 的 coreutils 及其完整依赖，同时保留包内 `usr/share/doc/*/copyright`。不得删除来源许可证。首个可交付包锁须在 TASK-098 的目标工具链验证后固定，当前 [ubuntu24-amd64-smoke.lock.json](./ubuntu24-amd64-smoke.lock.json) 的56包集合已通过 Ubuntu 24.04 / Linux 6.8 / amd64 有界 C++ 编译运行冒烟；尚未通过完整安全验收，不能作为正式节点版本承诺。

```sh
python3 deploy/sandbox-linux/rootfs/build.py \
  --lock /absolute/project-local/packages.lock.json \
  --packages /absolute/project-local/debs \
  --output /absolute/project-local/rootfs-build-unique
```

输出含 `rootfs/`、`manifest.json`、原始锁文件。构建器清除 setuid/setgid 和组/其他用户写权限，将硬链接转换为独立 inode，拒绝特殊文件；预建私有挂载点及不可供 payload 访问的 `.sandbox`。manifest 覆盖全部条目、模式、链接目标和文件摘要，单独打印其 SHA-256。时间戳不参与身份，包内容与权限参与。

运行时由 TASK-099 将整个版本目录安装为 root 所有、非 root 不可写，配置固定 `RootFS`、`ManifestPath`、`ManifestSHA256`。helper 启动逐项校验，额外文件、缺失文件、摘要或权限不符均拒绝启动；不通过重新生成摘要来掩盖部署偏差。helper 自身以受保护的运行二进制绑定到隔离根内，payload 的请求不能选择这个入口。

测试：`PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/sandbox-linux/rootfs -p '*_test.py'`。这些测试只验证清单和文件处理，实际 g++ 编译/链接及隔离运行证据见 WORK-048/VERIFY-049；其他环境仍待运行。

helper 创建监听 socket 前，会通过同一隔离链运行 rootfs 中的 `true`，要求正常退出、真实资源计量和完整清理成功。此冒烟会实际使用 namespace/cgroup；部署时需先授权并准备独立节点资源，不能把启动 helper 当作只读探测。

`layout: "usr-merged"` 显式要求构建器为存在的 usr/bin、usr/sbin、usr/lib、usr/lib64 建立根目录固定相对别名。已有冲突或未知布局拒绝构建；省略 layout 不创建别名。布局计入锁与清单摘要。

TASK-099补充本地复现：`download.py --lock <锁文件> --output <新目录>`从Ubuntu官方归档下载锁定包，逐个核验既有SHA256，不改版本、不运行安装器。索引仅用于定位pool目录，授权内容仍是锁文件摘要；`--resume`只复用哈希一致的已有文件。apt本地文件名中的epoch与pool文件名不同，下载器明确处理这一规则。

**构建输出必须在原生Linux文件系统。** macOS共享目录即使由Linux容器写入，也可能改变symlink模式或合并大小写不同的文件。本轮直接写macOS bind mount所得manifest与已验证版本不同；改在有界Linux tmpfs组装后，manifest恢复为上述固定摘要。导出tar归档后直接在目标Linux解包，不先在macOS展开rootfs再复制。跨架构解包不算执行验证。
