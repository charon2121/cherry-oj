---
id: "VERIFY-002"
type: "verify"
title: "交付 C++ ACM 答题闭环"
status: "approved"
work: "WORK-002"
owners: ["product/owner"]
depends_on: ["TASK-002", "TASK-076", "TASK-077", "TASK-078", "TASK-079", "TASK-080"]
related: []
implements: []
verifies: ["FEATURE-001#REQ-001", "FEATURE-001#REQ-002", "FEATURE-001#REQ-003", "FEATURE-001#REQ-004", "FEATURE-001#REQ-005", "FEATURE-001#REQ-006", "FEATURE-001#REQ-007", "FEATURE-001#REQ-008", "FEATURE-001#REQ-009", "FEATURE-001#REQ-010", "FEATURE-001#REQ-011", "FEATURE-001#REQ-012", "FEATURE-001#REQ-013", "FEATURE-001#REQ-014", "FEATURE-001#AC-001", "FEATURE-001#AC-002", "FEATURE-001#AC-003", "FEATURE-001#AC-004", "FEATURE-001#AC-005", "FEATURE-001#AC-006", "FEATURE-001#AC-007", "FEATURE-001#AC-008", "FEATURE-001#AC-009", "FEATURE-001#AC-010", "TASK-002", "TASK-076", "TASK-077", "TASK-078", "TASK-079", "TASK-080"]
tags: []
result: "pass"
created_at: "2026-08-24"
updated_at: "2026-09-07"
---

# VERIFY-002：正式提交闭环验证计划与证据

## 验证对象

FEATURE-001 本轮十项 AC 和六个任务。执行已签署意图闸的六任务方案，以下按实际命令记录证据；不代签人工验收。

## 对应要求

| 验收 | 负责任务 | 必须取得的证据 |
|---|---|---|
| AC-001 | TASK-076、TASK-080 | 既有发布/校准/节点成功与拒绝场景 |
| AC-002 | TASK-079、TASK-080 | 运行未开放、草稿与编辑器回归 |
| AC-003 | TASK-077、TASK-078、TASK-079、TASK-080 | 浏览器至真实 Judge 的 AC、唯一编号、链接/刷新恢复 |
| AC-004 | TASK-002、TASK-078、TASK-079、TASK-080 | 真实 CE 与脱敏诊断 |
| AC-005 | TASK-002、TASK-077、TASK-078、TASK-080 | 真实 WA、打印隐藏输入探针无公开泄漏 |
| AC-006 | TASK-077、TASK-079、TASK-080 | 丢失创建响应、同键并发、查询失败后恢复 |
| AC-007 | TASK-002、TASK-076、TASK-077、TASK-079、TASK-080 | 会话/账号隔离、服务身份负例与 no-store |
| AC-008 | TASK-076、TASK-077、TASK-078、TASK-080 | 新题版/新校准/切环境后旧输入未变 |
| AC-009 | TASK-077、TASK-078、TASK-080 | 重投、乱序、旧租约、崩溃、Kafka 恢复与最终 SE |
| AC-010 | TASK-079、TASK-080 | 键盘/窄屏/读屏状态、独立复核与人工手测 |

## 检查与结果

- 2026-09-07，代码基线 `42c181d`：只读检查 submission-service、judging-service、problem-service、Gateway、
  既有 contracts、全局架构/数据模型和 WORK-041。确认正式提交链路尚未实现；管理端 readiness 及 schema 不等于业务 API。
- 2026-09-07：`scripts/work check` 通过，338 份开发文档有效；保留既有 WORK-033 状态推导提示，与本次无关。
- 2026-09-07：`python3 scripts/docs_test.py` 通过，407 份 Markdown 入口与本地链接有效；`git diff --check` 通过。
- 首次文档校验发现 FEATURE 缺少模板规定的段落标题，已补齐并重跑通过；没有以格式通过替代人工审批。
- 以下初始检查之后已完成业务验证，最终证据见后文；人工验收仍待负责人执行。

### 人工验收步骤

