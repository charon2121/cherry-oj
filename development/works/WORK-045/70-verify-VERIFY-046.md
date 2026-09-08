---
id: "VERIFY-046"
type: "verify"
title: "统一本地judging服务启动入口"
status: "approved"
work: "WORK-045"
owners: ["codex/root"]
depends_on: ["TASK-090"]
related: []
implements: []
verifies: ["CHANGE-011#AC-001", "CHANGE-011#AC-002", "CHANGE-011#AC-003", "CHANGE-011#AC-004", "TASK-090"]
tags: []
result: "partial"
created_at: "2026-09-08"
updated_at: "2026-09-09"
---

# VERIFY-046：统一本地judging服务启动入口

## 验证对象

共享 judging 启动入口、私有配置迁移及 Spring 加载。

## 对应要求

CHANGE-011#AC-001 至 AC-004。

## 检查与结果

- AC-001：XML 解析通过；.run、.idea/runConfigurations 与 workspace 合计仅有一个 judging 入口。工作目录 `$PROJECT_DIR$`，配置路径 `file:./.local/judging-service.properties`。IDE 可视状态仍需刷新后确认。
- AC-002：使用当前 Java 21 和 judging 依赖执行 `/tmp/Work045ConfigProbe.java`（运行器 `/tmp/work045-probe.py`），通过真实 `ConfigDataEnvironmentPostProcessor.applyTo` 加载。五个 properties 原始值与原 XML 相等，五个对应 Spring 业务属性也与原值相等，原 application.yaml 的 server.port=8084 保留。仅输出断言结果，没有输出凭据。
- 缺失文件场景抛出 ConfigDataResourceNotFoundException，验证通过。初版探针错用 cherry.formal-judging 前缀，以及只捕获 LocationNotFoundException，导致探针断言失败；按已有 YAML 的 cherry.formal 前缀及实际异常修正后两场景退出码均为 0，未改业务配置来迎合测试。
- 静态 Python 检查：唯一入口、workspace 除目标配置块外逐字不变、每个私有文件均被 git check-ignore 忽略且权限为 600；编辑的共享配置、README 与 .gitignore 不包含原凭据。通过。
- AC-004：原三个 XML 备份位于 `.local/backups/WORK-045-20260908-145039/`；README 记录恢复位置。没有修改业务代码。备份恢复操作本轮未实际执行，以免撤回已完成迁移。
- `git diff --check` 通过；开发文档校验结果见最终检查。

## 未通过项

AC-003 的真实服务启动与内部运行请求尚未执行。8084 已由用户的 Java 进程占用，本次未中断该进程，也未启动第二个会消费正式队列的实例。

## 范围检查

只修改 TASK-090 指定启动配置、私有目录、忽略规则、README 和开发文档。保留 WORK-044 及其他已有业务改动，无提交或推送。

## 遗留问题

用户刷新 IDE 后停止旧 judging 实例，使用 JudgingServiceApplication 启动，再验证自定义运行；WORK-044 验收单独保留。

## 剩余风险

IDE 可能缓存旧运行项，需刷新或重开项目；正在运行的进程不会自动载入新配置。缺本地配置会明确启动失败。

## 结论

partial：实现与配置加载检查完成，真实重启后的内部调用及人工验收待完成，不宣称端到端通过。

## 变更记录

- 2026-09-08：状态变更：draft → review。原因：实现和配置检查证据已记录，真实重启验证待用户完成
- 2026-09-09：验收闸通过：review → approved。原因：确认 IDEA 服务启动入口统一完成

## 2026-09-08 submission 数据库启动修复

用户明确授权修改本机 cherry_oj_submission 密码并写入配置。只读确认账号为
cherry_oj_submission@localhost。最初尝试复用所提供密码被 MySQL 1819 密码策略拒绝，
未降低策略；随后生成独立强密码，ALTER USER 成功，并写入被忽略、权限 600 的
.local/submission-service.properties。修改前后 SHOW GRANTS 完全一致。

