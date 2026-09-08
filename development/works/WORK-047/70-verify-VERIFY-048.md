---
id: "VERIFY-048"
type: "verify"
title: "恢复自定义运行并移除运行冷却"
status: "approved"
work: "WORK-047"
owners: ["codex/root"]
depends_on: ["TASK-092"]
related: []
implements: []
verifies: ["ISSUE-014#AC-001", "ISSUE-014#AC-002", "ISSUE-014#AC-003", "ISSUE-014#AC-004", "TASK-092"]
tags: []
result: "pass"
created_at: "2026-09-08"
updated_at: "2026-09-09"
---

# VERIFY-048：恢复自定义运行并移除运行冷却

## 验证对象

真实运行就绪与取消点击冷却，依据 ISSUE-014#AC-001 至 AC-004。

## 对应要求

AC-001 至 AC-004；实施后逐项记录。

## 检查与结果

2026-09-08 只读检查：五服务 /actuator/health/readiness 均 HTTP 200 UP；当前唯一公开题目 a-plus-b-II；通过本地配置凭据调用快照 HTTP 200，执行配置 HTTP 503 JUDGING_NOT_READY。JDBC SELECT 确认节点在线、两条部署回执 available=0。临时探针 /tmp/RunReadinessProbe.java 与 /tmp/run-readiness-probe.py，仅 SELECT 和无副作用的查询接口，未输出秘密。

## 未通过项

无本次验收未通过项；用户已明确授权一条正式验证提交，最终 AC，6/6 通过。

## 范围检查

修改 TASK-092 内 Gateway 准入及测试、Submission/Judging 自定义运行诊断；后续经用户明确允许，仅补修 Web 部署按钮及对应测试；没有修改 Go、数据库或凭据。重启原 Gateway/Submission/Judging 服务加载新实现，无配置参数注入。

## 遗留问题

环境切换入口已补齐并实测成功。正式提交与自定义运行均已验证；基础 sandbox 硬化继续按既有决定暂缓。

## 剩余风险

已知 sandbox 内存统计限制仍按用户决定暂缓。

## 结论

pass：自定义运行、冷却移除、部署恢复、环境升级与正式提交回归全部完成，等待人工验收闸；未提交推送。

## 实施验证补充

- JDK 21：`./mvnw -pl gateway-service,submission-service,judging-service -am -Dtest='CustomRun*Tests,Trial*Tests' -Dsurefire.failIfNoSpecifiedTests=false test` 全部 11 项通过，日志 `/tmp/work047-tests-final.log`。使用独立 Testcontainers Redis；首次受沙箱 Docker 访问限制失败，之后按权限执行；新测试泛型编译问题已修复。
- 覆盖 503 后立即重试、12 次连续成功请求、跨控制器在途拒绝、400/503/504 清理、取消前后 Redis 回包竞态、owner 更替不误删、CSRF、版本快照与超时。代码执行期限未移除。
- 仅格式化本次四个 Java 文件后 compile 成功，日志 `/tmp/work047-compile.log`；git diff --check 通过。
- 重启后的 Gateway PID 44201、Submission PID 44242、Judging PID 44250，8080/8083/8084 readiness 均 HTTP 200 UP，使用主类/classpath，无配置用途命令参数。
- 真实 admin 浏览器中 23:42:40 和 23:43:36 两次点击运行：均到达 Judging 的执行就绪检查，没有第二次 RUN_BUSY。三层新增日志分别定位 submission HTTP503、judging HTTP503、execution JUDGING_NOT_READY；不记录程序正文或凭据。
- 已确认后台部署按钮在 PUBLISHED 状态被 canUseTestActions 禁用，而 ProblemPublicationService.deploy 本身支持同一绑定数据重新部署。DESIGN-041 已记录最小范围补充，未获用户确认前不改 Web；没有创建新题目修订或手改数据库绕过。

## Web 范围补充实施

