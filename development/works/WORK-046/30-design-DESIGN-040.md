---
id: "DESIGN-040"
type: "design"
title: "统一服务配置与环境启动管理"
status: "checked"
work: "WORK-046"
owners: ["codex/root"]
depends_on: ["CHANGE-012"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-08"
updated_at: "2026-09-08"
---

# DESIGN-040：统一后端服务配置

## 背景

用户最终确认：使用 Spring 标准 application-local.yaml 命名，本地私有配置留在项目内，不入 Git；直接运行主类，不要求参数或环境变量。容器通过 Compose 环境变量调整配置。本轮实施聚焦 apps/server 的五个后端服务，不扩建配置中心或全栈运维平台。

## 目标与限制

沿用 Spring Boot 配置文件、Profile、环境变量、已有配置绑定与校验。禁止自定义配置导入框架、local.yaml、用户主目录配置、生成器或必需启动包装器。不依赖 -D/-- 或 Maven run.jvmArguments 注入配置才能启动。

## 整体方案

每个服务 src/main/resources 采用三种文件：

- application.yaml：入 Git，列出该服务支持的项目配置字段、环境变量映射、说明和安全默认值；spring.profiles.default=local。必要秘密没有真实默认值。
- application-local.example.yaml：入 Git，说明本地所需填写值和配套凭据，不自动加载，不放真实秘密。
- application-local.yaml：不入 Git，本地私有值，复制示例填写一次；通过 Spring 标准 local Profile 自动加载，不使用 spring.config.import 或命令行指定路径。

应用默认 local 只用于开发便利；Docker Compose 明确通过 SPRING_PROFILES_ACTIVE=dev/test/prod（按其用途）选择非 local Profile。普通配置差异由 Compose 环境变量覆盖同一份 application.yaml，不为不同地址复制 application-dev/test/prod 文件；只有确有 Bean 行为差异才保留必要 Profile。原 judging application-dev.yaml 的环境预置必须盘点后分离/迁移，不能把虚构的开发指纹带入其他环境。

本地五个服务均须验证：不传配置参数、不设置环境变量、不先运行脚本，直接主类启动可读取本地文件。已有本机密码、服务凭据、地址、功能开关迁移后保持原语义，不能只迁移数据库连接。配置绑定缺失或非法时按启用功能明确报错。

Compose 使用 environment/env_file 为对应服务显式提供值，.env 仅参与插值，不等同于自动传入容器。非 local Profile 下 application-local.yaml 不参与加载；环境变量覆盖优先级与现有 CHERRY 映射按项目 Boot 实际运行验证。调整环境值后重建容器生效，不宣称动态刷新。

## 模块与数据

Gateway、user、problem、submission、judging 全部纳入。保持 Redis、数据库、Kafka、身份密钥、服务鉴权、节点部署和日志配置边界；不改公开接口、账号权限、业务流程或沙箱。服务关系在示例/说明中列配对表，不新建分发组件。

## 打包与测试隔离

application-local.yaml 同时加入 Git 忽略、发布 JAR 排除、Docker 构建排除；检查 Spring Boot 重打包、脏 target 和开发树构建，不能仅靠 .gitignore。开发运行类路径允许加载私有文件；发布产物不包含私有文件。测试明确使用测试 Profile 和隔离配置，不能因默认 local 而加载开发秘密、连接开发库；先审计所有现有 Spring 测试启动方式，再调整测试配置。

发布 JAR 通过部署环境配置启动，不要求读取本地秘密。没有必要凭据时明确失败，不能通过关闭鉴权制造可用状态。

## 接口与状态

使用现有 Spring 日志、健康检查和构建版本核实实际 Profile 与配置来源类别。不得输出秘密、完整环境或连接串中的密码。实际功能验收不能仅靠 XML、配置探针或进程存活。

## 安全与失败

私有配置迁移前备份，比较值仅输出匹配结果；保留未知项，冲突不覆盖，不重置数据库密码。移除基础 YAML 中的真实密码默认值，但将本机有效值完整保留在私有文件。功能启用时其必需配置必须完整；不更改鉴权语义、限流或运行租约。

## 监控与部署

本轮交付五服务的 Compose 环境配置样例及必要后端编排/构建适配，验证不同环境通过环境变量覆盖；不新增 Web/Go 功能或修改判题镜像、沙箱实现。原有 Go 判题环境作为集成验证依赖。当前验收未完成及沙箱已知限制继续记录。

## 迁移与兼容

逐服务盘点所有 @Value/@ConfigurationProperties、YAML、IDE/进程配置及 .local 文件，明确值来源和配对关系。生成本地私有文件，验证等值与实际加载后删除旧 .local 导入及 IDE 参数。删除旧文件前备份，避免泄露。旧 judging dev fixture 单独审查迁移，不覆盖真实节点数据。不写 ~/。

## 验证与回退

五服务零配置参数/零环境变量的本地配置加载及可用环境中的实际启动；现有后端测试、默认 local 下测试隔离；Compose 覆盖与非 local Profile；含私有文件的开发树打包后 JAR/镜像秘密排除；登录、正式提交、自定义运行与代码回看回归。真实环境不足时记录未完成项，不伪报通过。回退恢复私有备份和入口，不改数据库或数据卷，不停止用户未授权的进程。

## 备选方案

采用标准 Profile 与 Compose 环境覆盖。撤销自定义 local.yaml/import、用户目录及配置解析框架。辅助脚本仅做操作便利，不参与配置解析或成为启动前置。

## 风险与重审条件

若现有打包或测试方式无法隔离 application-local.yaml，先修正标准构建/测试配置再迁移，不引入配置框架绕过。业务接口、数据库或沙箱变更超出当前边界，需另行说明。

## 变更记录

- 2026-09-08：结构与内容校验通过，由工具置为 checked。
