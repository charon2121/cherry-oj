# Cherry OJ Server

这里是 Cherry OJ 的 Java 服务端基础工程。当前已经建立五个可独立启动、测试和打包的 Spring Boot 服务；数据库、鉴权、题库、提交和异步判题等业务能力仍待后续任务实现。

如果你想理解 Maven、Spring Boot Starter、WebMVC 和 WebFlux 分别做什么，请看 [`TOOLCHAIN.md`](./TOOLCHAIN.md)。本页只讲怎样运行和验收现有工程。

## 五个服务分别负责什么

- `gateway-service`，端口 8080：浏览器唯一入口，后续负责路由、会话边界和入口级策略。
- `user-service`，端口 8081：用户、认证与角色。
- `problem-service`，端口 8082：题目版本、语言模板和测试数据元信息。
- `submission-service`，端口 8083：提交记录、不可变 JudgeInput 和判题状态。
- `judging-service`，端口 8084：判题环境、任务编排，以及与 Go judge 的通信。

这些是目标职责，也是模块边界。现阶段各服务只有启动入口、端口和健康检查，还没有业务 API、数据库或服务间路由。看到进程健康，只能证明基础工程可运行，不能代表相应产品功能已经完成。

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

在 `apps/server` 目录中运行：

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

### 本地默认与生产 Secret

五个 Java 服务的 `application.yaml` 都提供了可直接启动的本地默认值。准备好配置所指向的 MySQL
schema/账号和 Redis 后，启动命令本身不需要先 `export CHERRY_*`。默认值只解决配置输入，不会自动
创建数据库、账号，也不会启动 MySQL、Redis 或 Go Judge。

user-service 在没有显式 RSA 配置时，会在进程内随机生成一把仅限本地联调的临时签名密钥。密钥不会
写入仓库或磁盘，但服务重启后旧 JWT 会失效，且这种模式不能用于多实例或生产部署。启动日志会给出
临时密钥警告，不会打印密钥内容。

部署使用 `prod` 或 `production` Spring profile。该 profile 下，user/problem/judging 数据库密码和
user-service 的 `kid`、私钥位置、公钥位置都必须由环境或 Secret 显式提供；缺失时服务启动失败，不会
回落到本地口令或临时密钥。现有变量名保持不变：

- `CHERRY_USER_DB_PASSWORD`、`CHERRY_PROBLEM_DB_PASSWORD`、`CHERRY_JUDGING_DB_PASSWORD`；
- `CHERRY_AUTH_KEY_ID`、`CHERRY_AUTH_PRIVATE_KEY_LOCATION`、`CHERRY_AUTH_PUBLIC_KEY_LOCATION`。

Gateway 的 `CHERRY_REDIS_PASSWORD` 默认空字符串，表示本地 Redis 未启用鉴权，这是有意保留的有效
配置；需要密码时照常覆盖。

例如把所有 Java 日志写到 `/srv/log/cherry-oj`：

```bash
CHERRY_LOG_PATH=/srv/log/cherry-oj ./mvnw -pl gateway-service spring-boot:run
```

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
