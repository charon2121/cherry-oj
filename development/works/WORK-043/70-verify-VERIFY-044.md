---
id: "VERIFY-044"
type: "verify"
title: "题目内提交记录与代码回看"
status: "approved"
work: "WORK-043"
owners: ["codex/root"]
depends_on: ["TASK-082", "TASK-083", "TASK-084"]
related: []
implements: []
verifies: ["FEATURE-011#AC-001", "FEATURE-011#AC-002", "FEATURE-011#AC-003", "FEATURE-011#AC-004", "FEATURE-011#AC-005", "FEATURE-011#AC-006", "FEATURE-011#AC-007", "TASK-082", "TASK-083", "TASK-084", "FEATURE-011#REQ-001", "FEATURE-011#REQ-002", "FEATURE-011#REQ-003", "FEATURE-011#REQ-004", "FEATURE-011#REQ-005", "FEATURE-011#REQ-006", "FEATURE-011#REQ-007", "FEATURE-011#REQ-008", "FEATURE-011#REQ-009", "FEATURE-011#REQ-010"]
tags: []
result: "pass"
created_at: "2026-09-07"
updated_at: "2026-09-07"
---

# VERIFY-044：题目内提交记录与代码回看

## 验证对象

题目工作台左侧题目描述/提交记录 Tabs、本题本人历史、原始源码回看及确认载入右侧编辑器。意图闸已由用户签署并明确允许实施。

## 对应要求

FEATURE-011#AC-001 至 AC-007；REQ-001 至 REQ-010。TASK-082/083 已交付，TASK-084 自动验证与用户实际环境复核已完成。

## 检查与结果

环境：macOS、JDK 21、bundled Node 24.19.0、Chromium。后端测试使用隔离 MySQL/Kafka；浏览器使用真实构建产物与 Playwright API fixtures，不冒充真实登录的全栈人工验收。

| 命令/证据 | 实际结果与覆盖 |
|---|---|
| apps/server：`./mvnw -pl submission-service,gateway-service -am test` | 成功；Gateway 58、submission 8、共享身份 8 项。真实 MySQL 覆盖本人/题目隔离、同时间稳定排序、翻页/判定筛选、列表无源码、本人原始源码、他人/不存在拒绝、owner 索引候选；既有提交/消息链路回归通过。 |
| apps/server：`./mvnw -pl gateway-service -am -Dtest=SubmissionServiceClientTests -Dsurefire.failIfNoSpecifiedTests=false test` | 后续 3 项成功；大源码原样读取、身份字段不符拒绝、只传 delegated token、最大 100 条转义诊断响应不被缓冲上限误拒绝。 |
| apps/web：`npm run generate:api`、`npm run check` | 生成契约一致；设计系统校验、格式、Lint、类型通过；40 文件 / 168 单元测试通过，含身份变化后迟到源码拒绝与 UTF-8 上限。 |
| apps/web：`npm run build` | 成功；既有大 chunk 提示仍在，不阻断构建。 |
| apps/web：`npx playwright test e2e/submission-history.spec.ts e2e/problem-workspace.spec.ts` | 原工作台 17 项全部通过；初轮新增历史的 Monaco 测试定位与隐藏 label 窄屏溢出失败均已定位并修正。后续新增历史测试单独回归，见下文。 |
| apps/web：`npx playwright test e2e/submission-history.spec.ts` | 最终 7 项全部通过：浏览/取消/确认载入与刷新；进行中结果轮询且源码只读一次、登出隐藏；320px/访客；筛选与旧恢复链接/键盘；缩放/双主题/forced-colors/reduced-motion；源码拒绝重试/长中文；多标签变化使旧确认禁用并保留当前草稿及恢复副本。 |

本机日志：`/tmp/work043-backend.log`、`/tmp/work043-client.log`、`/tmp/work043-web-check.log`、`/tmp/work043-build.log`、`/tmp/work043-e2e.log`、`/tmp/work043-e2e-final.log`、`/tmp/work043-conflict.log`。日志及截图是本次本机证据；可通过仓库测试重建。

## 页面八问

