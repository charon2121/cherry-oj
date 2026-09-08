---
id: "DESIGN-041"
type: "design"
title: "恢复自定义运行并移除运行冷却"
status: "checked"
work: "WORK-047"
owners: ["codex/root"]
depends_on: ["ISSUE-014"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-08"
updated_at: "2026-09-08"
---

# DESIGN-041：恢复自定义运行并移除运行冷却

## 背景

依据 ISSUE-014；服务启动成功与题目执行就绪是两个不同条件。

## 目标与限制

恢复真实运行，移除点击间隔/频率限制。保留单次执行预算及服务执行槽位，不扩大 sandbox 范围。

## 整体方案

1. 核对当前题目版本、测试数据摘要、有效标定与节点 session；通过现有管理部署 API 安装当前题目数据，确认真实部署回执，不直接 UPDATE 数据库。
2. Gateway 移除 rate 有序集合及 60 秒 10 次算法；保留原子 active 租约，仅保护实际在途请求。
3. 使用覆盖完成、错误、取消的资源生命周期清理，按 owner token 删除自己的租约。TTL 仅为崩溃兜底，不作为失败后的冷却。Judging 单槽位继续保护未结束的执行。
4. 下游失败日志记录阶段、HTTP 状态和安全错误码，供排障；不包含请求/响应正文或秘密。

## 模块与数据

Gateway 自定义运行准入及相关测试；Submission/Judging 仅必要的安全诊断日志。恢复部署使用现有业务 API 及现有归档，不修改数据库结构/账号，不伪造回执。若缺失归档或标定失效，报告具体前置条件，不能自动放宽检查。

## 接口与状态

公开请求与结果结构不变；RUN_BUSY 仅表示实际请求在途或执行槽位繁忙，不再表示按分钟次数超限。

## 安全与失败

身份、CSRF、内部凭据及资源上限保持。异常结束允许再次发起请求，但若执行线程尚未退出，Judging 仍可返回真实繁忙。旧 rate 键不再读取并自然过期，无需清空 Redis。

## 监控与部署

修改后重新编译相关服务，检查实际进程并用真实请求确认输出；不只跑配置探针。不自动重试用户 POST。

## 迁移与兼容

这是 WORK-044 限流语义的显式调整。仅恢复当前题目数据，不扩建节点自动部署系统。

## 备选方案

直接标记数据库 ready 会伪造安装事实，拒绝采用；仅删除频率计数仍会留下 65 秒失败等待，也不足以满足目标。

## 风险与重审条件

高频运行会增加资源竞争，先保留现有执行槽位。若需更改 Go、正式提交语义或自动部署所有题目，另行定义范围。

## 变更记录

- 2026-09-08：结构与内容校验通过，由工具置为 checked。

## 实施中发现的范围补充（用户已确认）

后端 ProblemPublicationService.deploy 已支持对 ACTIVE 题目的已发布版本重新部署当前绑定数据，且检查 rowVersion、数据 ID 和摘要。但 Web 工作台将部署按钮与草稿编辑权限绑定，已发布题目无法通过现有 UI 恢复。本次拟仅允许 ACTIVE 题目的 PUBLISHED 版本重新部署当前绑定 READY 数据；上传、绑定、题面编辑和校准权限保持不变。新增写路径仅工作台组件及对应回归测试，不改布局或接口。用户后续明确回复“允许”，本补充已获授权；TASK 已增加精确写路径，其余 Web 目录仍不扩展。

## 原始归档缺失后的恢复（用户已授权）

用户要求“随便生成一份文件，按照规则来”，授权生成符合题意的新数据以恢复运行。采用现有后台新修订流程：生成 A+B 成对 .in/.out ZIP，复制当前题目为新修订、上传并绑定新数据、部署、使用参考程序校准、发布新修订并验证运行。原已发布版本的数据 ID/摘要不变；不伪造原 ZIP、不直接改数据库。使用 /tmp/work047-recovery 保存生成文件及核验信息。

## 运行镜像兼容核验

真实 Judge 返回 RAN 和正确 stdout，但当前旧镜像缺失新接口 stderr，Java 严格校验将其映射 503。恢复步骤追加：仅从已实现的当前 Go 源码重建并更新现有 judge 容器，不修改 Go/sandbox 源码或沙箱容器；保留现有环境变量及数据卷，节点新 session 后通过现有按钮重新部署同一数据。不降低契约校验。

## 镜像更新失败与回退

更新后因可执行文件摘要变化触发 NODE_IDENTITY_CONFLICT。现有注册逻辑要求新 nodeId，且第二个环境只能 REGISTERED；没有环境切换管理 API。已回退原镜像和标签，保留生成的 v2 数据。当前范围不能直接改数据库状态、伪造指纹或复制旧校准。

待确认的后续方案：增加受控的环境切换操作入口，校验已注册环境与在线节点、串行切换并记录审计；新环境使用新 nodeId 和独立数据卷，重新部署、校准并发布新修订。需要先定义环境管理边界，不能在本次恢复中临时写 SQL 替代。

## 环境切换入口实施细化（用户回复“继续”，已授权）

采用独立运维 CLI，复用 judging-service 的 application.yaml/application-local.yaml 或 Compose 环境变量；不启动 Web、Kafka worker 或迁移，不增加服务启动参数要求，不开放新 HTTP 权限面。操作凭据仍为运维已持有的 judging 数据库凭据。

命令支持 list 与 switch：list 展示环境 ID、状态、rowVersion、在线节点数；switch 必须提供原 ACTIVE ID/rowVersion、目标 ID/rowVersion 和原因。与节点注册共享注册锁，并锁定两条环境记录；拒绝缺失/离线/无启用语言/版本冲突的目标，整笔切换与审计在一个事务内提交。旧环境 RETIRED，目标 ACTIVE；回退也走同一命令，必须有旧节点在线。不伪造回执、指纹、标定，不自动修改题目发布。切换至未校准环境期间题目不可运行，随后通过现有后台新修订部署/校准/发布恢复。

精确新增范围：judging-service 的独立 operations 包、该包集成测试、POM 指定已有服务主类（避免新增 CLI 主类干扰打包），apps/server/scripts/judge-environment 运维包装命令及本工作文档。原 Go/契约/身份模块禁改保持。测试使用临时 MySQL，覆盖原子切换、失败回滚、并发过期请求、离线拒绝、审计与历史校准保留。运行升级使用新 nodeId 与新 named volume，保存原镜像和原卷供回退。

运行配置落盘：核对现有 Judge 环境变量与 compose.yaml 默认值一致，因此只需在仓库根已被 Git 忽略的 .env 保存 JUDGE_NODE_ID 与 JUDGE_TESTDATA_VOLUME；不再依赖 /tmp 覆盖文件，普通 docker compose 可重复启动。新增本地写路径 .env，仅保存本次新身份和卷名。

## 运维命令用法

在 apps/server 执行 `./mvnw -pl judging-service -am package -DskipTests` 构建，然后从任意目录调用 `apps/server/scripts/judge-environment list`（路径相对于仓库根）。脚本只解包依赖到临时目录并启动独立命令，自动清理；读取项目内 judging 配置，不创建 Spring 应用上下文。使用 Java 21，JAVA_HOME 若已设置则遵循它。

`apps/server/scripts/judge-environment switch CURRENT_ID CURRENT_VERSION TARGET_ID TARGET_VERSION '切换原因'`。ID 与版本取自刚查询的 list 输出；冲突时重新查询后再决定，命令不会自动重试写入。部署/标定不会复制或自动置为就绪。运维身份来自执行者持有的 judging 数据库权限，审计标记 source=operator-cli、原/目标 ID 与版本及原因，不伪装为网站用户。

本次运行配置为根目录私有 .env：JUDGE_NODE_ID=judge-local-2、JUDGE_TESTDATA_VOLUME=cherry-oj-engine_judge-testdata-v2。旧镜像 0969dea11ce0… 与旧卷 cherry-oj-engine_judge-testdata 保留。回退时恢复旧镜像标签、旧 nodeId 和旧卷，等待注册后用 list/switch 切回旧环境；旧节点新 session 仍需正常重新部署。旧题目版本校准不自动用于新版本。

## 正式提交验收配置恢复（用户已授权一条验证提交）

正式提交首次被 SUBMISSIONS_PAUSED 拒绝，尚未受理。本地 submission 配置 accepting/messaging-enabled 均 false；Kafka 在线，judging 已启用正式判题。只读核对历史 submission 全部 DONE（2 条），outbox 全部已发送（2 条）。为完成授权验收，仅将 submission 私有 application-local.yaml 的这两个开关设为 true，重建资源并仅重启 submission；沿用浏览器同一次提交的幂等标识，不新增多条记录。精确增加该私有配置写路径，数据库凭据与其他配置不变。
