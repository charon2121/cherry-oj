---
id: "TASK-039"
type: "task"
title: "实现测试数据部署与发布就绪能力"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033", "TASK-038"]
related: []
implements: ["FEATURE-007#REQ-021", "FEATURE-007#REQ-022", "FEATURE-007#REQ-026", "FEATURE-007#REQ-027"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/backend.md", "docs/data-model.md", "docs/database-design.md", "contracts/judge.schema.json", "apps/server/pom.xml", "apps/server/TOOLCHAIN.md", "apps/server/problem-service", "apps/server/judging-service", "apps/judge-engine", "development/works/WORK-025"]
write_paths: ["apps/server/judging-service", "apps/server/TOOLCHAIN.md", "development/works/WORK-025"]
forbidden_paths: ["apps/server/gateway-service", "apps/server/user-service", "apps/server/submission-service", "apps/server/problem-service", "apps/web", "apps/judge-engine", "contracts"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-039：实现测试数据部署与发布就绪能力

## 任务目标

在 judging-service 落地当前环境的测试数据部署、手工绝对限制、参考程序验证和 readiness，使发布能够
依据真实 judge 目录与 VALID calibration，而不是默认值或人工口头确认。

## 依据

落实 FEATURE-007 REQ-021/022/026/027，遵守 judging 数据所有权和现有 judge.schema.json。只读取
problem-service 提供的受控资产流；禁止修改 Go judge、跨语言契约或 problem-service。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- judging environment V1 migration：environment/language/deployment/calibration/audit 及既有约束/索引。
- ACTIVE 环境/language 配置解析、首次显式运维 CLI 和开发 fixture；生产无默认环境，CLI 不负责环境切换。
- ADMIN JWT 保护的部署端点：expected hash/manifest + ZIP 流、临时解压校验、原子目录、幂等 READY 回执。
- 校准验证端点：显式正数 ns/bytes 限制、临时参考源码、调用 Go judge submit 模式、仅 AC 写/替换 VALID。
- readiness 只读接口和安全错误；HTTP/judge 调用在事务外，DB 状态机/审计在短事务。
- MySQL、文件、假 judge 和真实 Linux Go judge 联调测试。

## 完成标准

- [x] 空 MySQL 8.4 V1 与约束通过；唯一 ACTIVE/VALID、deployment hash 状态机和审计正确。
- [x] 相同 id/environment/hash 重试返回同一 READY；不同 hash 冲突且绝不覆盖现有目录/回执。
- [x] 解压目录与 manifest 完全一致并原子切换；恶意 ZIP/中断/磁盘满/DB 失败无半 READY。
- [x] cpuNs/memoryBytes 正整数、clockNs 可选正数；零值/溢出拒绝，不生成默认限制。
- [x] 正确参考程序 AC 后产生唯一 VALID，WA/CE/TLE/SE 不生效且不覆盖旧 VALID；源码不持久化/记录。
- [x] benchmarkSummary 只保存 sourceSha256、verdict 和有界资源摘要；不包含源码、输出或测试正文。
- [x] readiness 同时验证 ACTIVE、language、READY+matching hash、VALID；缺项返回明确 code。
- [x] USER/匿名拒绝，ADMIN actor 审计；judging 与全后端 verify、Linux 文件/真实 judge 联调通过。

## 验证

用 MySQL 8.4、临时 testdataRoot 和 fake judge 覆盖状态/并发/故障矩阵；在 Linux 运行真实 Go judge A+B
AC/WA/CE、权限和原子 rename 联调。执行 judging-service/全后端 verify、内存/句柄/敏感日志检查。

## 风险

风险是把系统故障当参考程序失败、默认限制或 DB READY 早于文件。若需要新 judge 字段、异步队列、服务
身份、多环境 UI、自动标定、修改 Go 加载规则或直接访问 problem DB，停止并升级设计。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全环境表、流式部署、校准、readiness、真实 judge 与安全验收，等待批准。
- 2026-08-30：状态变更：todo → ready。原因：源资产接口已完成，开始实现 judging-service 环境、部署、标定和 readiness
- 2026-08-30：状态变更：ready → doing。原因：已获文档批准，正在实现 judging-service 环境、部署、校准与 readiness
- 2026-08-30：完成 V1、显式环境准备、流式原子部署、参考程序校准、readiness、恢复与 ADMIN 安全边界；
  MySQL/文件/故障注入、全后端和真实 Linux Go Judge 均验证通过。
- 2026-08-30：状态变更：ready → doing。原因：已获文档批准，正在实现 judging-service 环境、部署、校准与 readiness
- 2026-08-30：状态变更：doing → done。原因：judging-service 环境、流式原子部署、校准、readiness 与恢复完成；MySQL/权限/故障注入/全后端/真实 Linux Judge 验证通过
