# Cherry OJ Server

这里是 Cherry OJ 的 Java 服务端基础工程。当前包含五个可独立启动、测试和打包的 Spring Boot 服务，承载数据库、鉴权、题库、提交和异步判题。

如果你想理解 Maven、Spring Boot Starter、WebMVC 和 WebFlux 分别做什么，请看 [`TOOLCHAIN.md`](./TOOLCHAIN.md)。本页只讲怎样运行和验收现有工程。

## 五个服务分别负责什么

- `gateway-service`，端口 8080：浏览器唯一入口，后续负责路由、会话边界和入口级策略。
- `user-service`，端口 8081：用户、认证与角色。
- `problem-service`，端口 8082：题目（题面、样例、语言模板，没有版本）和测试数据：按[测试数据协议](../../docs/testdata-protocol.md)
  把上传的 ZIP 写成目录，数据库只存目录地址。
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

全仓架构与服务边界以 [`AGENTS.md`](../../AGENTS.md) 为准，跨语言请求结构以 [`contracts/`](../../contracts/) 为唯一真源。

## 判题节点与测试数据

本地先启动 MySQL/Redis 和五个 Java 服务，再从仓库根运行 `docker compose up -d --build`。
Judge 自动注册并续租，注册只带节点身份：`JUDGE_NODE_ID`、每次启动新生成的会话号、访问地址和
能判的语言，不上报机器信息，也没有「判题环境」分组。两端使用相同的
`CHERRY_JUDGE_CONTROL_TOKEN`；本地默认值为 `local-judge-control-token`，生产必须覆盖。
Compose 的 `judge-testdata` 是节点私有持久卷，无需 `TESTDATA_PATH` 或 `dev` profile seed。
修改 Java 端口时设置 `JUDGE_CONTROL_PLANE_URL`；修改 Judge 映射端口时同步 `JUDGE_ADVERTISE_URL`。
Judge 注册前做启动自检：对端必须是 cherry-oj 的 sandbox；原生 Linux 部署还要核对部署清单里的
发布文件摘要与 cgroup 上界。自检失败会拒绝节点上线。

判题与标定不再绑定节点：任何在线、声明了该语言的节点都行。判题时 judging-service 向 problem-service 取题目此刻的
测试数据地址与指纹（`cherry.judging.problem.token`，须属于 problem-service 的 `judging-problem-tokens`），
把地址交给节点，节点按[测试数据协议](../../docs/testdata-protocol.md)读取；节点要能读到该地址
（本地路径需挂载同一路径，见 Compose 的 `PROBLEM_TESTDATA_ROOT`）。
标定按「题目 × 语言」记录并带标定时的数据指纹：题目的测试数据换了，旧标定过期，需在工作台重新校准。

> 注：下面的端到端脚本仍是旧流程，随第 7 步改写。

隔离端到端验证：先运行 Maven package 和 `docker compose build judge`，再运行
`python3 apps/server/judging-service/scripts/node-e2e.py`。脚本建立独立 MySQL/Redis、五服务、
Compose 项目与卷，从 Finder ZIP 上传、绑定、部署到 C++ 校准，并验证节点停止/恢复。
结束只清理本次创建的资源，证据保存在打印的临时目录。


## 配置与本地启动

五服务统一使用 `application.yaml`、`application-local.example.yaml` 和不入库的
`application-local.yaml`。默认 Profile 为 local；首次填写本地文件后，直接运行主类即可，
不配置 VM 参数、Program 参数或环境变量。原 .local/*.properties 和 IDE 参数已移除。
完整配置说明、凭据配对及 Compose 操作见 [CONFIGURATION.md](./CONFIGURATION.md)。
