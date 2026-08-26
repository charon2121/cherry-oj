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

网关运行基础已经提供一个 browser-facing 的 `GET /api/status` 连通性资源。它按
`contracts/web-api.openapi.json` 返回 `{ data, meta }`，并由请求过滤器生成与 `meta.requestId` 相同的
`X-Request-Id`。统一异常处理将格式错误、校验错误、明确业务错误和未知故障映射为脱敏的 RFC 9457
`application/problem+json`。该资源只证明 Gateway 能处理 REST/JSON，不代表其它服务或基础设施健康，
也不表示业务路由已经接通。8080 的 Actuator 健康检查继续服务运维，页面不直接耦合其响应结构。

后续业务路由仍须由所属工作项明确 URI、拥有服务、失败语义和权限；不能因为 `/api/status` 可用就
让 Gateway 直接拥有用户、题目或提交数据。

Gateway 不应同时引入 WebMVC Starter。WebFlux 与 WebMVC 是两套不同的服务器模型，入口层保持 WebFlux，业务服务保持 WebMVC。

## 四个业务服务使用的依赖

### `spring-boot-starter-webmvc`

`user-service`、`problem-service`、`submission-service` 和 `judging-service` 使用它建立常规 HTTP API。它提供 Controller、JSON 转换、异常处理和内嵌 Web 服务器等基础能力。

WebMVC 只解决请求进入 Java 服务后的同步 Web 处理，不自动提供数据库、鉴权、消息队列或跨服务通信。这些能力尚未进入当前 POM。

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

当前 POM 没有 MySQL 驱动、MyBatis、Flyway、Spring Security、Kafka、服务注册或可观测性后端。这不是遗漏说明，而是当前实施阶段的真实边界。

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