- 用户明确回复“允许”，先更新 DESIGN-041 与 TASK-092 精确边界，再修改工作台。仅部署准入条件改为 ACTIVE 题目的草稿/可审阅/已发布版本，仍要求无未保存修改、绑定原 READY 数据及节点可用；其他编辑权限未扩大。
- `npm run build` 通过（包括 TypeScript），日志 `/tmp/work047-web-build.log`；针对组件及新增测试的 ESLint 通过；`npx playwright test e2e/published-deployment.spec.ts` 两项通过，日志 `/tmp/work047-web-tests.log`。覆盖 PUBLISHED 键盘部署、请求锁定数据 ID/摘要/rowVersion、ARCHIVED 禁用、上传/绑定/校准/保存继续只读。
- 实际浏览器热更新后部署按钮可用，点击返回 503（req_a7927cbf26fe445e83f21999bac4686d），链路日志显示失败停在 Problem、未到达 Judging。只读数据库核对：当前数据 01a079c1-c530-7962-a5ad-f09adb0a0260 的 storage_ref 为 assets/01a079c1-c530-7962-a5ad-f09adb0a0260.zip，SHA-256 为 a6d629ddf74660ed60ad799a0e3f5bc64c838a4a693464dc7d901de07710f961。
- 本地配置 root 为 ./data/problem-testdata，当前工作目录 apps/server；对应归档不存在。仓库归档目录仅有另一份资产；相关下载 ZIP 摘要不匹配。未创建替代资产、未修改已发布绑定或数据库摘要。已请求用户提供原归档/备份路径；目前不能声称真实 C++ 运行恢复。

## 设计自检八问

1. 新增框为 0，仅更改现有按钮 disabled 条件。
2. 没有修改行、列或对齐规则。
3. 没有新增文字色，复用现有按钮可用/禁用状态。
4. 保留现有长任务工作台及其模板，不新增页面。
5. 新增 var(--ds-*) 为 0，未增加 alias。
6. 没有新增有序量或饱和色，状态继续有文字说明。
7. 本轮验证键盘 Enter、可用/禁用状态和真实页面；未重复双主题/320px/200%/长中文/forced-colors/reduced-motion 完整视觉矩阵，布局和样式未改。
8. 未修改行高、边距、字号、图标、gap 或描边，未引入新的设计数值。

## 新归档与真实执行核验（2026-09-09）

- 用户明确授权生成符合规则的文件。生成 `/tmp/work047-recovery/a-plus-b.zip`，包含 6 对常规文件 .in/.out，覆盖零、正负数和十亿整数；展开总计 80 bytes，SHA-256 `17c63562178a77205e3e3d18f4a1eb81eabc0be922a6339dca8dd8cd7570dcf8`。最初 ZIP 文件类型位未标记常规文件，被规则正确拒绝；修正后上传通过，保留失败记录。
- 经后台正常新修订/上传绑定/部署/参考程序校准/发布，当前公开版本为 v2 `01a081c0-833b-7c15-b787-68181026bd11`；新数据 `01a081c5-1a7b-7bac-8320-e35047f40be6`。6 个测试点校准通过，1 秒 CPU、256 MiB 内存。v1 不变，无直接数据库写入。
- 对真实旧 Judge 的生成程序探针：snapshot 200、execution-profile 200、judge 200，verdict RAN，stdout 为 `42\n`，但 caseResults 缺少 stderr，导致 Java 按契约拒绝。浏览器编译失败结果可以展示；正确程序仍报服务不可用。
- 当前 Go 源码构建成功，新镜像 SHA `15ce775bce28d1e6f97b4f80ef05aad66c45fdce752e4dbb36a0edf4a5038e6c`；仅重建 Judge 容器，sandbox 未动。升级改变二进制摘要，触发 NODE_IDENTITY_CONFLICT。注册代码只自动激活第一个环境，后续环境只有 REGISTERED；仓库没有切换 API。WORK-040 曾在隔离测试中使用显式数据库事务切换，并非当前可用管理入口。
- 回退至原镜像 SHA `0969dea11ce0bf796ad8f7fae05447245e5c4a1b65ce81cd66fcdca2e6f58852` 并恢复 cherry-oj-judge:local 标签；只读查询证实 judge-local-1 再次在线，原环境 ACTIVE。未绕过身份或复制校准。

- 回退后通过后台再次部署 v2 数据成功；页面显示 judge-local-1 部署可用。最终只读核验：snapshot/execution-profile 均 HTTP 200、在线节点 1 个、当前有效回执 1 条。缺失 stderr 的旧镜像兼容问题仍在，未宣称真实运行恢复。

## 环境切换与最终真实运行（2026-09-09）

