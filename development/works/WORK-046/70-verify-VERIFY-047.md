---
id: "VERIFY-047"
type: "verify"
title: "统一服务配置与环境启动管理"
status: "approved"
work: "WORK-046"
owners: ["codex/root"]
depends_on: ["TASK-091"]
related: []
implements: []
verifies: ["CHANGE-012#AC-001", "CHANGE-012#AC-002", "CHANGE-012#AC-003", "CHANGE-012#AC-004", "CHANGE-012#AC-005", "CHANGE-012#AC-006", "TASK-091"]
tags: []
result: "partial"
created_at: "2026-09-08"
updated_at: "2026-09-09"
---

# VERIFY-047：五个后端服务配置重构

## 验证对象

五服务 application.yaml/application-local.yaml、示例、标准 Profile、测试/产物隔离及 Compose 环境覆盖。

## 对应要求

CHANGE-012#AC-001 至 AC-006。

## 检查与结果

- AC-001：五个服务默认 local，无自定义 import。用真实 ConfigDataEnvironmentPostProcessor 在不设置系统环境/配置参数、随机无关工作目录下分别加载，全部读取到对应私有配置；各服务 9/8/23/18/21 项值与私有文件匹配。临时探针 /tmp/Work046LocalProbe.java。四个数据库服务再通过真实 JDBC SELECT 1，均成功；未运行迁移或修改数据库。
- AC-002：/tmp/Work046MigrationProbe.java 逐项对比原 YAML + 原 properties 与新 YAML，全部等值；身份密钥相对路径转换为指向同一文件的绝对路径。三条服务凭据关系完全匹配。五个本地文件模式 600、被 Git 忽略，示例入库且秘密为空。原值在 .local/backups/WORK-046-20260908-180050/ 私有备份。
- AC-003：Compose dev/test/prod 三种环境通过 docker compose config --format json 检查，均五服务、明确非 local Profile、成对凭据一致、没有 command/entrypoint 参数覆盖。使用合成值，不打印真实配置。该模板只编排后端，MySQL/Redis/Kafka/Go 节点是明确的外置前提，未启动真实五服务容器栈。
- AC-004：JDK 21，apps/server 下 ./mvnw test，全量 189 项（初次该轮 1 失败、1 跳过），日志 /tmp/work046-tests-final.log。唯一失败为节点注册测试写死旧的公共本地控制口令；改用独立 test Profile 控制口令后，./mvnw -pl judging-service -am -Dtest=JudgeNodeRegistryIntegrationTests -Dsurefire.failIfNoSpecifiedTests=false test 通过，日志 /tmp/work046-judge-retest.log。合计 188 项通过、1 项真实 Linux 测试因未启用而跳过。测试资源默认 test，所有 SpringBootTest 已有或新增非 local Profile，五个新配置测试确认 non-local 不加载私有文件且环境变量可覆盖端口。首次探索性回归还发现旧默认口令断言及空 Map 绑定问题，已按新语义修复后纳入完整回归。
- AC-005：./mvnw clean package -DskipTests 成功，日志 /tmp/work046-package.log。python3 apps/server/scripts/check-config-artifacts.py 检查五个可执行 JAR 和五个原始 JAR，共 10 个，均包含基础配置且无 application-local.*。开发树中私有文件真实存在，因此不是空目录假通过。使用通用 Dockerfile 构建 gateway 代表镜像 cherry-oj-gateway:work046-check 成功，日志 /tmp/work046-image.log；创建未启动容器、提取其中 JAR 并核对与已审计文件逐字一致，无私有配置，然后删除临时容器。其他四个服务镜像未单独构建。
- AC-006：五个唯一共享 IDE 配置，无环境或配置用途 VM/Program 参数；原 .properties 已备份并移除，无遗留自定义 import。judging dev fixture 移到测试资源，防止部署 dev 隐式预置虚构节点。git diff --check 通过。未提交推送、未重启用户进程。

## 未通过项

已补验 problem/submission 的真实 IDEA 启动，但尚未验证迁移后本地五服务完整启动及浏览器自定义运行闭环。Compose 的真实容器栈依赖隔离数据库、密钥目录和 Go 节点，本轮没有启动，静态配置与代表镜像检查不能替代完整部署验收。

## 本地启动回归补验（2026-09-08）

- 用户 19:09 的两份失败日志显示 problem/submission 仍携带旧 additional-location 参数，引用已经迁走的 .properties。此前 AC-006 仅检查磁盘文件，未覆盖 IDEA 内存中的运行项，清理结论不完整。
- 通过 IDEA Run → Edit Configurations 直接确认并清除两项旧 VM 参数，保存后重新打开确认无残留；Judging 当前运行项也没有旧参数。未恢复过渡 properties 或引入新的启动参数。
- 从 IDEA 分别启动 ProblemServiceApplication（19:41:31，PID 50273，8082）与 SubmissionServiceApplication（19:43:36，PID 54353，8083），日志均确认默认 local、Started 与 Tomcat 监听成功。
- 对 127.0.0.1:8082/8083 的 /actuator/health/readiness 实际 GET 均返回 HTTP 200，顶层、readinessState、identityVerifierHealth 均 UP。未停止其他服务，未改数据库密码或业务实现。
- 保存后扫描 .run 与 .idea/workspace.xml，未发现 additional-location / VM_PARAMETERS。操作说明增加 IDE 内存状态迁移注意事项。此次只修复本地启动项，不重复运行与其无关的全量测试；完整功能验收仍保留。

## 范围检查

仅修改 TASK-091 允许的后端配置、资源打包、相关测试、IDE、部署样例、忽略规则和文档；没有修改公开契约、Web/Go 实现、密码、账号权限或数据库。新测试针对配置安全与环境隔离。

## 遗留问题

WORK-044/045 的真实运行验收仍未完成。本工作不以配置探针代替功能验收，也不改变已暂缓的 sandbox 内存统计问题。

## 剩余风险

现有服务需重新编译启动才能使用新配置。首次填写示例必须提供实际密码/凭据及有效密钥路径；容器依赖与挂载目录需按部署环境准备。完整容器运行、所有实例 readiness 及真实功能 smoke 待验收。

## 结论

partial：五服务配置重构、188 项后端测试、迁移等值、四库连接、10 个 JAR 排除和代表镜像核验完成。真实本地/容器全链路尚未确认，不代签验收。

## 变更记录

- 2026-09-08：状态变更：draft → review。原因：实际测试结果和未执行的全链路验收已写明
- 2026-09-09：验收闸通过：review → approved。原因：确认后端配置统一完成，私有配置不进入 Git

## 最终联调证据补齐

2026-09-09：后续 WORK-047 已完成真实配置加载、后台运行恢复与正式提交 AC（6/6），详见 VERIFY-048。用户自行在 IDEA 重启后确认功能完成，并已签署本工作验收闸。上文 partial 与未重启描述保留为历史排查记录，本工作保留签署时的 partial 元数据，最终端到端 pass 结论归 WORK-047；以 application-local.yaml 和现有共享启动入口作为最终配置方式。
