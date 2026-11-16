# 应用发布工具

用于已有测试环境的五个 Java 服务与 Web，不接管原生判题节点或创建基础设施。
文件格式、失败语义与兼容规则以[应用发布协议](../../docs/protocols/app-release.md)为准。
只使用 Python 3.11+ 标准库；本机构建需要 Git、Java 21、Node 24，以及 Java 集成测试使用的 Docker。

## 构建与核验

从仓库根目录执行：

```sh
python3 deploy/app/release.py build --ref HEAD --output /absolute/new-output
python3 deploy/app/release.py inspect --package /absolute/new-output/app.tar.gz --sha256 <package.json中的sha256>
```

输出目录必须不存在。构建器导出指定提交后，执行 Maven `clean verify`、`npm ci`、Web `check` 与 `build`，
再检查原始源码未变化。工作区的未提交源码、配置和遗留产物不进入构建；失败现场和日志保留在输出目录。
成功得到 `app.tar.gz`、`package.json` 与 `release.json`。分发归档时单独核对其期望摘要。

## 测试环境升级与回滚

先上传归档与本目录五个运行模块（`artifacts.py`、`host.py`、`state.py`、`manage.py`、`release.py`），
在已安装应用的 Linux 服务器上由 root 执行：

```sh
python3 manage.py upgrade --package /absolute/app.tar.gz --sha256 <期望摘要> \
  --mysql-container cherry-test-mysql --mysql-defaults-file /run/cherry-client.cnf --database-prefix cherry_test_
python3 manage.py rollback \
  --mysql-container cherry-test-mysql --mysql-defaults-file /run/cherry-client.cnf --database-prefix cherry_test_
```

MySQL 凭据通过容器内既有客户端配置提供；容器需要 `mysql`、`mysqldump` 和 GNU `timeout`。
工具只对 `/opt/cherry-app` 操作，使用既有账号、服务、环境文件、密钥、Nginx 和数据目录。
升级会停止应用并备份数据库，原生判题服务继续运行。所有应用服务健康后才完成切换。

备份与操作记录位于 `/opt/cherry-app/operations/<operationId>/`，只允许 root 访问。
新发布失败且数据库历史未变时自动恢复旧应用，命令仍返回失败；不能自动恢复时保留操作材料并报告具体位置。
`rollback` 只使用记录中的上一发布，并要求两发布的数据库迁移完全一致；不会导入备份或执行数据库降级。

## 中断恢复

进程中断后，新的升级或回滚会拒绝执行。先运行：

```sh
python3 manage.py recover \
  --mysql-container cherry-test-mysql --mysql-defaults-file /run/cherry-client.cnf --database-prefix cherry_test_
```

数据库参数须与原操作一致。恢复工具核对实际链接与已保存的发布摘要，而非只信日志中的最后一步。
数据库已变化或旧发布损坏时不会强行启动旧程序；按操作目录里的回执、日志与备份另行处理。
数据库备份尚不构成完整系统恢复方案，测试数据目录、密钥和依赖持久数据的恢复需单独演练。

## 测试

```sh
PYTHONDONTWRITEBYTECODE=1 python3 -m unittest discover -s deploy/app -p '*_test.py'
```

测试使用临时目录、构建提交和进程替身，不连接测试服务器或真实业务数据库。
覆盖发布篡改、路径与归档边界、来源绑定、升级失败、迁移后拒绝回退、互斥、中断恢复与备份进程期限。
