---
id: "VERIFY-009"
type: "verify"
title: "建立统一的 Web REST 交换协议与请求基建"
status: "approved"
work: "WORK-009"
owners: ["codex/root"]
depends_on: ["TASK-009"]
related: []
implements: []
verifies: ["CAPABILITY-002", "TASK-009"]
tags: []
result: "pass"
created_at: "2026-08-25"
updated_at: "2026-08-25"
---



# VERIFY-009：建立统一的 Web REST 交换协议与请求基建

## 验证对象

TASK-009 交付的 OpenAPI、Gateway 公共层、Web client/parser、status 迁移及兼容和安全边界。

## 对应要求

验证 CAPABILITY-002 的 REQ-001 至 REQ-019、DECISION-006 的人工确认项和 TASK-009 完成标准。

## 检查与结果

验证日期为 2026-08-25。本地使用 OpenJDK 21.0.12.1；Web 项目声明 Node 24，当前桌面环境为
Node 26.3.0 / npm 12.0.2，因此 npm 会提示 engine warning，CI 继续以 Node 24 为门禁。

- `python3 scripts/contracts_test.py`：7/7 通过；校验 9 份 JSON 契约、全部本地 `$ref`、required
  examples、OpenAPI 3.1.2、ApiSuccess/ApiProblem 媒体类型和 request ID 示例。
- `npm run generate:api:check`：通过；@hey-api/openapi-ts 0.99.0 在临时目录重建 2 个类型文件并与
  `src/generated/api` 逐文件一致。初选 openapi-typescript 7.13.0 因 peer 仅支持 TypeScript 5.x 被
  正常拒绝，没有使用 force/legacy-peer-deps 绕过。
- Gateway 组件测试覆盖单资源 success、cursor/page、400、401、403、404、409、413、415、422
  violations、429、500、502、503、504、非法来访 request ID、header/body 一致和未知 5xx 脱敏；
  最终定向执行 17/17 通过。
- `cd apps/server && ./mvnw clean verify`：Reactor 6/6 成功；加入 pagination 类型后又执行聚合
  `./mvnw verify`，Gateway 与四个业务服务均编译、测试和打包成功；最终新增的状态矩阵用例由上述
  Gateway 定向套件复核。
- `cd apps/web && npm run check`：通过；OpenAPI drift、Prettier、ESLint、TypeScript 与 Vitest 全绿，
  4 个测试文件共 19 个测试通过。覆盖 request body 必须为可序列化 object、success 未知字段、cursor
  pagination、201/202 + Location、
  缺 Location、204、Problem + Retry-After(ns)、request ID 不一致、downstream HTML、aborted/timeout、
  Query 重试边界和 status UI。
- `npm run build` 与 `npm run storybook:build`：通过；Vite 生产构建和 Storybook 静态构建均完成。
- `npm run test:e2e`：Chromium smoke 1/1，通过统一 envelope 驱动应用成功态。
- 真实启动 Gateway 后请求 `127.0.0.1:8080/api/status`：返回 200、`application/json`、
  `X-Request-Id: req_...` 与 `{data,meta}`，header 和 `meta.requestId` 完全一致。
- 同时启动 Gateway 与 Vite 后请求 `127.0.0.1:5173/api/status`：Vite proxy 保留同一 header/body，
  验证真实开发链路；随后两个进程均已停止。
- `python3 scripts/docs_test.py`：94 份 Markdown 入口和本地链接有效；`scripts/work check` 与
  `git diff --check` 在最终状态更新后执行。

当前没有业务创建、鉴权、限流或下游路由 endpoint，因此 201/202、204、401/403/429 与非法 upstream
通过公共基础层组件测试验证，不虚构生产业务联调。后续每个 endpoint 仍须在自己的工作项增加契约和
端到端用例。

## 未通过项

暂无。过程中 Playwright 和本地服务首次在受限沙箱绑定/访问端口失败，按环境要求在获批的沙箱外
重跑通过；这是已解决的执行环境限制，不是代码失败。

## 范围检查

对照 TASK-009 的 write/forbidden paths，改动只在获准的契约、Gateway、Web、CI、全局协议文档和
WORK-008/009。未修改业务服务源码、judge、数据模型或被阻塞的 WORK-002，也没有改变 approved wire。

## 遗留问题

生产发布、同源路由和上线指标观察不在本工作内；未来业务 endpoint 必须逐项接入 contract、权限、
幂等和端到端验证，不能仅以 status 纵向切片代替。

## 剩余风险

Gateway 当前仅有 browser-facing status controller，尚无真实下游路由可验证超时和非法响应的服务端
映射；Web 已验证非 JSON/timeout 分类，后续首个下游 BFF endpoint 必须补 Gateway WebClient/route
映射集成测试。当前 @hey-api/openapi-ts 为 0.x，已精确锁版本并用生成漂移门禁控制升级风险。

## 结论

CAPABILITY-002、TASK-009 与 DECISION-006 的仓库内实现及本地跨模块链路验证通过。release 与 observe
仍必须等待真实部署，当前结论不代签生产发布。

## 变更记录

- 2026-08-25：状态变更：draft → review。原因：契约、后端、前端、浏览器、真实代理与安全矩阵已有完整通过证据
- 2026-08-25：状态变更：review → approved。原因：所有仓库内完成标准与本地跨模块验证通过，发布和线上观察保留为后续阶段