1. 在获准的验收环境登录普通用户，打开专用已发布 C++ ACM A+B；确认版本、节点、数据、VALID 校准就绪。
2. 输入正确完整程序并提交，等待真实 AC，记录编号；刷新、复制链接重开，编号不变。
3. 在已提交结果旁继续编辑，再明确提交语法错误与答案错误程序，分别确认 CE/WA；旧记录不变。
4. 提交后暂时断开浏览器网络，恢复后查询同一次结果；创建响应不确定时按“确认原请求”操作，不生成重复记录。
5. 退出登录、切换另一个普通账号尝试打开原结果链接，应不可见；运行按钮仍未开放，本地草稿不跨账号。
6. 键盘操作与窄屏查看均可用。节点掉线、消息重复等故障由隔离自动化执行，不让用户在现有环境手动停服务。

每步填写实际地址、账号角色（不写密码）、提交编号、预期/实际结果与时间。未出现可用环境时不虚构通过。

## 未通过项

本轮范围内没有遗留失败检查。初次全量 Java 回归因配置清单未登记新增的有意空值失败，补充明确分类后全量重跑通过。隔离脚本早期运行发现题目未设为 PUBLIC、晚创建账号触发既有 JWT 年龄限制、SQL 分隔缺少换行，均修正测试准备后重跑；失败轮次创建的资源已清理。

## 范围检查

实现按六个 TASK 的读写边界完成；新增路径及原因均先写入 PLAN/对应 TASK。现有运行数据 ZIP 保持不动，未部署现有环境。仅新增迁移，不改已执行迁移；API 生成漂移检查通过。user-service 仅调整聚合配置清单测试，未修改账号业务。

## 遗留问题

2026-09-07 负责人已确认 WA 汇总展示与验收环境边界，两个阻塞均解除；自定义运行、完整历史、统计是明确的后续范围，不列为本轮已完成。

## 剩余风险

新链路已经过隔离真实故障验证及只读独立复核。现有环境的部署、配置迁移与人工冒烟尚未执行，不能把隔离成功等同于已上线。发现既有 user-service 的 JwtIssuedAtValidator 将签发时间限制在 30 秒以内，与 token 有效期冲突；该账号服务业务修复超出本轮边界，已记录 MEMORY 后续处理。

## 结论

技术验证 result=pass；负责人完成隔离及本地开发环境手测，并已通过命令签署验收闸，VERIFY 为 approved。

## 实施阶段证据（持续补充）

- 2026-09-07：TASK-002 契约检查 11 项、身份支持 8 项通过；新增凭据隔离和轮换测试 3 项包含其中。
- 2026-09-07：TASK-076 problem/judging 模块回归与新增快照、路由鉴权 3 项通过，使用隔离 MySQL。
- 2026-09-07：TASK-077 submission 模块测试通过；真实 MySQL 4 项验证并发幂等、原子回滚、版本拒绝、终态消费；独立 Kafka 测试 1 项通过并正常关闭。此时完整链路尚未执行，后续最终验证已补齐，见下文。

### 2026-09-07：独立复核、Gateway 与工作台模块验证

- 只读独立审查者报告两项 Go 兼容问题：PE 白名单遗漏、原始大组 WA 响应上限过低。修复后
  `HttpJudgeGatewaySizeTests` 使用真实 HTTP 返回 300 点、超过 1 MiB 的响应，保持 WA；
  `FormalResultTests` 覆盖 PE 和安全投影。聚合定向 Maven 测试通过，日志
  `/private/tmp/work002-review-fixes.log`、`/private/tmp/work002-large-response.log`。
- 独立审查又发现跨版本未决恢复丢失及 CSRF 刷新后的账号切换竞态。恢复入口改为账号+题目，
  请求正文仍冻结版本；增加 `X-Expected-User-Id` 会话一致性前置条件，Gateway 不匹配即拒绝。
- `cd apps/server && ./mvnw -q -pl gateway-service -am test` 通过，含新增真实 HTTP 代理与预期账号检查。
  `/private/tmp/work002-gateway-tests.log`。内部短期 JWT 来自服务端会话，不接受浏览器提供的授权替代物。
