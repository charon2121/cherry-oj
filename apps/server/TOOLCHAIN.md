# Server 工具链说明

这份文档解释 `apps/server` 中已经实际使用的 Java 构建工具和 Spring 依赖。阅读重点是职责边界：谁负责统一版本、谁负责启动 Web 服务、谁只在测试阶段出现，以及产品审核需要看到什么结果。

## 一次后端改动怎样变成交付物

```text
Java 源码和 application.yaml
        │
Maven Wrapper 启动固定版本的 Maven
        │
根 pom 统一 Java、Spring Boot、Spring Cloud 和模块清单
        │
子模块 Starter 提供 Web、校验、健康检查与测试能力
        │
compile → test → package → verify
        │
各模块 target/ 中的可执行 Spring Boot JAR
```

业务功能由 Java 代码和配置决定；Maven 与插件负责把它稳定地检查、测试和打包。产品负责人通常审核 API 与故障行为，技术负责人审核版本管理和构建链路。

## JDK 21

JDK 是编译和运行 Java 的基础环境。根 `pom.xml` 通过 `java.version` 固定为 21，保证开发机、CI 和部署环境使用同一语言级别。

JDK 不是 Maven 依赖，不能通过 `pom.xml` 自动安装。若本地 JDK 版本错误，构建会在业务测试之前失败。

## Maven Wrapper 与 Maven 3.9.16

`mvnw`、`mvnw.cmd` 和 `.mvn/wrapper/maven-wrapper.properties` 组成 Maven Wrapper。它让开发者运行 `./mvnw` 时自动使用项目固定的 Maven 3.9.16，避免“我的 Maven 能构建、你的不能”。

优先使用 `apps/server/mvnw` 从聚合工程执行命令。各子模块也保留 Wrapper，可用于模块独立场景，但日常验收应从根工程运行，确保五个服务都进入构建。

## 根 `pom.xml` 为什么存在

根 POM 的 `packaging` 是 `pom`，它本身不生成业务服务，主要承担四件事：

- 聚合五个服务与 `logging-support` 基础库，让一条命令能构建全部服务。
- 继承 Spring Boot Parent 的通用版本和 Maven 默认配置。
- 用 Spring Cloud BOM 统一 Cloud 组件版本。
- 统一 Java 版本和 Spring Boot 打包插件。

子模块把根工程声明为 `parent`，因此不需要各自重复这些配置。

## Parent、BOM、Dependency 和 Plugin 的区别

### Spring Boot Parent

`spring-boot-starter-parent` 4.1.0 是根 POM 的父工程。它提供经过协调的依赖版本、编译参数和常用插件默认值。它的作用类似“全项目基础配置”，不是一个会被业务代码调用的类库。

### Spring Cloud BOM

`spring-cloud-dependencies` 2025.1.2 通过 `dependencyManagement` 导入。BOM 只管理兼容版本，不会因为被导入就把所有 Spring Cloud 组件装进服务；只有子模块显式声明的 Gateway Starter 才会进入构建。

### Dependency

`dependencies` 中的 Starter 和测试库会进入模块的编译或测试 classpath。Starter 通常会继续带入一组经过验证的传递依赖，因此 POM 不需要逐个列出 Web 服务器、JSON 等底层包。

### Spring Boot Maven Plugin

`spring-boot-maven-plugin` 是构建插件，不是运行时代码。它支持 `spring-boot:run`，并把普通 JAR 重新打包成包含运行依赖、可直接启动的 Spring Boot JAR。

## Gateway 使用的依赖

### `spring-cloud-starter-gateway-server-webflux`

这是浏览器入口 Gateway 的响应式网关能力，负责后续的请求匹配、转发和过滤器链。它基于 WebFlux 的非阻塞模型，适合入口层处理大量短连接与转发。

Gateway 的 browser-facing 路由只承载公开业务 API；每条路由都须由所属工作项明确 URI、拥有服务、
失败语义和权限。统一异常处理将格式错误、校验错误、明确业务错误和未知故障映射为脱敏的 RFC 9457
`application/problem+json`。8080 的 Actuator 健康检查继续服务运维，页面不直接耦合其响应结构。

Gateway 不应同时引入 WebMVC Starter。WebFlux 与 WebMVC 是两套不同的服务器模型，入口层保持 WebFlux，业务服务保持 WebMVC。

