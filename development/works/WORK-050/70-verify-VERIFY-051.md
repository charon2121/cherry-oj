---
id: "VERIFY-051"
type: "verify"
title: "将沙箱已验收回归固化为重构 CI"
status: "review"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-109", "TASK-110", "TASK-111", "TASK-112", "TASK-113"]
related: []
implements: []
verifies: ["CAPABILITY-008#AC-001", "CAPABILITY-008#AC-002", "CAPABILITY-008#AC-003", "CAPABILITY-008#AC-004", "CAPABILITY-008#AC-005", "CAPABILITY-008#AC-006"]
tags: []
result: "pending"
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# VERIFY-051：回归 CI 验证记录

## 验证对象

CAPABILITY-008 的用例映射、真实内核/原生部署/业务自动化、汇总可靠性及重构交接。

## 对应要求

AC-001～006分别对应清单、内核、部署、业务、失败/清理汇总以及两次基线。基础套件已本地执行，Linux编排已实现，真实Linux及Actions基线尚未执行。

## 检查与结果

2026-09-10只读盘点现有ci.yml及WORK-048最终证据：当前CI六job已成功，Linux边界测试依赖显式环境，普通Go运行不等于已执行；legacy容器是trusted-host，现有custom-run浏览器测试模拟响应。部署15项/rootfs6项本地Python单测未接CI；真实TASK-100夹具含旧固定ID及手工登录前提，需要独立环境驱动。

已创建分层方案与TASK边界，首轮目标托管Ubuntu24.04/amd64，实际内核与LSM须运行时记录。未连接或修改现有服务器，未安装软件、写workflow或运行压力/部署/业务场景。

## 未通过项

AC-001清单与基础接线已实现；AC-002编排已实现但实机证据待取得；AC-003～006尚未完成，不能从WORK-048旧报告复制PASS。

## 范围检查

当前改动为WORK-050材料、WORK-049回归依赖说明、ci.yml和deploy/sandbox-linux/ci；两个旧测试入口仅增加可选单元名及结果日志。原WORK-048及生产代码不变，未提交推送，原ZIP保留。

## 遗留问题

首次托管VM能力及行为还未验证；93个case已细化，原生部署、完整业务环境与总汇总尚未实现。

## 剩余风险

共享内核、托管镜像升级、资源波动、下载失效和取消期间证据缺失；详见DESIGN-044，不声称所有Linux支持。

## 结论

result=pending；用户已签署意图闸并允许实施，验收闸未签署；完整CI仍在实现。

## 变更记录

- 2026-09-10：状态变更：draft → review。原因：已盘点既有验收与CI缺口，补齐分层方案、边界及验收条件供人工审核；尚未实施

## TASK-109 本地实施证据

- `PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work050-basic-03`：macOS arm64通过，15项安装器、6项rootfs、8项报告自测，加全部非忽略Python AST/shell语法；93项必需case按basic/kernel/native/business分组。
- `ruby -rpsych -e 'p Psych.parse_file(".github/workflows/ci.yml").class'`：YAML解析通过。新增基础job及手动触发，保留原六job；官方Action版本通过GitHub API核验并固定提交。
- 首次自测因CI测试数量下限误写为9而失败，核对实际8项后修正为8重新执行；未改任何沙箱断言。
- 报告拒绝缺项、重复、错误SHA/schema、未执行、取消、清理失败、证据缺失/链接和测试脚本摘要不符。尚无该改动的GitHub执行记录。

## TASK-110 本地实施证据与实机前置

- `PYTHONDONTWRITEBYTECODE=1 python3 deploy/sandbox-linux/ci/basic.py --output /private/tmp/cherry-work050-basic-08`：39项单测通过（15+6+18），包含执行超时/大输出/退出失败、拒绝接管已有资源、拒绝停止未知单元、残留阻止删除、缺Go用例/1000次记录/快照/边界完成记录拒绝。AST与shell语法通过。
- `/private/tmp/cherry-work050-actionlint/actionlint -shellcheck= .github/workflows/ci.yml`：官方1.7.12发行包核验SHA256后静态检查通过；未安装系统工具。
- `CGO_ENABLED=0 GOOS=linux GOARCH=amd64 GOCACHE=/private/tmp/cherry-oj-work048-go-cache go test -c -o /private/tmp/cherry-work050-boundary.test ./tests/sandbox-linux/boundary`：构建通过；未执行，不是跨平台支持证据。
- 93项清单为basic5/kernel63/native10/business15；Linux四包冻结38个实际测试名，普通包成功而缺具体测试仍失败。
- 新增kernel job原生构建本提交及锁定rootfs，在独占VM内按原预算串行执行；finally与always清理只认本轮root所有权标记，最终检查全部存活进程的挂载/身份和执行cgroup。每命令日志最多2MiB，同目录18MiB日志预算，为报告保留空间。
- 尚无该批Actions运行；TASK-110保持doing，TASK-111～113未开始，WORK-049重构尚不能启动。根据CLAUDE提交规则及PLAN-034阶段验证顺序，下一步需要用户授权发布此批CI，以取得真实Linux结果后继续。

- `scripts/work check`：429份文档通过，保留既有WORK-033提示。`refresh WORK-050`因后续TASK仍todo而将开发任务阶段视作未完成，拒绝将WORK推至doing；未绕过工具或提前置后续任务ready。当前TASK-109 done、TASK-110 doing的事实记录保留。

## 首轮 GitHub 实际执行（保留失败）

用户于本轮明确允许提交推送，已发布7cd13146925664965749769a6773d4cd2bfa3f3b。运行：[34464207491](https://github.com/charon2121/cherry-oj/actions/runs/34464207491)，8个job中6成功、2失败，不是完整通过。

- basic通过5/5，39项Python测试、43个Python文件和4个shell入口；下载报告核验sourceSha/harnessSha/证据和cleanup均通过。
- kernel实际环境Ubuntu24.04.5、Linux6.17.0-1022-azure、x86_64、4CPU、约16GiB内存及3GiB swap，LSM含AppArmor；本轮未关闭安全策略。锁定rootfs构建及38个Linux包测试通过。
- 四组真实边界通过：缺控制器拒绝、11类exec阶段/errno、9类文件拒绝与1000次路径交换、8类启动握手；静态线程采样因/proc/8333/task/8336/status在枚举后消失而失败。后续用例NOT_RUN，不能算通过；最终tasks/mounts/cgroups全部为空，cleanup PASS。
- 修正测试采样：线程消失时丢弃整份样本，在原2s观察期限内再次采样，不能保留部分线程绕过全线程权限断言。
- 原有Go job的TestEndToEndCpp与TestEndToEndJavaWithInnerClass各在5.01s编译退出-1；未出现race诊断。两者使用未修改的host开发夹具。包间资源竞争只是待验证推断；CI改为-p=1限制测试包间调度，保留包内并发、race、完整包集合及原5s期限，并保证失败时仍打印工具链版本。
- 本次失败报告和日志保留在原运行产物中。修正后使用新提交完整运行，不将首轮覆盖或登记为成功。