- 用户回复“继续”授权补充方案；先扩展 DESIGN-041/TASK-092 后实施。新增独立 EnvironmentCommand/EnvironmentOperations 和 scripts/judge-environment，不启动 Web/Kafka/Flyway，不修改服务的启动参数或对外契约。POM 明确原服务主类，普通 JAR 启动目标不变。
- `./mvnw -pl judging-service -am -Dtest=EnvironmentOperationsTests -Dsurefire.failIfNoSpecifiedTests=false package` 4 项真实 MySQL 测试全部通过，日志 `/tmp/work047-environment-tests.log`。覆盖事务切换/回退、原回执保留且不复制、过期版本/ABA/离线/语言禁用拒绝、并发只接受一个请求、审计失败整笔回滚。首次触发器夹具因 MySQL 权限失败，改用临时测试库 CHECK 约束模拟失败后通过；未改本地 MySQL 权限。
- 命令 list 使用本地既有配置运行成功；switch 明确指定旧环境 `01a0771e-4c5b-73dd-858a-8bab46c3810e`、旧版本 0、新环境 `01a081e0-bd32-779e-b42f-ef0ba5fe2ce3`、新版本 0 和 WORK-047 原因，成功将旧环境 RETIRED/新环境 ACTIVE，双方 rowVersion=1，审计与切换同事务提交。
- 新 Judge `judge-local-2` 自行注册在线，镜像为此前已构建的新实现，独立卷 `cherry-oj-engine_judge-testdata-v2`。根目录已忽略的 .env 保存 nodeId/卷名，无临时启动参数依赖。sandbox 未改；旧镜像和旧卷保留。
- 后台新修订 v3 `01a081e2-1541-70f9-917a-c24ca4133ef9` 复用已生成的 6 组数据，正常部署到新节点、参考程序校准 VALID、发布检查全部通过后发布。没有复制旧环境标定或伪造部署。
- 00:42:14 真实页面运行 C++ 输入 40 2：运行完成，stdout 42、stderr debug；00:42:51 再运行成功，无 RUN_BUSY。Gateway 12 次连续成功和 503 后立即重试由前述 Redis 集成测试覆盖。
- 00:43:11 真实死循环返回“超过时间限制”，HTTP 200，执行耗时约 10.128 秒（墙钟预算 10 秒）；本项证明墙钟终止仍有效，不表示当前基础 sandbox 已具备精确的 1 秒 CPU 强制隔离。Go/sandbox 硬化继续按既有范围暂缓。
- 00:43:48 恢复正确程序后再次运行成功，stdout 42、空 stderr，不残留运行占用。最后只读探针 snapshot/execution-profile 均 HTTP 200，新节点 ONLINE、环境 ACTIVE、VALID 校准总计 3 条；旧事实保留。
- 正式提交按钮被自动审批拒绝，理由为创建持久化提交记录未明确授权；未使用其他通道绕过，已向用户请求一次授权。
- `sh -n apps/server/scripts/judge-environment`、`git diff --check` 与 `scripts/work check` 通过（既有 WORK-033 提示保持）。本次无提交推送、无人工闸代签。

## 正式提交授权与最终结果

- 用户明确回复“允许创建一条验证提交”。首次请求 req_510b0fcaee184267a02dd860dd0ea9ce 被 SUBMISSIONS_PAUSED 拒绝，未创建记录。只读确认历史两条提交均 DONE，outbox 两条均已发送；本地 submission 的 accepting/messaging-enabled 为 false，Kafka 在线且 judging 正式判题已启用。
- 先在 DESIGN-041/TASK-092 登记私有配置边界，再将 submission application-local.yaml 两开关置为 true；compile 成功（/tmp/work047-submission-enable.log），仅重启 submission，PID 81541，readiness UP；仍无配置用途启动参数。
- 浏览器点击“用原代码重试同一次提交”，沿用原幂等请求。新增提交 `01a081ed-e4ce-7d36-952d-1eb3601cc7bb`，v3，最终 AC，6/6 通过，已执行 6，CPU 1.762 ms、内存 17424 KiB。仅创建这一条验证提交。
- 实现复核：环境切换复用注册锁及事务，旧/新环境版本冲突防护覆盖 ABA；审计失败回滚，无指纹/回执/标定伪造。运维命令不启动 Web、worker 或迁移，普通服务打包主类固定。原任务 Gateway 租约清理保持 owner 检查，Web 只扩展部署准入。此次仅增加私有提交配置开关；未修改正式提交业务代码或 Go/sandbox。人工验收仍由用户签署。

## 变更记录

- 2026-09-09：验收闸通过：review → approved。原因：确认自定义运行恢复、取消冷却、环境切换及正式提交 AC 验证通过