- Node 24：`cd apps/web && npm run check` 通过：39 个测试文件、165 项测试；设计系统、API 生成漂移、
  格式、ESLint 和 TypeScript 均通过。`npm run build` 通过。
- `npm run test:e2e -- e2e/problem-workspace.spec.ts`：17 项 Chromium 测试通过，覆盖同次提交刷新、
  DONE 停止轮询、响应丢失后跨版本重试原请求、CSRF 刷新保持原账号条件，以及原工作台草稿、
  会话过期、触控、窄屏和 200% 等效视口回归。此批 API 使用 fixture，不能替代真实整链路证据。
- `python3 scripts/contracts_test.py` 11 项通过；`scripts/work check` 338 份通过，仅有既存 WORK-033 提示；
  `git diff --check` 通过。

真实整链路结果见后文；人工验收结论由负责人给出。

### 工作台设计自检八问

1. 新结果区没有独立外框或卡片；沿用编辑区底部一条分隔线，将编辑器与提交操作分开。
2. 结果标题、编号、汇总和说明共享左边缘；度量值使用同一行的紧凑分组，窄屏折行，不铺满列宽。
3. 判定用 foreground、说明用 fg-2、辅助文案用 fg-muted、编号用 fg-meta；fg-disabled 仅用于禁用按钮。
4. 沿用 `WorkbenchPageTemplate variant="coding"`，草稿状态栏始终属于编辑区；没有新增页面模板。
5. 新业务代码没有 `var(--ds-*)`，未增加设计 alias。协议 UUID 仅在恢复模块生成，已登记精确门禁例外，非 DOM id。
6. 测例数、CPU 和内存直接给数字及单位，判定给文字与 verdict；新饱和色只来自“提交”主按钮。
7. Chromium 覆盖双主题、320px、200% 等效 CSS 视口、键盘编辑与移焦、长中文、forced-colors 与 reduced-motion。
   新结果区截图：`/tmp/work002-visual/result-dark.png`、`result-light.png`、`result-narrow-forced.png`；已查看截图确认折行、对齐与可达性。
8. 对照 measurements 的控件/图标尺寸和间距档位，复用既有 Button sm、14/16px 图标及语义文字档；结果是汇总区，
   不套用列表 44px 行高。无新增控件三态描边，沿用既有 Button 合同及源码/主题对比门禁。


### 最终技术验证与独立复核

- Java 21：`cd apps/server && ./mvnw -q test` 退出 0。169 项通过，1 项条件 Linux 测试跳过，其真实场景由下述隔离栈覆盖。
  模块计数：problem 41、gateway 58、submission 7、judging 29 通过/1 跳过、user 26、identity support 8。
  日志 `/private/tmp/work002-java-final-2.log`。新增 completion 重投后 late-started 的真实 Kafka 测试单独重跑通过，
  `/private/tmp/work002-kafka-order-final.log`；保持终态 AC 与 rowVersion，事件去重且不倒退。
- 最新 Web 检查：`npm run check`（165 项）、`npm run build`、17 项工作台 Playwright 全部退出 0。
  日志 `/private/tmp/work002-web-final-check.log`、`/private/tmp/work002-web-final-build.log`、`/private/tmp/work002-web-e2e-final.log`。
  修复正常创建 pending 时提前查询请求键而显示 404 恢复提示；发送完成后才查询，不改变不确定响应恢复协议。
- 只读独立审查发现并确认修复：PE 兼容、较大 Judge 响应、跨版本恢复入口、CSRF 刷新账号竞态、
  隔离脚本宿主环境污染、当前校准证据配对与旧节点恢复等待。末轮仅复核上述末次差异，未发现新的阻塞问题。
- 安全验证覆盖内部调用凭据隔离/轮换、匿名和非本人访问、ADMIN 无越权读取、预期账号不符 409、
  隐藏输入回显探针、CE 路径/控制字符脱敏、Kafka lifecycle 白名单与死信不保留污染的 key/source。
- 数据验证使用真实 MySQL 测试并发同键唯一、写入失败整体回滚、旧版本拒绝、不可变输入与旧 attempt fencing；
  消息恢复同时使用真实 Kafka，未以数据库直接置终态替代消费处理。