- 使用该账号通过 127.0.0.1 TCP 登录、切换 cherry_oj_submission 数据库并读取表数量成功。
- 使用用户启动日志中的 Java 21 与 submission 原依赖，执行临时 Work045SubmissionProbe：
  读取共享启动项的 JVM 参数，通过真实 Spring ConfigDataEnvironmentPostProcessor 加载，
  将解析出的 spring.datasource 属性交给 JDBC 并执行 SELECT 1，验证通过，退出码 0。
- 唯一共享 SubmissionServiceApplication 入口已建立，workspace 对应重复项已移除并私有备份。
  私有配置被 git check-ignore 忽略，共享编辑文件不包含新密码。
- 当前只配置数据库三项参数。未启动完整服务，未迁移数据库，未补齐 submission 其他服务凭据，
  不宣称自定义运行全链路已经通过。未 commit/push。原密码已更新，不能仅恢复文件来撤回密码修改。

## 17:11 自定义运行首次失败的后续修复

首次 req_443448bd20974146870201d88399a924 在 submission 59 ms 返回 503，未到 judging；
后续三次 429 发生在 65 秒租约内，不是十次额度耗尽。submission 私有配置仅有数据库三项，
运行进程也缺少 problem/judging 发送凭据。已恢复三条配套鉴权，保留现有 judging 凭据，
生成缺失的 problem 配对凭据；新增 ProblemServiceApplication 共享入口及私有配置。

实际 Spring ConfigDataEnvironmentPostProcessor 验证（三服务分别使用其 target/classes）：
Work045LinksProbe 的 submission、problem、judging 三项解析与双方匹配断言全部通过。
problem 凭据使用 canonical 属性名，因为原 YAML 没有对应 CHERRY 占位符。

但运行中 judging 的无效空请求鉴权探测返回 401，尚未通过。只读检查进程启动参数证实：
8082 problem 和 8084 judging 没有 additional-location 参数，8083 submission 有；
磁盘上三个共享配置均正确且无重复项，说明运行实例尚未加载共享配置。未中断用户进程。
用户需关闭重开 IDEA 项目以刷新缓存，并用三个共享配置重启。端到端结果仍未验证，result 保持 partial。

## 17:18 重启后再次失败的明确证据与修复

req_2a3abae1e84942009fb52aa41ce47318，trace d6117ebbc25143f5cc8377f268d02622：
judging 鉴权 401 → submission 503 → Gateway 503。只读检查三个 PID：problem/submission
有 additional-location，judging 没有任何 spring.config 参数，工作目录确为 apps/server。
共享 XML 正确且没有重复项，因此不能再将磁盘配置正确等同于实际启动正确。

已增加 judging application.yaml optional 本地文件导入，解除直接运行主类对 IDE JVM 参数的依赖。
用最新源码 resources 置于 classpath 首位，真实 Spring ConfigData 加载检查：
无 additional-location、有 additional-location 两种场景，judging 接收/发送凭据与 submission
配套值完全匹配；无本地文件场景沿用原有空凭据、formal.enabled=false。三项均通过。
临时探针为 /tmp/Work045LinksProbe.java 和 /tmp/Work045AbsentProbe.java；仅输出布尔断言。

git diff --check 通过。现有 judging 进程尚未重启，因此不宣称真实鉴权或完整运行通过；
用户只需重新构建并重启 judging，不需要再次修改密码、服务凭据或其他服务。result 仍 partial。

## 最终联调证据补齐

2026-09-09：后续 WORK-047 已完成真实配置加载、后台运行恢复与正式提交 AC（6/6），详见 VERIFY-048。用户自行在 IDEA 重启后确认功能完成，并已签署本工作验收闸。上文 partial 与未重启描述保留为历史排查记录，本工作保留签署时的 partial 元数据，最终端到端 pass 结论归 WORK-047；以 application-local.yaml 和现有共享启动入口作为最终配置方式。
