---
id: "TASK-100"
type: "task"
title: "验收新节点校准与真实运行提交闭环"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-099"]
related: []
implements: ["IMPROVEMENT-004#REQ-005", "IMPROVEMENT-004#AC-004", "IMPROVEMENT-004#AC-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "development/works/WORK-048", "apps/judge-engine", "contracts", "apps/server/scripts/judge-environment", "docs", "development/works/WORK-044", "development/works/WORK-047", "deploy/sandbox-linux", "deploy/sandbox-linux/tests/acceptance", "apps/server/problem-service/src/main/java", "apps/server/judging-service/src/main/java", "apps/web/src/api", "apps/web/vite.config.ts", "apps/web/package.json", "apps/web/dist", "apps/server/gateway-service/src/main/resources", "apps/server/judging-service/src/main/resources", "apps/server/judging-service/target/judging-service-0.0.1-SNAPSHOT.jar", "apps/server/TOOLCHAIN.md"]
write_paths: ["development/works/WORK-048", "deploy/sandbox-linux/tests/acceptance"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine", "deploy/backend", "compose.yaml"]
created_at: "2026-09-09"
updated_at: "2026-09-10"
---

# TASK-100：验收新节点校准与真实运行提交闭环

## 任务目标

通过既有管理入口完成新环境数据部署和重新校准，并验证真实自定义运行与正式提交。

## 依据

front matter 的 implements 条目、DESIGN-042 冻结结果与 PLAN-032。

## 可查看范围

以 read_paths 为准；写入范围也已列入可读范围。

## 可修改范围

以 write_paths 为准。使用既有受保护 API/运维入口，仅操作 cherry-oj 新测试节点及对应测试题数据。先查询现状并准备带审计的切换计划；当前管理入口仅支持 ACTIVE 环境，须先取得具体切换批准，再部署并校准。切换当前可用节点需用户明确批准具体对象。本任务不改 Java/Web/Go 代码、不直接写库。

## 禁止修改

以 forbidden_paths 为准；不得修改现有可用判题节点、全局安全策略或无关服务。

## 依赖

以 depends_on 为准。当前为 TASK-093 新拆分的实施材料，保持 todo；用户审核并明确允许这些具体实施范围后，依次推进 ready，不从已签的只读探测授权推断编码或部署许可。

## 产出

新节点数据部署/校准记录、真实业务回归证据及剩余风险。

## 完成标准

- [x] 新环境身份与工具链/策略摘要匹配，部署测试数据并重新校准；不复制旧标定或伪造指纹。
- [x] 自定义运行 stdout/stderr、CE/RE/TLE/MLE/OLE；正式提交 AC/WA 等结果闭环可核对。
- [x] 大输出后空程序、1 秒 CPU 死循环在真实业务入口回归，记录资源口径和误差。
- [x] 跨发行版/内核/架构已验证与待验证明确；机器重启未授权或未执行须保留未完成项，不代签验收闸。

## 验证

用 apps/server/scripts/judge-environment 查询、受审部署/校准/切换，以及真实 API/页面验证；逐条关联 AC，隐藏源码、答案、凭据与私有服务器地址。

## 风险

业务数据操作与节点切换属于外部写入，必须在本任务明确授权范围内；遇到 Java/Web 缺陷另行界定修复任务。

## 执行记录

- 2026-09-09：TASK-093 根据只读探针和现有源码拆分；尚未编码、测试或部署。

- 2026-09-09：用户已阅读纯 Go 材料并明确表示“没有问题，你可以开始编码”；本地代码和测试实现已授权，按依赖推进。远端安装、机器重启及现有节点切换仍按具体任务边界执行。
- 2026-09-10：状态变更：todo → ready。原因：TASK-099已完成，用户明确继续数据部署、重新校准与业务验收；ACTIVE切换单独确认具体对象
- 2026-09-10：状态变更：ready → doing。原因：核验新环境在线状态、既有部署校准入口及当前题目数据，为受审切换准备完整前置条件
- 2026-09-10：状态变更：doing → done。原因：已批准切换新ACTIVE，v4部署校准发布、自定义状态与资源回归、正式AC/WA及源码回看通过，最终无任务残留；机器重启与平台限制已单列

## 本轮执行授权与只读边界补充（2026-09-10）

TASK-099已完成，用户明确继续下一步。开始新节点cherry-linux-2的业务数据准备与重新校准，通过现有管理/API入口操作；当前ACTIVE环境切换仍须核对具体对象并由用户明确确认。为核验现有API是否支持REGISTERED环境预部署/预校准，新增只读范围apps/server/problem-service/src/main/java、apps/server/judging-service/src/main/java及apps/web/src/api（仅查看调用定义），不改这些模块。若当前API强制ACTIVE，则准备好具体切换和回退计划后等待授权，不直接改库、复制标定或临时扩大业务API。浏览器会话使用现有登录状态，不提取凭据。

管理员登录返回通用错误后，为定位前端预览端口与网关来源校验，增加只读范围 apps/web/vite.config.ts 和 gateway-service/src/main/resources；不输出秘密、不改配置、不重启服务。用户随后提供管理员账号，允许仅在本地登录流程中使用，不保存凭据到文件。

配置核验发现预览端口4173不在网关默认受信来源localhost:5173中。允许只读现有package.json/dist并在空闲5173端口启动本任务独立Vite预览进程，只绑定本机回环；用现有构建，不重编译或写Web文件、不关闭来源检查。记录PID，验收会话结束后只停止本任务进程；原4173与IDEA服务保持运行。

## TASK-100 切换清单（用户已批准，2026-09-10）

只读核验 NodeDeploymentService/JudgingReadinessService：部署和校准均选择当前 ACTIVE，没有目标环境参数。
ProblemPublicationService 的校准要求 DRAFT，公开 v3 不能原地重校准。因此调整为先准备草稿，
再受审切换、部署、校准、发布；不调用内部服务绕过题目版本状态约束。

| 对象 | 当前事实 |
|---|---|
| 原环境 | judge-local-2；`01a081e0-bd32-779e-b42f-ef0ba5fe2ce3`；ACTIVE；row_version=1；在线1节点 |
| 目标环境 | cherry-linux-2；`01a08a09-23b1-7ed5-bc0f-e03002e65e4a`；REGISTERED；row_version=0；在线1节点 |
| 目标指纹 | `79b8cb44ea253f47c44c2cc518fc4b0f785d9b7f69f857e110bfea9df07835bb`，来源TASK-099最终注册证据 |
| 测试题 | a-plus-b-ii；`01a079be-f10a-7175-90ff-e221b025d495` |
| 当前公开版本 | v3；`01a081e2-1541-70f9-917a-c24ca4133ef9`，发布检查显示部署READY、校准VALID |
| 已准备新草稿 | v4；`01a08a30-ff4c-7a71-9112-9a4dde2923e1`；DRAFT，复用v3内容与数据绑定，尚未校准或发布 |
| 测试数据 | `01a081c5-1a7b-7bac-8320-e35047f40be6`；6组、80 B |
| ZIP摘要 | `17c63562178a77205e3e3d18f4a1eb81eabc0be922a6339dca8dd8cd7570dcf8`，已从管理页核验 |

批准后的操作顺序：

1. 重新查询环境及在线节点；若对象/版本变动则停止，不能自动改预期版本绕过乐观锁。
2. 用现有审计入口执行下列切换。该命令目前只准备，未执行。
3. 管理页对v4部署既有数据，要求返回新环境、相同摘要和READY；不手工上传到节点目录。
4. 用acceptance/sum.cpp在新环境执行真正校准：CPU 1 s、内存256 MiB、墙钟留空，读回有效限制；
   校准为VALID且发布检查全通过才发布v4。保存新校准ID和资源摘要，不复制旧记录。
5. 按acceptance/README.md串行完成8次自定义运行、正式AC/WA各1次，核对版本、身份与清理。

```sh
apps/server/scripts/judge-environment switch \
  01a081e0-bd32-779e-b42f-ef0ba5fe2ce3 1 \
  01a08a09-23b1-7ed5-bc0f-e03002e65e4a 0 \
  'WORK-048 TASK-100: activate cherry-linux-2 for fresh data deployment, calibration and acceptance'
```

影响与回退：切换是整个本地后端的 ACTIVE 环境变更，不仅影响此题；切换到新环境后，
尚未部署/校准的版本会拒绝运行和提交。预期公开v3暂时不可判题，v4发布后恢复此题；其它题不纳入本次迁移。
批准前不改变ACTIVE，v4草稿不替换公开v3。

发布v4前若失败：停止验收、不发布；重新list，核对双方状态/最新row_version和旧节点在线，
可通过同一switch入口反向恢复原开发环境与仍公开的v3。回退记录必须说明原环境仅供可信开发，
不能把它作为不可信代码的安全降级。v4草稿和失败审计保留，不直接删库或清理原ZIP。

发布v4后若失败：旧环境没有v4的有效校准，单纯反向切换不能恢复此题。停止不可信运行，保留Linux
现场并修复/重新校准；若必须恢复旧开发环境，需通过正常修订/校准/发布流程处理，不能改公开版本指针或复制标定。
本清单不承诺一条命令完成发布后的业务回退，不自动重启任何现有服务或机器。

本轮已准备10份静态C++夹具，9份语法通过、1份按预期编译失败；未在本机运行资源消耗夹具。
Ubuntu原生三个服务和slice仍active，资源封顶读回正确；TASK-100业务实测仍待具体切换批准。

用户阅读具体对象、暂时不可判题的影响和后续步骤后明确“批准”。授权按本清单切换
judge-local-2 → cherry-linux-2、部署与独立校准v4、检查通过后发布，并完成8次自定义运行及正式AC/WA各1次。
此批准不是WORK验收闸签署；执行前仍核对对象、row_version及在线状态。

验收取证补充：页面只展示VALID，不展示校准ID及完整身份关联；现有运维CLI只有list/switch。
允许acceptance目录的只读取证驱动复用同一judging-service配置和已构建JAR依赖，在数据库只读事务内
以固定SELECT核对本次v4、新环境、部署及切换审计摘要。增加对应配置/JAR/工具链的精确只读路径，
不启动Spring服务或迁移，不写库，不输出连接串、凭据、源码或答案；限定目标ID、行数和查询超时。
业务变更仍只能走前述管理入口，不借取证驱动实现切换/校准。观察脚本首轮程序名大小写不符，
同一CPU用例串行补测一次以完成墙钟取证；实际共9次自定义运行，正式提交仍仅AC/WA各1次。

## 完成记录（2026-09-10）

已按用户具体批准切换新ACTIVE；v4部署六组数据、真实校准VALID并发布。自定义运行8类用例及
CPU观测补测通过，正式AC为6/6、WA为0/6，代码回看正确且未改变编辑器草稿。最终取证将新校准ID、
实际环境指纹、部署摘要及切换审计ID关联；独立只读清理检查无任务/组/挂载/文件/产物残留。
所有业务动作通过既有入口，没有生产Java/Web/Go改动、直接写库或复制旧校准。

CPU1 s两次实际1004.207/1004.343 ms，补测运行可见区间1.051588756 s；5 ms采样不是严格误差保证。
OOM与大输出分别为MLE/OLE，大输出后空程序为COMPLETED、3880 KiB组峰值。完整数值与ID见VERIFY-049。
校准实际输入保存为calibration.cpp（与sum.cpp同一A+B逻辑），其SHA256与新校准记录完全一致。

本TASK在已授权首站范围完成；机器重启尚未授权、服务开机自启未开启，其他Linux保持待验证，
arm64当前后端明确不支持。WORK的AC-004仍缺机器重启证据，VERIFY保持review/partial，未代签验收。
