---
id: "TASK-065"
type: "task"
title: "建立持久化身份密钥环与受控轮换"
status: "done"
work: "WORK-037"
owners: ["codex/root"]
depends_on: ["ISSUE-009", "DESIGN-031", "DECISION-021", "PLAN-025", "TASK-066"]
related: []
implements: ["ISSUE-009#AC-001", "ISSUE-009#AC-002", "ISSUE-009#AC-003", "ISSUE-009#AC-004", "ISSUE-009#AC-007", "ISSUE-009#AC-008", "ISSUE-009#AC-009", "ISSUE-009#AC-010", "ISSUE-009#AC-011"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering/README.md", "docs/engineering/java.md", "apps/server/TOOLCHAIN.md", "apps/server/pom.xml", "apps/server/identity-security-support", "apps/server/user-service", "scripts", ".gitignore", "development/works/WORK-013", "development/works/WORK-025", "development/works/WORK-037"]
write_paths: ["apps/server/user-service/pom.xml", "apps/server/user-service/src/main/java/com/cherryoj/userservice/security", "apps/server/user-service/src/main/java/com/cherryoj/userservice/config", "apps/server/user-service/src/main/java/com/cherryoj/userservice/api/JwksController.java", "apps/server/user-service/src/test", "apps/server/user-service/src/main/resources", "scripts/identity-keys", "apps/server/TOOLCHAIN.md", ".gitignore", "development/works/WORK-037"]
forbidden_paths: ["contracts", "apps/web", "apps/judge-engine", "apps/server/gateway-service", "apps/server/problem-service", "apps/server/submission-service", "apps/server/judging-service", "apps/server/user-service/src/main/java/com/cherryoj/userservice/application", "apps/server/user-service/src/main/java/com/cherryoj/userservice/persistence", "apps/server/user-service/src/main/resources/db", "database migrations"]
created_at: "2026-09-04"
updated_at: "2026-09-05"
---

# TASK-065：建立持久化身份密钥环与受控轮换

## 任务目标

把 user-service 的进程内临时签名 key 改为显式持久 key ring，建立内容派生 kid、严格加载校验、
prepare/activate/retire 轮换协议和 readiness，使普通重启不再改变信任事实。

## 依据

实现 `ISSUE-009#AC-001`～`#AC-004`、`#AC-007`～`#AC-010` 中签发方和 key lifecycle 部分。实现必须
复用 TASK-066 的共享 primitives，并遵循 DECISION-021 的分阶段迁移与保留 K1 回退原则。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准；必须先完成 TASK-066，避免 signer 与 verifier 分别定义 kid/claims。

## 产出

- key ring loader、active signer、published verification keys、确定排序的 JWKS/ETag/cache metadata。
- 公私钥匹配、内容派生 kid、算法/强度、权限、符号链接、重复/冲突配置的 fail-fast 校验。
- 开发一次性原子初始化与 prepare/activate/retire 检查命令；生产 Secret 只读加载约定。
- 只暴露非敏感 generation/kids/TTL 的 identity probe 和 user-service readiness。
- 旧配置一个发布周期的兼容读取、弃用告警与无数据删除的回退说明。

## 完成标准

- [x] 同一开发环境连续重启的 active public key/kid 不变，重启前未过期 token 和重启后 token 均可验证。
- [x] user-service 签发 JWT 的 `exp-iat` 固定为 2 小时，并允许 Gateway 提前 5 分钟续签的配置组合。
- [x] `generated:local` 和固定 `local-ephemeral` 不再是普通 server 启动默认。
- [x] 同 kid/不同 key、公私钥不匹配、重复 key、缺失 active、弱 key 和不安全权限均拒绝启动/轮换。
- [x] 私钥永不进入 JWKS、日志、Git、manifest 或测试快照；初始化不覆盖已有文件。
- [x] K1→K2 的 prepare/activate/等待/retire 不能跳阶段或提前退休，并保留明确回退 key。
- [x] 旧配置兼容和当前本地不可导出 ephemeral K1 的一次性迁移行为均有自动化证据。

## 验证

实施前运行 `scripts/work context TASK-065`。执行 key ring 单元/权限/损坏输入、user-service 启动、连续重启、
轮换时钟和 JWKS 合约测试；再按 TOOLCHAIN 运行服务聚合验证，结果写入 VERIFY-038。

## 风险

错误轮换可使所有服务拒绝。命令必须默认只检查、显式推进、原子写 manifest，且绝不覆盖或删除 key。
若 Secret 平台需要外部变更或当前正式 K1 无法读取，应停在迁移计划，不擅自生成生产密钥或执行轮换。

## 执行记录

- 2026-09-04：创建任务。
- 2026-09-04：由上传局部恢复任务重写为签名密钥生命周期任务。
- 2026-09-04：纳入 2 小时 JWT 签发与配置不变量。
- 2026-09-05：状态变更：todo → ready。原因：TASK-066 已完成，开始持久化签名密钥环实施准备
- 2026-09-05：状态变更：ready → doing。原因：开始实施持久化签名密钥环、重启稳定性与轮换重叠窗口
- 2026-09-05：状态变更：doing → done。原因：持久开发密钥、内容派生 kid、严格加载校验、JWKS 缓存元数据、readiness 与 prepare/activate/retire 门禁已实现并通过定向测试
