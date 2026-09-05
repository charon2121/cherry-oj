---
id: "PLAN-025"
type: "plan"
title: "分阶段重建内部身份信任链"
status: "checked"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["ISSUE-009", "DESIGN-031", "DECISION-021"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# PLAN-025：分阶段重建内部身份信任链

## 目标

把密钥生命周期、资源验签和 Gateway 凭证转发收敛成一套可部署、可观测、可轮换的系统能力。最终以
普通重启、K1→K2 完整轮换和真实 ZIP 上传证明：有效登录不会出现间歇 401/502，任何业务请求都不靠
Controller 失败重放恢复。

## 改动区域

- `apps/server/identity-security-support`：共享 claims、kid/key ring 校验、JWKS verifier 与安全分类。
- `user-service`：持久 key ring、签名/JWKS、轮换状态与 readiness。
- `problem/submission/judging-service`：迁移到共享 verifier，本地权限规则保持独立。
- `gateway-service`：统一 DelegatedIdentity、Session token 生命周期和内部请求构造。
- `scripts/`、`apps/server/TOOLCHAIN.md` 与启动配置：初始化、轮换、探针和发布顺序。

## 阶段与顺序

1. **建立基线**：补足安全失败分类和跨服务复现夹具，记录当前 active kid、各 verifier 所见 key、token
   generation 和业务调用次数；先确认故障链，不把尚未证明的密码学原因写死。
2. **统一验证基座**：新增无业务依赖的共享模块和兼容 fixture，迁移 problem/submission/judging 及
   user-service 的算法、claims、key 选择和错误分类；路径与角色授权测试留在原服务。
3. **持久化签名事实**：建立开发一次性初始化和生产 Secret key ring，内容派生 kid，加入配置校验、
   JWKS 缓存元数据、identity metadata/probe 与 user-service readiness；普通重启测试必须先通过。
4. **落地轮换协议**：实现 prepare/activate/retire 命令及门禁，演练 K1+K2 重叠、旧 token 存活窗口、
   JWKS 短暂故障和安全回退；禁止直接覆盖 active key。
5. **统一 Gateway 转发**：所有内部 client 改用同一凭证对象/请求工厂，覆盖 JSON、multipart、下载流；
   将 JWT 生命周期调整为 2 小时/提前 5 分钟续签，删除 WORK-028/030 的路由级 token recovery，统一
   身份基础设施错误映射和 Gateway readiness。
6. **迁移 fixed-absolute Session**：将 MySQL idle deadline 改为 nullable 并停止使用，保留 absolute；Gateway
   Redis TTL 只按 absolute 计算，移除 idle 延长语义，并将 `/touch` 改为只读 grant validate。
7. **系统验收**：在真实 Redis/MySQL 和四个 Java 服务上执行重启、滚动版本、三阶段轮换、并发和全量
   Maven 回归；真实 ZIP 只发送一次、problem 业务只调用一次、返回 201。
8. **独立复核与收尾**：安全复核密钥材料、授权边界、错误映射、发布/回退；验证通过后才回填 VERIFY、
   MEMORY 和相关上游文档，是否提交、推送、部署仍由用户决定。

## 并行与依赖

- TASK-066 先建立共享验证基座并迁移资源服务。
- TASK-065 在该基座上建立 user-service 持久 key ring、轮换与开发/部署工具。
- TASK-068 在签发基座稳定后迁移 MySQL/Redis 的无 idle、30 天 fixed-absolute Session 与主动撤销语义。
- TASK-067 等 TASK-065/066/068 完成后统一 Gateway 转发和 5 分钟提前续签，并移除局部恢复路径。
- VERIFY-038 依赖四个任务全部完成，不允许用单模块绿测替代系统验收。

## 迁移与交付

正式环境若已有持久 PEM，先把现有 K1 原样纳入 key ring 并发布兼容 verifier，再发布 key ring signer，
最后发布 Gateway 与删除旧路径；每一步都保留上一版本可回退。旧配置兼容读取一个发布周期，新旧同时配置
且不一致时拒绝启动。

当前本地 `generated:local` 私钥只存在进程内，无法导出时先显式初始化持久 K1，并把切换记录为一次性
开发迁移事件；旧 120 秒 JWT 按原寿命消失，新 JWT 为 2 小时。Session 迁移只延续部署时仍有效记录，
不复活已经失效或撤销的登录。此例外不能成为正式轮换方式。

## 风险

- 不把私钥、token、Cookie、grant、用户标识或 ZIP 内容写入日志、fixture、Git 和快照。
- 不在同一步同时替换 signer key、verifier 实现和 Gateway 转发；每阶段有独立探针和回退点。
- 未知 kid 且 JWKS 无法刷新时按身份基础设施不可用 fail closed；已缓存合法 key 按明确窗口继续服务。
- 共享模块只做认证，不接管业务授权；各服务的 route/role 测试是迁移门禁。
- 上传不缓存、不落盘、不自动重放；若单次正常传输仍失败，应回到信任链证据，不增加接口特例。
- 取消 idle 不使用百年 TTL 或 `0` 哨兵；Redis 存量 key、MySQL nullable idle、30 天 absolute、撤销后清理
  和回退均需真实迁移测试。每请求 validate 必须保持主动撤销即时生效。

## 验证

- 模块级：key ring 破坏性输入、kid 指纹、claims、JWKS cache/refresh、安全分类、路由权限和 Gateway
  请求构造测试。
- 跨服务：旧/新 token、2 小时 `exp`、5 分钟提前续签、普通重启、prepare/activate/retire、readiness、
  JWKS outage、无 idle/30 天 absolute Session、主动撤销和并发 Session。
- 业务级：Admin JSON 读写、multipart ZIP、streaming 下载贯穿真实 HTTP，断言请求/业务执行次数。
- 聚合与范围：按 `apps/server/TOOLCHAIN.md` 执行定向模块和 `clean verify`，再运行
  `scripts/work check`、`git diff --check` 与独立 Security Review。

## 回退

回退始终以“保留旧公钥”为前提：Gateway 可先回旧转发实现，资源服务可回兼容 verifier，user-service 可
切回仍在 key ring 的 K1。回退期间不得 retire 或删除 K1/K2，不删除 Redis Session、login grant、业务
数据或测试资产。只有超过 token TTL、clock skew 和 verifier cache safety window 并完成复核后才可清理。

## 变更记录

- 2026-09-04：初稿按 multipart 单次恢复安排实施。
- 2026-09-04：根据负责人反馈废弃路由级恢复计划，重写为三任务、七阶段的系统级信任链迁移。
- 2026-09-04：增加持久 Session 数据迁移任务，计划调整为四任务、八阶段。
- 2026-09-04：按最终时间策略把永久 Session 迁移改为取消 idle、absolute 固定 30 天。
- 2026-09-05：结构与内容校验通过，由工具置为 checked。
