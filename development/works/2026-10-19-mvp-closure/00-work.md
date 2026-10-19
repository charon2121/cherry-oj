# MVP 收尾验证

类型：验证 · 创建：2026-10-19 · 状态：完成（2026-10-19）

## 为什么做

PRD §3.4 的六条完成基线在隔离栈（`scripts/work-002-e2e.py`）里都有证据，但还有三处没在真实环境运行过：
原生 Linux 节点上的 CORE、Web 对真实栈的 `judge-node` 与 `e2e-live`、Linux 业务闭环 CI。

## 结果

| 项 | 证据 |
|---|---|
| 原生 Linux 节点上的 CORE | Benchmark 工作流本来就用正式安装器在一次性 VM 上装真实节点（systemd 单元 + setuid 执行器，`backend=linux`）。`5c8562c` 的运行 39051771624 中 `core-add` 7 项全过（AC、两种 CE、RE、WA、不支持语言 SE、自测 AC）。测试服务器节点未改动（仍是 2026-10-14 的 `cherry-home-v2`，不支持 CORE）。 |
| `judge-node`（Web 对真实栈） | 对 `work-002-e2e.py --keep` 隔离栈运行，暴露 320px 下已公开题目的工作台横向溢出 22px（`488f476` 修复并补 mock 用例）；修复后通过：停掉真实判题节点，两个主题与 320px 下显示离线并禁用校准，恢复后状态恢复。 |
| Linux 业务闭环 + `e2e-live` | 恢复为手动触发的 `.github/workflows/business.yml`。运行 [39196126198](https://github.com/charon2121/cherry-oj/actions/runs/39196126198)（`946ba8f`）15 项全部 PASS，清理确认：原生节点注册与 verify-native、协议目录上传、标定、8 种自测（MLE/OLE/信号均由真实 cgroup 判出，执行观察采到 cpu/memory 两次执行及其退出）、Kafka 正式提交 AC（6/6）与 WA 及其证据、历史回看（草稿保留、无额外执行）。报告见 [business-report-39196126198.json](./business-report-39196126198.json)。 |

## 让业务闭环重新跑通改了什么

脚本在 2026-10-06 精简 CI 后按新架构改写过但从未运行，前四次运行依次暴露的问题：

1. 必须执行的 Linux 测试名单里有改名前的 `TestDeploymentRejectsChangedFilesAndLimits`（`a115ac9`）。
2. `e2e-live` 还按重做前的自测界面写（"自定义输入"页签、一次运行一个请求、"标准输出"）（`a115ac9`）。
3. 测试数据根目录在 0700 的私有目录里，原生节点的 judge 读不到（`4d9792f`）。
4. 托管 VM 的 `/opt` 不满足节点家目录"祖先归 root 且不可写"的要求（`572b31f`）。
5. 用例 id 仍叫 `business.deploy`（`572b31f`）。
6. 页面预览把 `/api` 代理到 `ports.env` 里本地联调的 9080，而 CI 的 gateway 在 8080（`c5cbdeb`）。
7. 观察器、残留检查、预检仍按 2026-10-14 之前的 UID（61001–61010）识别本项目身份（`572b31f`、`946ba8f`），改为取自安装器布局。

## 没有做

- 测试服务器原生节点没有重装，所以"节点连上控制面并参与业务判题"只在一次性 VM 上验证过。
- `business.yml` 只手动触发，不随提交运行；里程碑验收时手动跑。

## 结论

PRD §3.4 六条基线都有实际验证证据：管理员公开 ACM 与 CORE 题（e2e、浏览器实际出题）；普通用户读题、编辑、
自测、提交、结果与历史（业务闭环 `e2e-live`，找题由题库 mock 用例覆盖）；AC/CE/WA 与平台故障判 SE（e2e、业务闭环）；
CORE 合并与行号（e2e、Benchmark `core-add`）；不泄漏（e2e 标记检查）；可追溯（e2e）。2026-10-19 负责人确认据此
声明完整 MVP 已交付，并写入 `docs/product.md` §3.4 与 `docs/status.md`。
