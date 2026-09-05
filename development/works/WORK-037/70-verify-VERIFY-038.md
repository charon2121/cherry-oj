---
id: "VERIFY-038"
type: "verify"
title: "验证内部身份信任链重构"
status: "approved"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["TASK-065", "TASK-066", "TASK-067", "TASK-068"]
related: []
implements: []
verifies: ["ISSUE-009#AC-001", "ISSUE-009#AC-002", "ISSUE-009#AC-003", "ISSUE-009#AC-004", "ISSUE-009#AC-005", "ISSUE-009#AC-006", "ISSUE-009#AC-007", "ISSUE-009#AC-008", "ISSUE-009#AC-009", "ISSUE-009#AC-010", "ISSUE-009#AC-011", "ISSUE-009#AC-012", "TASK-065", "TASK-066", "TASK-067", "TASK-068"]
tags: []
result: "pass"
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# VERIFY-038：验证内部身份信任链重构

## 验证对象

验证持久 key ring、受控轮换、共享 verifier、Gateway 统一转发和部署/readiness 门禁形成一条完整信任链。
ZIP 上传只是端到端验收之一，不能用单一路由成功代替系统验证。

## 对应要求

覆盖 `ISSUE-009#AC-001`～`#AC-012`：普通重启、key/kid 约束、K1→K2 轮换、四服务一致验签、全部
Gateway client 统一转发、ZIP 单次传输、安全失败分类、JWKS 故障/readiness、2 小时 JWT、5 分钟提前
续签、无 idle/30 天 absolute Session、主动撤销、兼容回退和全量回归。

## 验证矩阵

| 层级 | 场景 | 必须证明 |
|---|---|---|
| key ring | 初始化、重启、损坏输入、权限、K1→K2 | key/kid 稳定，非法状态 fail fast，轮换不可跳阶段 |
| verifier | token corpus、unknown kid、JWKS outage | 四服务结论一致，缓存窗口明确，失败分类准确 |
| Gateway | JSON、multipart、streaming、并发 Session | 统一注入，无业务 401 重放，错误映射一致 |
| 系统 | Redis/MySQL、滚动重启、完整轮换 | 有效登录不出现间歇 401/502，readiness 阻断未就绪实例 |
| 上传 | 真实 ZIP 经浏览器 API 到 problem-service | 网络发送一次、业务执行一次、201，资产内容不变 |
| 会话 | 旧 30 分钟边界、30 天 absolute、Redis TTL、MySQL deadline | idle 不再失效，absolute 不滑动，撤销即时生效 |
| 续签 | 2 小时 JWT、剩余 5 分钟、并发请求 | 只 exchange 一次，新 token 写回后再发送业务请求 |
| 安全 | 日志、配置、Git、回退 | 无敏感材料，授权未放宽，K1/K2 可安全回退 |

## 检查与结果

- `./mvnw clean verify`：8 个 reactor 模块全量构建成功，137 个测试通过、0 失败、1 个仅 Linux 支持的
  `RealLinuxJudgeIntegrationTests` 按平台跳过；总耗时 1 分 42 秒。真实 Redis、MySQL 8.4/Flyway、资源
  JWKS、Gateway multipart/ZIP 单次发送和 30 天 Session 场景均执行。
- `bash -n scripts/identity-keys`：通过。临时 3072-bit K1→K2 演练证明：缺少 judging-service 时 probe
  直接拒绝；三个具名、不同 readiness URL 同时为 UP 且发布 K2 后才能 activate；activate 后立即 retire
  被 7530 秒安全窗口拒绝。
- `git diff --check`：通过；结构扫描确认 Gateway 主代码只有 `InternalRequestFactory` 注入 Bearer，业务
  Controller/client 中不再存在 `readWithRecovery` 或 token rejection 重放。

## 未通过项

无自动化或轮换演练失败项。仅 Linux judge 集成测试的平台跳过不属于本次身份改动失败。

## 范围检查

实施分别在执行 `scripts/work context TASK-065/066/067/068` 后按任务推进。共享模块、父 POM、服务依赖、
Gateway、user-service、三个资源服务、迁移、运维脚本和 WORK-037 验证文档均属于四个任务合并后的声明
边界；未修改 contracts、Web UI 或 judge-engine。

## 遗留问题

仓库实现与本机集成验证已完成。生产 Secret 挂载、现有 K1 导入、TLS 端点和滚动发布仍需在真实部署环境
按 TOOLCHAIN 的 prepare→三服务 probe→activate→等待→retire 顺序执行，仓库测试不伪造这部分环境事实。

## 剩余风险

30 天 Cookie 扩大了凭据被窃后的最长窗口，因此生产 profile 默认 `Secure`，同时保留 HttpOnly、SameSite、
CSRF、每请求 grant validate 与主动撤销。独立 Security Review 提出的强制内部 TLS、公钥 owner、decoder
challenge 和配置不可覆盖属于更强威胁模型；负责人明确选择可信内网模型，本工作不继续追加这些复杂度。

## 结论

通过。原始 502 的身份一致性根因已由持久 key ring、共享 verifier、统一 Gateway 转发和轻量轮换门禁
系统性消除；JWT/Session 时间策略与主动撤销语义符合负责人确认值，不依赖上传重试。

## 变更记录

- 2026-09-05：状态变更：draft → review。原因：完整回归与轻量三服务轮换演练通过，结论已记录，提交负责人验收
- 2026-09-05：验收闸通过：review → approved。原因：确认身份架构重构、时间策略和可信内网简化方案符合要求
