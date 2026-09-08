---
id: "TASK-092"
type: "task"
title: "恢复自定义运行并移除运行冷却"
status: "done"
work: "WORK-047"
owners: ["codex/root"]
depends_on: ["ISSUE-014", "DESIGN-041"]
related: []
implements: ["ISSUE-014#REQ-001", "ISSUE-014#REQ-002", "ISSUE-014#REQ-003"]
verifies: []
tags: []
read_paths: ["apps/server", "apps/judge-engine", "apps/web", "development", "docs", "scripts"]
write_paths: ["apps/server/gateway-service/src/main/java/com/cherryoj/gatewayservice/trial", "apps/server/gateway-service/src/test", "apps/server/submission-service/src/main/java/com/cherryoj/submissionservice/trial", "apps/server/submission-service/src/test", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/trial", "apps/server/judging-service/src/test", "development/works/WORK-047", "apps/web/src/features/problems/components/admin-problem-workbench.tsx", "apps/web/e2e", "apps/server/judging-service/src/main/java/com/cherryoj/judgingservice/operations", "apps/server/judging-service/pom.xml", "apps/server/scripts/judge-environment", ".env", "apps/server/submission-service/src/main/resources/application-local.yaml"]
forbidden_paths: ["apps/judge-engine", "apps/web/design-system", "apps/web/src/components/ui", "contracts", "apps/server/identity-security-support"]
created_at: "2026-09-08"
updated_at: "2026-09-09"
---

# TASK-092：恢复自定义运行并移除运行冷却

## 任务目标

按 DESIGN-041 恢复当前题目部署并移除点击频率限制，交付真实运行证据。

## 依据

ISSUE-014#REQ-001 至 REQ-003。

## 可查看范围

以 front matter 为准，私有配置只在内存使用，不输出。

## 可修改范围

Gateway trial 与相关测试；Submission/Judging 自定义运行诊断及相关测试；工作文档。现有管理 API 仅用于当前题目的部署恢复。

## 禁止修改

Go/sandbox、身份安全实现、数据库结构或密码、正式提交语义、全局清理 Redis、伪造就绪。不得提交推送。

## 依赖

用户审核本次文档、签署意图闸并允许实施后开始。

## 产出

准入清理代码、回归测试、真实节点恢复记录与验收证据。

## 完成标准

- [x] 当前题目数据安装到当前节点，真实自定义运行输出正确。
- [x] 超过 10 次顺序运行和失败后立即重试无冷却。
- [x] 在途并发、取消竞态、真实执行超时及日志隐私检查通过。

## 验证

针对性 Gateway Redis 集成测试、三服务相关测试、真实节点烟测；记录准确请求结果及部署状态。源码/输入不进入日志。

## 风险

节点或数据前置条件缺失时明确报告，不通过数据库手改或关闭资源限制解决。

## 执行记录

- 2026-09-08：只读诊断及方案完成，尚未实施。
- 2026-09-08：状态变更：todo → ready。原因：用户已签署意图闸并允许执行
- 2026-09-08：状态变更：ready → doing。原因：开始修复运行准入与真实节点部署验证

- 2026-09-08：后端准入与诊断完成，11 项回归通过并重启生效；部署恢复遇到已发布版本按钮被禁用，等待 DESIGN-041 范围补充确认。

- 用户明确允许 DESIGN-041 的范围补充：仅修复已发布题目的重新部署按钮及对应 Web 回归；先扩展上述路径，再实施。

- 2026-09-08：Web 按钮修复、2 项浏览器回归、构建和 ESLint 通过；真实部署发现原始 ZIP 已缺失，等待用户提供匹配归档，未伪造 READY 或修改已发布版本。

- 用户授权生成新测试数据；按 DESIGN-041 补充，通过现有管理界面创建同一题目的新修订、上传/绑定/部署/校准/发布，完成恢复。不修改后端管理 API 实现或数据库结构；临时产物位于 /tmp/work047-recovery。

- 2026-09-09：按授权完成 6 组新数据和 v2 上传/部署/校准/发布。真实运行定位为旧 Judge 响应缺少 stderr；更新镜像触发节点环境指纹冲突，已回退。后续环境切换入口不在已授权代码范围，保留未完成状态，详情见 VERIFY-048。

- 2026-09-09：用户回复“继续”，授权环境切换补充方案。先登记上述精确边界，再实现受控事务 CLI；该入口拥有的事务更新与审计属于授权实现，不允许临时脚本直接修改就绪或标定事实。

- 2026-09-09：受控环境切换 4 项 MySQL 测试通过，真实新节点部署/校准/v3 发布完成；用户页面 stdout/stderr、再运行、死循环超时与恢复运行均通过。正式提交烟测等待单独授权，暂不标记任务完成。

- 2026-09-09：用户明确授权一条验证提交；恢复本地提交开关后沿用原幂等请求，提交 01a081ed-e4ce-7d36-952d-1eb3601cc7bb 最终 AC（6/6）。完成全部验收证据，等待人工验收。
- 2026-09-09：状态变更：doing → done。原因：真实自定义运行、超时恢复与正式提交 AC 均通过；环境切换和准入回归通过