## 四个业务服务使用的依赖

### `spring-boot-starter-webmvc`

`user-service`、`problem-service`、`submission-service` 和 `judging-service` 使用它建立常规 HTTP API。它提供 Controller、JSON 转换、异常处理和内嵌 Web 服务器等基础能力。

WebMVC 只解决请求进入 Java 服务后的同步 Web 处理，不自动提供数据库、鉴权、消息队列或跨服务通信；
各服务必须按职责显式引入这些能力。

## problem-service 的数据工具

problem-service 单独引入 MyBatis Spring Boot Starter 4.0.1、Flyway、MySQL Connector/J 和测试阶段的
Testcontainers MySQL。Flyway 只执行 `src/main/resources/db/migration` 中已发布、只追加的 migration；
MyBatis 的 XML mapper 负责题库键集分页和详情批量读取；Connector/J 只让该服务连接
`cherry_oj_problem`，不会扩大到其它服务数据库。

Apache Commons Compress 只在 problem-service 内处理私有测试数据 ZIP。它让服务在不解压到
业务目录的前提下识别 symlink/非普通文件，并配合实际流量、解压总量、单文件和压缩比
限额拒绝 ZIP 穿越与解压炸弹。原包保存到 `CHERRY_TEST_DATA_ROOT` 配置根内，READY 文件
只读封存，不与 judging-service 共享目录。

## judging-service 的部署与校准工具

judging-service 独立引入 Spring JDBC、Flyway、MySQL Connector/J、Commons Compress 与测试阶段的
Testcontainers MySQL。它只连接 `cherry_oj_judging`，V1 migration 创建环境、启用语言、测试数据部署、
语言校准和审计五类事实；不读取 problem-service 数据库。

测试数据由受 ADMIN JWT 保护的内部接口以 manifest 和 ZIP 流传入。服务在
`CHERRY_JUDGE_TESTDATA_ROOT` 下核对摘要、文件清单、测例对、安全限额与 UTF-8，然后通过同文件系统原子
rename 生成 Go Judge 使用的 `<testDataVersionId>/` 目录。校准通过环境表的 `endpoint_ref` 调用现有
Go Judge `/judge`，源码只存在于当次请求内，数据库和审计仅保存源码摘要及有界结果摘要。
部署根、临时目录使用 0700，测例使用 0400；生产部署必须让 judging-service 与只读挂载该目录的 judge
使用同一受控 Unix UID（Compose 为 10001），不能靠放宽为全局可读来解决权限问题。启动恢复只扫描
受控根下一层、只清理超过 stale-age 且没有 READY 回执的 UUID 目录与临时目录。

生产 profile 不创建默认环境。首次环境使用显式启动参数
`--cherry.judging.provision.enabled=true` 并完整提供 `cherry.judging.provision.*` 字段；若已有 ACTIVE
环境会拒绝执行，且该命令不承担环境切换。`dev` profile 只对 ID、指纹、路由与语言完全相同的本地 C++
fixture 幂等返回，任何差异仍拒绝启动。

测试使用临时 MySQL 8.4 验证真实 CHECK、外键、JSON、UUID 与索引计划。开发 profile 的 A+B 是应用启动
后的幂等 seed，不在 migration 或生产 profile 中。生产环境通过 `CHERRY_JUDGING_DB_URL`、
`CHERRY_JUDGING_DB_USERNAME` 和 `CHERRY_JUDGING_DB_PASSWORD` 提供最小权限连接。

## 本地启动默认与生产配置边界

五个服务的 `application.yaml` 对本地启动必需的 `CHERRY_*` 配置提供默认值，开发者准备好默认地址上的
MySQL/Redis 后可以直接执行 `spring-boot:run`。空 Redis 密码表示本地无鉴权，judging 环境预置字段在
功能关闭时允许为空，`${LOG_FILE}` 由 Spring 日志系统在运行时提供；这些都不是缺失的环境变量。

user-service 默认使用进程内随机生成的临时 RSA 密钥。它只服务单进程本地联调，不落盘，重启即更换。
显式提供 `CHERRY_AUTH_KEY_ID`、`CHERRY_AUTH_PRIVATE_KEY_LOCATION` 和
`CHERRY_AUTH_PUBLIC_KEY_LOCATION` 后仍按原逻辑读取 PEM，并支持上一把公开密钥。