1. 使用工作台面板分隔线、列表 hairline；新增详情仅代码编辑区有边界，用于独立代码滚动/选择，不给结果再套 Card。确认弹窗沿用既有 Dialog 边框及浮层材质。
2. 短判定标题使用 DataList packed 固定标题列；时间/编号折到第二行。桌面与 320px 截图对照，无字段被推到两端；窄屏通过折行而非页面横滚降级。
3. foreground 用于判定/题目名，fg-2 用于资源及解释，fg-muted 用于帮助/分页，fg-meta 用于编号/时间/计数。fg-disabled 仅禁用控件；不可载入有原因说明。
4. 仍为 WorkbenchPageTemplate；左侧嵌套现有 Tabs，右侧编辑器保持原挂载身份。没有新增全局页面或导航。
5. 业务代码新增 `var(--ds-*)` 为 0；没有新增 alias 或修改共享原语/token。
6. 判定通过明确文字显示，不用彩色 Badge 编码高低；资源用准确数字和 mono。保留既有主提交按钮品牌色，历史列表无饱和色铺陈。
7. 已查看暗色桌面/320px、实际切到亮色后的 640×475（200% 等效视口）、forced-colors 截图。键盘方向键/Enter 切换 Tabs、reduced-motion 模式、长中文 320px 无横向溢出均有 E2E。200% 是等效视口测试，并非操作浏览器缩放菜单。
8. 对照 measurements：行使用 min-h-11，双行元信息允许超过 44px；E2E 实测断言行高 ≥44px、标题 13px，并附行高/内边距/gap/行高计算样式。packed 标题宽度沿用原语；窄屏 gutter 沿用原语的 phone 值。按钮三态、16px 图标、簇内间距及 1px 焦点使用既有组件，未重新定义；未声称逐像素穷举所有按钮状态。

截图：`/tmp/work043-history-list.png`、`/tmp/work043-history-detail.png`、`/tmp/work043-history-mobile.png`、`/tmp/work043-history-zoom-light.png`、`/tmp/work043-history-forced-colors.png`。

## 未通过项

无未解决的验收问题。验收闸仍待用户签署。

## 实际环境人工复核

2026-09-07：用户独立验收先报告 requestId `req_e3229678c16249adbde9d581fac67ce0` 返回 405。实际网关日志记录该 GET 路由未匹配，原进程启动于 19:21，尚未加载新增接口。20:31 两服务重启后，实际网关 OPTIONS 已包含 GET，匿名 GET 进入正常 401 认证检查；真实列表及源码请求均记录 HTTP 200（如列表 `req_f846737dbd3c43d8a6fad8fb9e2f0996`，源码 `req_1017962ba09445b29fc57336b01c69fa`）。智能体未重启服务或读取用户源码。

用户随后明确确认“是我没有重启后端，现在已经没有问题了”，并在本轮表示“没有别的问题了”，请求验收闸命令。人工产品复核由用户完成；不将实现者自查冒充独立复核，也不声称用户执行过未报告的源码审计。

## 范围检查

差异限于契约、submission-service/Gateway 提交边界、题目路由 search/工作台、提交历史模块、相应测试及 WORK 文档。无数据库迁移；不改判题引擎、身份信任链、problem-service、共享 Web 原语或全局导航。源码仍来自 submission.source；无 source 的列表/View 与独立 no-store 源码端点保持分离。

## 遗留问题

人工产品复核已完成，验收闸保持未签署。智能体没有部署、重启既有服务、commit 或 push。

## 剩余风险

浏览器 API fixture 回归与用户实际环境验收分别记录。SQL 测试证明隔离和候选索引，未做生产规模性能压测。题目重新打开后为 404 时不提供历史找回页，符合本轮范围。

## 人工验收与回退

1. 打开有历史提交的题目，切换两个 Tab、分页/筛选、打开记录并返回；刷新后状态仍在，右侧草稿不变。
2. 选择旧版本代码，复制、取消载入、再确认载入；检查版本提醒、草稿保留与实际新提交使用当前版本。
3. 使用另一账号（含 ADMIN）核对不能读取前一账号源码，退出时历史隐藏；另一标签修改草稿时旧确认不可执行。
4. 复核公开 API 权限及 no-store、无源码日志，确认产品行为后再由人完成验收闸。

回退仅撤回本工作前后端差异，无迁移或历史数据回填需要回滚；原 POST/结果查询/恢复键接口保持兼容。未在真实环境执行回退。

## 结论

pass：自动验证及用户实际环境复核通过，正式验收签署仍由用户执行。

## 变更记录

- 2026-09-07：状态变更：draft → review。原因：用户确认没有其他问题，自动验证与实际环境记录完整，提交验收闸签署
- 2026-09-07：验收闸通过：review → approved。原因：题目内提交记录与代码回看验收通过，后端重启后运行正常，无其他问题
