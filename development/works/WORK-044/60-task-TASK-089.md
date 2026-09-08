---
id: "TASK-089"
type: "task"
title: "移除下游重复开关并验证Gateway单点控制"
status: "done"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["DESIGN-039", "TASK-086"]
related: []
implements: ["FEATURE-012#REQ-001"]
verifies: []
tags: []
read_paths: ["apps/server", "development/works/WORK-044", "docs/engineering"]
write_paths: ["apps/server/gateway-service/src", "apps/server/submission-service/src", "apps/server/judging-service/src", "apps/server/README.md", "development/works/WORK-044"]
forbidden_paths: ["apps/judge-engine", "apps/web", "contracts", "apps/server/identity-security-support", "apps/server/user-service", "apps/server/problem-service"]
created_at: "2026-09-08"
updated_at: "2026-09-08"
---

# TASK-089：移除下游重复开关并验证Gateway单点控制

## 任务目标

落实 DESIGN-039：Gateway 单独控制公开自测开关，移除下游重复判断和配置，保留所有现有鉴权与执行保护。

## 依据

FEATURE-012#REQ-001、DESIGN-038、DESIGN-039；配置职责以新补充设计为准。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

三服务相关代码/配置/测试、README本地配置说明、VERIFY-045新增真实验证证据。

## 完成标准

- [x] 只有Gateway读取CHERRY_CUSTOM_RUN_ENABLED，下游无重复开关。
- [x] Gateway关闭不转发；开启且下游未配置开关时可进入完整执行编排。
- [x] 下游鉴权失败、Gateway CSRF、限流/并发/超时、正式提交既有行为不回归。
- [x] 删除构造器参数后全部受影响测试通过；默认关闭和开关配置说明正确。
- [x] VERIFY-045记录实际结果，用户暂缓的sandbox缺陷不伪报通过。

## 验证

apps/server 下运行 ./mvnw -pl submission-service,judging-service,gateway-service -am test。重点测试真实路由：Gateway默认关闭/显式关闭不请求下游；Gateway开启时下游无需开关；judging合法服务凭据进入执行逻辑（修改原先以关闭开关503为到达断言的用例），无凭据或管理员JWT仍拒绝；submission版本检查与身份验证保持。rg检查生产引用只剩Gateway。共享功能不变，不重复无关Web视觉测试。

## 风险

本轮先文档审核，后续明确允许执行才改业务代码；不代签验收。仅修改本次配置职责和受影响测试，不改身份信任链、sandbox或默认开放策略。额外需求先升级设计。

## 执行记录

- 2026-09-08：创建任务。
- 2026-09-08：状态变更：todo → ready。原因：方案确认且依赖完成
- 2026-09-08：状态变更：ready → doing。原因：开始重构单点开关

- 2026-09-08：用户明确“方案通过，开始实施”。已移除submission/judging字段、构造器参数、禁用分支及YAML配置；Gateway变量名与默认false保留。新增关闭入口不准入不转发、下游无开关转发成功测试，并更新真实服务鉴权路由断言。三服务及依赖完整Maven回归115通过、1项需真实Linux环境的测试跳过；日志 /tmp/work044-toggle-tests.log。README本地启动说明已更新，未重启当前服务或提交代码。
- 2026-09-08：状态变更：doing → done。原因：Gateway单点开关重构及完整后端回归通过，证据已记录