`prod` 或 `production` profile 是部署安全边界：三个数据库密码和 user-service 的三项当前密钥配置
没有默认值，必须由部署 Secret 注入；user-service 代码也拒绝 `generated:local` 和默认本地 `kid`。
本地数据库默认口令是公开开发约定，不具备 Secret 属性，不得用于共享、测试或生产环境。

## 五个服务共有的依赖

### `logging-support`

仓库内共享基础库，统一提供 Spring 原生 JSON 日志、MVC/WebFlux HTTP 完成事件和 W3C Trace 关联。
它只处理日志与传播，不包含业务 DTO、Metrics、远端 exporter 或采集后端；五个服务仍独立打包部署。
日志文件目录通过 `CHERRY_LOG_PATH` 配置，并按 UTC 日期滚动。

### `spring-boot-starter-actuator`

提供运行状态与运维端点。当前配置只暴露 `/actuator/health` 和 `/actuator/info`，并启用健康探针。它回答“进程是否健康”，不回答“某个产品流程是否正确”。

### `spring-boot-starter-validation`

提供 Bean Validation，使后续 Controller 可以通过注解声明必填、长度和数值范围，并在业务逻辑前拒绝非法输入。仅仅引入依赖不会自动完成 API 校验，DTO 仍需明确约束并测试错误响应。

### `spring-boot-starter-test`

提供 Spring Boot 测试、JUnit 和常用断言、Mock 能力。它的 `scope` 是 `test`，只在测试编译与执行时可用，不会进入生产运行 classpath。

当前每个模块只有应用上下文启动测试。后续业务实现应补 Controller、领域规则、持久化和契约测试，不能把“上下文能启动”当成完整质量证据。

## 当前明确还没有的工具

数据库与资源服务器能力只按服务职责引入：user-service、problem-service 和 judging-service 已拥有各自
MySQL 持久化/Flyway 与 Spring Security，submission-service 仍没有数据库工具。整个 reactor 尚未引入
Kafka、服务注册或可观测性后端。这不是遗漏说明，而是当前实施阶段的真实边界。

当任务需要这些能力时，应单独说明：由哪个服务拥有、解决哪个业务问题、失败策略是什么、怎样测试，以及是否改变跨服务契约。不要因为架构路线图提过某项技术，就提前把依赖装进所有模块。

## Maven 命令分别做什么

- `./mvnw clean verify`：清理旧产物，编译、测试、打包并完成全部验证；提交前主命令。
- `./mvnw test`：编译并执行测试，不生成最终交付包；开发中快速反馈。
- `./mvnw package`：执行测试并生成 JAR，但不会执行 `verify` 之后可能增加的检查。
- `./mvnw -pl problem-service test`：只选择一个模块运行测试。
- `./mvnw -pl gateway-service spring-boot:run`：从源码启动一个服务，适合本地联调。
- `./mvnw dependency:tree`：查看直接和传递依赖，用于排查版本来源，不是日常验收命令。

`-DskipTests` 会跳过测试执行，应只用于明确的本地排查。它不能作为任务完成证据。

## 产品经理怎样审核后端工具变更

看到“新增 Starter”或“升级 Spring”时，可以要求任务把技术语言翻译成下面这些结果：

- 它支撑哪条用户、运营或部署能力。
- 会改变哪个服务、接口或错误表现。
- 是线上运行依赖、测试依赖，还是只影响构建。
- 是否扩大服务的数据权限或网络边界。
- 用什么请求、测试或部署检查证明变更有效。

如果只是无行为变化的补丁版本升级，产品负责人不必审核每个传递依赖；技术负责人需要给出全量 `verify` 和兼容性证据。如果升级改变接口、鉴权、路由或故障恢复，才进入产品验收。

## 新增、升级或删除依赖前

1. 先确认依赖应放在根 POM、`dependencyManagement`，还是某一个子模块。
2. 避免把一个服务的数据库、业务实体或客户端共享给其他服务。
3. 判断它是直接依赖、版本 BOM、构建插件还是测试依赖，不要混放。
4. 记录配置入口、运行时权限、故障方式和可观测信号。
5. 为业务行为补测试，从 `apps/server` 运行 `./mvnw clean verify`。
6. 同步更新本说明；涉及跨语言 DTO 时，同时核对 `contracts/`，不能在 Java 侧另造字段。
