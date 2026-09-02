---
id: "VERIFY-029"
type: "verify"
title: "修复后台用户列表偶发误跳登录页"
status: "approved"
work: "WORK-028"
owners: ["codex/root"]
depends_on: ["TASK-046"]
related: []
implements: []
verifies: ["ISSUE-006#AC-001", "ISSUE-006#AC-002", "ISSUE-006#AC-003", "ISSUE-006#AC-004", "ISSUE-006#AC-005", "ISSUE-006#AC-006", "TASK-046"]
tags: []
result: "pass"
created_at: "2026-09-02"
updated_at: "2026-09-02"
---

# VERIFY-029：修复后台用户列表偶发误跳登录页

## 验证对象

ISSUE-006#AC-001～AC-006、TASK-046、Gateway 强制 token 恢复、Admin 用户列表、Redis Session 并发回写
和 Web 登录跳转边界。

## 对应要求

front matter 已锚定 ISSUE-006 六项 AC 与 TASK-046。实施后逐项记录：正常路径、一次恢复、真实撤销、
临时失败/二次拒绝、并发单飞与密钥切换回归，不能用单一 happy-path 测试代替。

## 检查与结果

验证环境为 macOS、OpenJDK 21.0.12.1、Node.js 26.3.0、npm 12.0.2、Docker Desktop 与 Chromium。

- `cd apps/server && ./mvnw -pl gateway-service -am test`：通过，Gateway 55 项测试，0 失败、0 错误、0 跳过。
  其中新增 4 项 controller、4 项认证恢复/并发测试，并在真实 Redis Session + HTTP 边界中证明旧 token
  首次 401 后仅 exchange 1 次、业务调用共 2 次、列表返回 200，随后 `/api/auth/session` 仍为已认证。
- `cd apps/server && ./mvnw clean verify`：通过，7 个 Reactor 模块共 130 项测试，0 失败、0 错误、1 项
  既有环境型跳过；覆盖登录授权、密码、账号持久化、资源权限、CSRF/退出相关 Gateway 行为和其它服务回归。
- `cd apps/web && npm run typecheck`：通过。
- `cd apps/web && npm run test:run`：30 个文件、109 项测试全部通过。
- `cd apps/web && npm run build`：通过；仅有既有大 chunk 提示。
- `cd apps/web && npm run test:e2e -- e2e/smoke.spec.ts --grep "lets an admin manage users|redirects a signed-in non-admin"`：
  Chromium 2 项通过，确认 ADMIN 用户页和非管理员 403 导航合同未回归。
- Security Review：对 branch changes 独立复核，未发现安全 finding。复核进程内 Mockito 无法 attach，未重复
  执行测试；上述主执行环境测试已在允许 JVM agent/Testcontainers 的环境成功运行。

AC 对应证据：正常路径不恢复；旧 token 路径只 exchange/重试一次；grant 401 逐 WebSession 失效；临时
exchange 失败保留状态；fresh token 二次 401 转 503；并发恢复只执行一次 exchange，且晚到 touch 不覆盖新 token。

## 未通过项

暂无产品或代码未通过项。第一次在文件系统沙箱内启动 Playwright preview 时，端口绑定因 `EPERM` 失败；
按相同命令在获准的本地执行环境重跑后 2 项全部通过，该失败不属于实现回归。

## 范围检查

实际业务改动只在 `gateway-service` 的认证协调和 Admin 用户 controller；测试只在同模块认证测试目录；
另更新 WORK-028 控制文档。未修改 Web 源码、contracts、user-service 实现、数据库、其它服务或设计系统，
未自动重放 POST/PATCH/密码重置。影响复核确认 Admin problems 的下游 401 已映射为 502 而非浏览器 401；
Admin 用户写操作仍不重放，符合本 WORK 的幂等边界。

## 遗留问题

Admin 用户写操作若在短 token 恰好失配时仍可能失败；本 WORK 不自动重放非幂等写请求。后续若要改善，
应设计“确认/刷新凭据但不重放原写请求”的统一 BFF 边界，不能直接复用 GET 重试。

## 剩余风险

单飞仅限单个 Gateway 进程；多实例同时遇到同一旧 token 时，每个实例最多各 exchange 一次。现有 grant
交换允许该行为且不改变授权真值，但若未来要求全局严格单次交换，应引入 Redis 级协调并重新评审失败恢复。
本地临时 RSA 密钥仍只适用于开发，生产必须继续使用显式可轮换 PEM。

## 结论

ISSUE-006#AC-001～AC-006 与 TASK-046 均有自动化、真实 Redis HTTP、跨模块和独立安全复核证据，结果通过；
按项目规则进入人工验收闸，机器结果不代替负责人签署。

## 变更记录

- 2026-09-02：状态变更：draft → review。原因：Gateway 55 项、服务端 130 项、Web 109 项、构建与 2 项 Chromium E2E 通过，提交人工验收
- 2026-09-02：验收闸通过：review → approved。原因：后台用户列表 JWT 恢复修复验收通过