### 真实 Linux 隔离整链路

运行 `python3 scripts/work-002-e2e.py --keep`，使用 MySQL 8.4、Redis 7、Kafka native 3.8、五个 Java 服务与本地 Judge/sandbox 镜像。
脚本以专属 Compose project、临时卷/目录及随机 loopback 端口隔离，只清理自己创建的资源；浏览器使用专属 localhost 子域，避免与现有登录 Cookie 混用。
额外注入不可达 `SPRING_DATASOURCE_URL`、伪 `SPRING_APPLICATION_JSON` 与 `JUDGE_BIND_ADDRESS=0.0.0.0` 验证环境白名单隔离。

完整成功轮次日志 `/private/tmp/work002-real-e2e-final-2.log`，耗时约 4 分钟；证据目录
`/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/cherry-work002-qo027_rj`。

| 场景 | 真实提交编号 | 实际结果 |
|---|---|---|
| 正确程序 | 01a07ac8-0dc5-7921-95b6-5642a1615a3d | AC |
| 错误答案 | 01a07ac8-19be-73ee-ba6e-0061ede66b8b | WA |
| 语法错误 | 01a07ac8-2086-7517-b53e-41f77653b801 | CE |
| 超时程序 | 01a07ac8-2330-7f44-a167-8bd0e9da7c9b | TLE |
| 隐藏输入回显 | 01a07ac8-4fbe-7952-8e65-da1959933dac | WA，公开结果无输入/输出/差异 |
| Kafka 中断后恢复 | 01a07ac8-5b41-7abb-bbb2-098d87260ebd | PENDING 后 AC |
| 节点不可用 | 01a07ac8-6ad8-7fc3-8829-d7bb0aef0a41 | 有界重试后 SE |
| RUNNING 进程强制退出 | 01a07ac8-b475-7d3c-a591-47954edb5015 | 租约到期后 attempt 2 接管，TLE |
| 换成另一就绪环境 | 01a07ac9-62a8-773a-a28f-c97b36031988 | 旧任务不借用新环境，最终 SE |
| 普通用户提交 | 01a07aca-9e43-7ca5-8f5c-59063e63e165 | AC，其他账号及 ADMIN 不可读取 |

- 题目 `01a07ac7-d323-7169-9e4b-4bda5a8a2617`，当前 v2 `01a07ac9-5b2d-752c-a0d5-c24d75b3b095`；
  当前 VALID 校准 `01a07ac9-5c0d-79af-adf1-88f43d39b87a`，旧基线校准 `01a07ac8-0703-7a7d-90f7-c063bd695b21`。
  该运行中旧证据字段误指基线，已用仅针对本轮 MySQL 的只读查询核正，并在 evidenceCorrection 保留说明；脚本也已修正。
- 环境指纹 `044a36e87983d35f99d4b0b25364ed3b4983d9eda8fb3db64967d173421ee199`；
  数据版本 `01a07ac7-d46b-74ca-b57f-fee3332d4ea0`；数据 ZIP SHA-256 `3cb2d97b914e9a6fbd590de75e85d230bd4ed34833ccf352804f31467a0f1084`。
  全部前置资源及结果保存在该目录的 `evidence.json`，未记录秘密或隐藏测试正文。镜像内容 ID 见交付运行记录。
- 发布 v2/新校准后旧 JudgeInput 保持相同；旧版本新请求返回 409。恢复旧环境及节点后重新受理成功。
- 回退演练：关闭新受理后，新键返回 503；查询与旧键重放仍可用；重新开放后普通用户真实提交 AC。没有删表或清空队列。
- 真实浏览器（普通用户）：AC `01a07acd-5d63-722c-9cb9-8babfe10023c`，刷新后编号、结果与草稿保留；
  CE `01a07ad0-6090-73de-8ef5-61d34e9c7392` 显示受限 `Main.cpp` 编译诊断，未出现沙箱绝对路径。
  实际查看截图与页面结构，确认运行仍关闭、版本 v2、代码可继续编辑且不改变已提交结果。
