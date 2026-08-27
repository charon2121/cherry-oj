---
id: "VERIFY-013"
type: "verify"
title: "建立用户身份与访问控制服务"
status: "approved"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["TASK-016", "TASK-017", "TASK-018", "TASK-019"]
related: []
implements: []
verifies: ["CAPABILITY-004", "TASK-016", "TASK-017", "TASK-018", "TASK-019"]
tags: []
result: "pass"
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# VERIFY-013：建立用户身份与访问控制服务

## 验证对象

CAPABILITY-004 全部要求、TASK-016～TASK-019、公开身份协议、user-service 数据与安全边界、Gateway
Session/CSRF、资源服务离线验权、Web 体验和一次真实跨服务身份生命周期。

## 对应要求

REQ-001～REQ-012，以及四个 TASK 的完成标准。重点证明密码/凭据不泄露、公开失败不枚举账号、Session
固定攻击不可用、CSRF 必须、JWT 不能伪造、角色不能越权、安全事件在 120 秒上限内全端失效。

## 检查与结果

验证日期为 2026-08-26。本地使用 OpenJDK 21.0.12.1、工作区 Node 24.19.0、npm 12.0.2、Docker
Engine 29.7.2、Testcontainers MySQL 8.4 与 Redis 7.4：

- `python3 scripts/contracts_test.py`：9/9 通过；公开 auth/admin 路径、CSRF、no-store、错误和敏感字段
  负面 schema 均符合 OpenAPI。Web 的生成类型在临时目录重建后无漂移。
- `cd apps/server && TESTCONTAINERS_RYUK_DISABLED=true ./mvnw clean verify`：7 个 Reactor 模块全部
  SUCCESS，共 47 个 Java 测试通过。user-service 13 个测试包含真实 MySQL/Flyway/MyBatis、Argon2、
  失败退避提交、授权摘要、JWT claim/TTL 与上一轮换公钥；Gateway 26 个测试包含真实 Redis Session、
  ID 旋转、CSRF、Cookie、单飞刷新、撤销故障和 Retry-After；三个资源服务验证固定 RS256、issuer、
  audience、exp/iat/kid/sub/role/pwd、401/403、裸头、JWKS 缓存和未知 key 故障。
- 使用项目声明范围内的 Node 24.19 执行 `npm run check && npm run build && npm run test:e2e`：format、
  lint、typecheck、36 个 Vitest/MSW/无障碍测试、生产构建和 5 条 Chromium E2E 全部通过。E2E 覆盖
  匿名首屏、外部 return path、429 恢复、重复提交、首次改密、敏感存储负例、USER 越权、ADMIN 分页/
  创建/恢复/重置、一次性临时密码和 360px 窄屏。
- 独立安全复核发现并修复五类自动化首轮未覆盖问题：失败登录事务回滚、MySQL 第 4 次提前锁定、Session
  查询不确认撤销/退出故障保留 Session、`pwd=true` 可访问资源、轮换时 user-service 只认当前公钥；
  每项均有新回归证据。429 Retry-After、密码显隐/Caps Lock、401 清理和空列表也一并补齐。
- `scripts/work check` 校验 102 份开发文档，`git diff --check` 无输出；主代码与资源目录扫描未发现
  PEM 私钥或 JWT canary。测试响应、浏览器 DOM/URL/localStorage/sessionStorage 均无密码、JWT、登录
  授权或 CSRF token 持久化。
- 跨模块证据采用真实组件边界组合：MySQL 身份生命周期、Redis Gateway HTTP Session、临时 JWKS 资源
  验权和 Chromium Web 流程分别运行，不以 mock 替代各自安全真源。problem/submission/judging 目前尚无
  WORK-002 的业务 controller/Gateway route，因此没有伪造一条不存在的“提交判题”产品链路；该业务
  E2E 仍由 WORK-002 在端点实现后验收。

## 未通过项

仓库实现与本地组件验证无未通过项。生产发布、生产 Secret/内网、HA 和线上撤销时延没有目标环境或
授权，不由本验证代签。

## 范围检查

逐 TASK 对照 `read_paths`、`write_paths` 与 `forbidden_paths`：契约/user-service、Gateway、三个资源服务
和 Web 按 TASK-016～019 分阶段落地；复核修正均回写原所属模块。未增加开放注册、第三方 IdP、完整
RBAC、用户资料、judge/sandbox、跨库访问或浏览器 JWT。

## 遗留问题

账号删除/匿名化、邮箱/短信验证、自助找回、设备管理、外部 API 客户端和即时零窗口撤销不在本工作。

## 剩余风险

即使本地验证通过，生产 Secret 管理、时钟、Redis/MySQL HA、公网限速参数、哈希容量和实际攻击流量仍需
发布环境证据；不能由仓库测试代签。

## 结论

CAPABILITY-004、EXPERIENCE-005、DECISION-009 和 TASK-016～019 的仓库内身份底座实现与本地组件验证
通过。浏览器 Session、user-service 登录授权和 120 秒内部 JWT 保持三个独立安全对象；本结论不代表
生产发布、线上观察或尚未实现的 WORK-002 题目/提交业务 E2E 已完成。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：契约、Java 聚合、Web、真实 MySQL/Redis、资源验权和安全负例证据已记录
- 2026-08-26：状态变更：review → approved。原因：全部仓库内验收通过，生产发布与 WORK-002 业务 E2E 的边界已明确保留
