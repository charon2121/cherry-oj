# Cherry OJ Server

这里是 Cherry OJ 的 Java 服务端基础工程。当前包含五个可独立启动、测试和打包的 Spring Boot 服务，承载数据库、鉴权、题库、提交和异步判题。

如果你想理解 Maven、Spring Boot Starter、WebMVC 和 WebFlux 分别做什么，请看 [`TOOLCHAIN.md`](./TOOLCHAIN.md)。本页只讲怎样运行和验收现有工程。

## 五个服务分别负责什么

- `gateway-service`，端口 8080：浏览器唯一入口，后续负责路由、会话边界和入口级策略。
- `user-service`，端口 8081：用户、认证与角色。
- `problem-service`，端口 8082：题目版本、语言模板和测试数据元信息。
- `submission-service`，端口 8083：提交记录、不可变 JudgeInput 和判题状态。
- `judging-service`，端口 8084：判题环境、任务编排，以及与 Go judge 的通信。

这些职责也是模块边界。进程健康不等于产品功能完成，具体能力仍须验证完整调用链。

## 先完成一次全量构建

需要 JDK 21。项目自带 Maven Wrapper，会使用仓库固定的 Maven 3.9.16，不要求电脑预先安装 Maven。

```bash
cd apps/server
java -version
./mvnw clean verify
```

第一次运行 Wrapper 会下载 Maven 和项目依赖，需要能访问 Maven Central。构建成功后，五个模块都会完成编译、测试和打包。

Windows PowerShell 使用：

```powershell
cd apps/server
.\mvnw.cmd clean verify
```

## 启动一个服务

首次按 [配置说明](./CONFIGURATION.md) 填写本地文件后，在 `apps/server` 目录中运行：

```bash
./mvnw -pl gateway-service spring-boot:run
```

另开终端检查健康状态：

```bash
curl -sS http://127.0.0.1:8080/actuator/health
```

期望看到包含 `"status":"UP"` 的 JSON。停止服务时在运行它的终端按 `Ctrl+C`。

把命令中的模块名替换为 `user-service`、`problem-service`、`submission-service` 或 `judging-service`，即可分别启动其他服务。需要联调全部服务时，应在五个终端中分别启动，避免后台进程的日志和生命周期无人管理。

## 常用命令

### 构建全部模块

```bash
./mvnw clean verify
```

`clean` 先删除旧构建产物，`verify` 会走完编译、测试、打包和验证阶段。这是提交前的主要后端检查。

### 只测试一个模块

```bash
./mvnw -pl problem-service test
```

适合开发中快速反馈。提交前仍应回到根目录执行全量 `verify`，避免只验证到局部模块。

### 打包但跳过测试

```bash
./mvnw package -DskipTests
```

只适合已经单独跑过测试后的本地排查，不是正常验收命令。产物位于各模块的 `target/`。

## 配置在哪里

每个服务的 `src/main/resources/application.yaml` 定义基础服务信息与日志策略：

- `spring.application.name`：服务名。
- `server.port`：本地监听端口。
- `management.endpoints`：开放 `health` 和 `info`，并启用健康探针。
- `logging`：console/file 使用相同 JSON 字段，文件目录由 `CHERRY_LOG_PATH` 覆盖并按 UTC 日期滚动。
- `management.tracing`：HTTP 使用 W3C Trace Context；本地只关联日志，不启用 OTLP 导出。

根目录 `pom.xml` 统一定义 Java 版本、Spring Boot 父工程、Spring Cloud 版本、五个服务和共享的
`logging-support` 模块。服务通过该模块获得 MVC/WebFlux HTTP 完成日志与 Trace 关联，不复制过滤器。
完整字段、传播边界和 Go 对齐规则见 [`../../docs/logging.md`](../../docs/logging.md)。

### 本地配置与容器环境

基础配置不再内置数据库密码或服务秘密。按 [CONFIGURATION.md](./CONFIGURATION.md)
准备本地私有文件；容器通过 Compose 选择非 local Profile 并提供环境变量。
发布 JAR 不包含本地配置；缺少必要凭据不能用开发默认密码启动。

## 产品审核时看什么

后端任务不应只提供“服务启动成功”的截图。产品负责人优先审核：

- 新增了哪个用户或运营能力，对应什么 API 行为。
- 正常、参数错误、无权限、资源不存在和服务异常时分别返回什么。
- 哪些服务和数据参与这条链路，失败后用户能否理解和恢复。
- 是否有可复现的请求示例和验收证据。

Maven 版本、BOM 或构建插件属于技术审核范围；只有它们改变了可部署性、接口行为或故障表现时，才需要上升为产品验收点。

## 常见问题

### `release version 21 not supported`

当前终端使用的不是 JDK 21。先检查 `java -version` 和 `JAVA_HOME`，切换 JDK 后重新运行 Wrapper，不要修改项目的 Java 版本来适配个人电脑。

### 端口已被占用

错误日志会出现 `Port 808x was already in use`。先关闭旧进程，或确认是否已经有同一服务在运行。端口属于前后端本地联调约定，不应临时改完后提交。