- 上述轮次资源已由 stop 文件正常清理。最新构建产物的交付运行地址与证据在下一节补充。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：真实整链路、模块回归、故障与回退验证通过；仅记录技术结论，等待人工验收闸


### 可直接粘贴的手测程序

正确程序，预期 AC；提交后刷新应保留原编号。将 `a + b` 改成 `a - b` 再提交，预期 WA，结果只展示汇总；
将全部代码替换为 `int main( {` 再提交，预期 CE，展示编译诊断。三次提交编号应不同，每次刷新对应编号不变。

```cpp
#include <iostream>
int main() {
    long long a, b;
    std::cin >> a >> b;
    std::cout << a + b << "
";
}
```

退出账号后重开带 submissionId 的链接应要求登录；其他账号不得看到该结果。
正式环境仍需另行授权部署，本轮页面地址只用于隔离验收。


### 最新构建的交付运行（保留供负责人验收）

`./mvnw -q -DskipTests package` 成功后，使用最新 Java jar 和已通过检查的 Web dist 再次运行上述完整脚本，所有场景再次 PASS。
日志 `/private/tmp/work002-delivery.log`；专属项目 `cherry-work002-9375cc65`，证据 `/var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/cherry-work002-w7ma1cqw/evidence.json`。

页面：<http://cherry-work002-9375cc65.localhost:52337/problems/work002-plus>，普通用户 `work002user`；临时测试密码通过交付消息提供，不写入仓库。

当前 v2 `01a07ad7-3fe7-7ef5-aef9-9216ff746652`，VALID 校准 `01a07ad7-40cc-71cc-a52a-234e17696334`，数据版本 `01a07ad5-c864-77e4-a0a0-77b37c6104f5`；
环境指纹与 ZIP SHA-256 同上轮。本轮证据自动区分当前及基线校准，不需手工核正。

| 场景顺序 | 提交编号 | 结果 |
|---|---|---|
| 1 | 01a07ad5-ff57-7f78-abac-59fd11cbfe7c | AC |
| 2 | 01a07ad6-0aeb-7c0d-b7d7-0ec6a8983a11 | WA |
| 3 | 01a07ad6-11b6-7b24-b9db-cfbf341b6769 | CE |
| 4 | 01a07ad6-1aee-72bc-bd8e-57d39c19a913 | TLE |
| 5 | 01a07ad6-4595-710e-a23c-91d2ae77764c | WA |
| 6 | 01a07ad6-5342-76f3-9b31-69dde5902c73 | AC |
| 7 | 01a07ad6-5fb6-7993-82a2-18f66760af49 | SE |
| 8 | 01a07ad6-9564-79a7-98aa-3153ed8ecb09 | TLE |
| 9 | 01a07ad7-4819-7827-9213-09fcdb499888 | SE |
| 10 | 01a07ad8-88a5-7300-b654-59feae1fa36a | AC |

镜像内容 ID（`docker image inspect`）：

- Judge：`sha256:0969dea11ce0bf796ad8f7fae05447245e5c4a1b65ce81cd66fcdca2e6f58852`
- sandbox：`sha256:3e439f943bf8c91a078e3fe2c83926eff41d82a19512b12ad5a5f69e249586dc`

验收结束后运行以下命令通知脚本正常退出，并只清理本轮资源；日志与证据保留：

```bash
touch /var/folders/_m/62m76bs510j0640qx9q9bfmh0000gn/T/cherry-work002-w7ma1cqw/stop
```


### 负责人手测反馈与提交授权

2026-09-07，负责人反馈“我手工测试了，确实可以提交”，并明确要求提交本轮工作、推送远程 main。
该反馈按实际范围记录，不推断负责人逐项执行了全部故障场景，也不代为执行验收闸命令。
- 2026-09-07：验收闸通过：review → approved。原因：已完成隔离环境及本地开发环境手工验收，确认正式提交与判题闭环通过

- 2026-09-07：负责人已签署验收闸并授权提交推送。开发环境使用独立 Kafka 和 IDEA 本地服务配置；数据库密码保留在本地运行配置，源码维持环境变量注入。运行数据 ZIP 不纳入版本控制。
