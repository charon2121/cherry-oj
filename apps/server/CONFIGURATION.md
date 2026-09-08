# 后端配置

每个服务的 `src/main/resources/` 使用相同约定：

| 文件 | 用途 | Git / 发布 JAR |
|---|---|---|
| application.yaml | 通用字段、安全默认值、CHERRY 环境变量映射 | 包含 |
| application-local.example.yaml | 本地填写示例，不会自动加载 | Git 包含，JAR 排除 |
| application-local.yaml | 本机配置，Spring 标准 local Profile 加载 | 均排除 |

## 本地开发

首次将每个服务的示例复制为 `application-local.yaml` 并填写真实值。已迁移的本机无需重新复制。
默认 `spring.profiles.default=local`，IDE 直接运行对应主类，或用 Maven spring-boot:run，
无需配置参数、环境变量、指定工作目录来寻找配置，也不需要先运行生成器。
不要在 IDEA 添加 SPRING_PROFILES_ACTIVE 或 spring.config.additional-location。
已有进程不会热加载这次改动，重新编译并启动后生效。

从旧配置迁移时，还需在 IDEA 的 Run → Edit Configurations 中检查实际运行项：
清除指向 `.local/*.properties` 的 `-Dspring.config.additional-location=...` 并保存。
IDEA 打开期间仅修改磁盘上的 `.run` / `workspace.xml`，可能不会更新内存中的运行项。
迁移完成以实际启动命令不再带旧参数、日志默认加载 `local`、readiness 返回 `UP` 为准。

本地值采用 Spring 属性名，例如 `spring.datasource.password`，不使用顶层 CHERRY 键。
自定义运行只在 Gateway 设置 `cherry.custom-run.enabled`。正式提交是否开放及消费消息
沿用 submission 的 accepting/messaging-enabled 和 judging 的 formal.enabled，按实际用途配置。
它们是不同的运行职责，不能混同为自定义运行的多服务开关。

密钥位置及测试数据目录建议填写本机绝对路径，避免 Maven/IDE 工作目录影响数据位置。
配置加载本身通过类路径，不受当前工作目录影响。示例中的空凭据必须填写，不能当有效默认值。

| 调用 | 发送方本地属性 | 接收方本地属性 |
|---|---|---|
| submission → problem | cherry.submission.problem-token | cherry.service-calls.submission-problem-tokens |
| submission → judging | cherry.submission.judging-token | cherry.service-calls.submission-judging-tokens |
| judging → submission | cherry.formal.submission-token | cherry.service-calls.judging-submission-tokens |

服务 token 是 32–512 位 URL-safe 字符，发送值必须属于接收列表。不要自动轮换或取轮换列表的首项。
judging 节点 control-token 与现有 Go 引擎配置匹配。数据库仍使用各服务专有账号，不共用 root。

## 容器环境

[后端 Compose](../../deploy/backend/compose.yaml) 只编排五个后端服务，复用已准备好的 MySQL、Redis、
Kafka 和 Go 判题节点；不创建数据库或修改账号。配置独立数据库、密钥目录及引擎网络后执行：

```bash
# 仓库根执行；编译需要 JDK 21
cd apps/server
./mvnw clean verify
cd ../../deploy/backend
cp .env.example .env
# 编辑 .env：填写隔离数据库、真实凭据、身份密钥路径等；BACKEND_PROFILE 不能为 local
# 首先检查配置（不要将含秘密的完整 config 输出粘贴到日志或聊天）
docker compose config --quiet
docker compose build
docker compose up -d
```

Compose 明确通过 `SPRING_PROFILES_ACTIVE` 选择 dev/test/prod，使用 environment 传入需要的键。
`.env` 只用于 Compose 插值，未被 environment 引用的键不会自动传入容器。
同一镜像可以适配不同环境；修改 .env 后 `docker compose up -d` 重建受影响容器。
不能只 `docker compose restart` 就期待更新容器环境变量。

所有容器默认仅 Gateway 暴露端口。数据库地址必须是容器可达地址，容器内 localhost 指向自身。
身份密钥目录只读挂载，运行 UID 10001 必须有读取权限。日志和业务数据使用独立卷；
本地 Docker 若连接现有依赖，应同时保证依赖允许容器网络访问，不为连通性扩大数据库用户权限。
prod/production 保留既有严格秘密检查与 Secure Cookie 行为；通过 HTTPS 代理接入。
开发 fixture 已移入测试资源，真实部署的 dev Profile 不会自动预置虚构判题环境。
本配置不意味着当前沙箱已能安全承载公网不可信程序。

## 测试、打包和迁移

Spring 集成测试显式选择非 local Profile，测试资源也将默认 Profile 设为 test；
测试库/身份密钥由既有 Testcontainers 或测试注入提供，不加载 application-local.yaml。
Maven jar 插件在打包前排除所有 application-local.*，Boot repackage 复用该无私有配置的 JAR。
Docker context 也排除源码私有文件；镜像只复制已验证 JAR，不复制开发 resources/target 目录。
发布 JAR 由部署环境提供秘密，不依靠本地 YAML。检查最终 JAR，不能只检查 Git 忽略规则。

本轮原配置备份位于 `apps/server/.local/backups/WORK-046-*/`，目录不入 Git。
原 .properties 私有值已迁移并保留备份；数据库密码与账号权限没有改变。
回退应先停止相应服务，恢复原 YAML、私有文件、POM 及启动项；不删除数据卷或重置密码。