### Wrapper 下载失败

确认当前网络能访问 Maven Central。Wrapper 固定下载 Maven 3.9.16，随后 Maven 还会下载 Spring 依赖；离线环境需要提前准备本地仓库。

### Gateway 健康但 `/api` 仍然 404

这是当前骨架的预期状态：Gateway 已能启动，但尚未配置业务路由。路由应随对应业务任务实现和验收，不能把健康检查当成接口完成证据。

全仓架构与服务边界以 [`CLAUDE.md`](../../CLAUDE.md) 为准，跨语言请求结构以 [`contracts/`](../../contracts/) 为唯一真源。

## 判题节点与测试数据

本地先启动 MySQL/Redis 和五个 Java 服务，再从仓库根运行 `docker compose up -d --build`。
Judge 自动注册并续租，首次空 judging 数据库自动建立 ACTIVE 环境。两端使用相同的
`CHERRY_JUDGE_CONTROL_TOKEN`；本地默认值为 `local-judge-control-token`，生产必须覆盖。
Compose 的 `judge-testdata` 是节点私有持久卷，无需 `TESTDATA_PATH` 或 `dev` profile seed。
修改 Java 端口时设置 `JUDGE_CONTROL_PLANE_URL`；修改 Judge 映射端口时同步 `JUDGE_ADVERTISE_URL`。
Judge 经 sandbox 的限时探测获取实际 CPU、内核、系统、编译器、资源配额与二进制摘要，计算环境指纹。
探测失败会拒绝节点启动；Judge 自身二进制摘要同样纳入指纹。升级 sandbox 时必须同步重启 Judge 重新探测。
改变实际运行环境需要使用新 `JUDGE_NODE_ID`，并为它设置新的 `JUDGE_TESTDATA_VOLUME`，例如
`JUDGE_NODE_ID=judge-v2 JUDGE_TESTDATA_VOLUME=cherry-judge-v2 docker compose up -d --build`。
旧身份和旧安装回执不能用于另一环境；保留旧卷用于回退，不覆盖旧目录，回退时恢复原节点 ID 与卷名。

既有数据库先升级 V2 并显式保留 `legacy-local`；历史环境、部署与标定都不改写。
已有其他指纹的 ACTIVE 环境不会自动替换，新节点只会 REGISTERED。准备迁移时，先记录旧/新环境 ID，
确认允许新环境暂时因尚未部署/校准而不可用，然后生成可审阅的切换 SQL：

```bash
python3 apps/server/judging-service/scripts/switch-environment.py OLD_UUID NEW_UUID > /tmp/switch-judge.sql
# 审阅目标 ID 后，在 judging 数据库用 mysql 批处理执行；不要使用 --force。
mysql --defaults-extra-file=/安全路径/mysql.cnf cherry_oj_judging < /tmp/switch-judge.sql
```

脚本本身只生成 SQL。事务会校验原 ACTIVE、目标 REGISTERED/RETIRED 和在线租约，并与注册/回执共用锁；
不满足时整段回滚。切换后启用 `node-remote`，重新部署并对新环境校准，不能复制旧标定。
校准遵循现有版本状态机：只有 DRAFT 可开始验证；已 READY_FOR_REVIEW 的版本应在旧环境完成发布，
已发布版本则创建复用测试数据的新草稿修订，再在新环境校准。旧发布版本与旧环境标定保持不变。
回退时把 OLD/NEW 对调并加 `--legacy-target`，再恢复下述 legacy 配置；历史标定仍属于原环境。

工作台每 10 秒刷新发布检查。节点离线后，部署按钮显示原因并禁用；节点恢复后可以重新部署，
节点检查已有目录的 hash/manifest 后返回回执，无需重新上传 ZIP。

回退：设置 `CHERRY_JUDGE_DEPLOYMENT_MODE=legacy-local`，配置原 Java 测试数据目录，并执行
`TESTDATA_PATH=/绝对路径 docker compose -f compose.yaml -f compose.legacy.yaml up -d`。
回退保留 V2 表与节点卷，不删除旧资产；旧目录权限仍需允许 Judge 的 UID 10001 读取。

隔离端到端验证：先运行 Maven package 和 `docker compose build judge`，再运行
`python3 apps/server/judging-service/scripts/node-e2e.py`。脚本建立独立 MySQL/Redis、五服务、
Compose 项目与卷，从 Finder ZIP 上传、绑定、部署到 C++ 校准，并验证节点停止/恢复。
结束只清理本次创建的资源，证据保存在打印的临时目录。


## 配置与本地启动

五服务统一使用 `application.yaml`、`application-local.example.yaml` 和不入库的
`application-local.yaml`。默认 Profile 为 local；首次填写本地文件后，直接运行主类即可，
不配置 VM 参数、Program 参数或环境变量。原 .local/*.properties 和 IDE 参数已移除。
完整配置说明、凭据配对及 Compose 操作见 [CONFIGURATION.md](./CONFIGURATION.md)。
