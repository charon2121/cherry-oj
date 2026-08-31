---
id: "TASK-038"
type: "task"
title: "实现测试数据资产版本管理"
status: "done"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["FEATURE-007", "EXPERIENCE-013", "DESIGN-019", "DECISION-014", "PLAN-015", "TASK-033", "TASK-037"]
related: []
implements: ["FEATURE-007#REQ-018", "FEATURE-007#REQ-019", "FEATURE-007#REQ-020", "FEATURE-007#REQ-026", "FEATURE-007#REQ-027"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/data-model.md", "docs/database-design.md", "contracts/web-api.openapi.json", "apps/server/problem-service", "development/works/WORK-025"]
write_paths: ["apps/server/problem-service", "apps/server/TOOLCHAIN.md", "development/works/WORK-025"]
forbidden_paths: ["apps/server/gateway-service", "apps/server/user-service", "apps/server/submission-service", "apps/server/judging-service", "apps/web", "apps/judge-engine"]
created_at: "2026-08-30"
updated_at: "2026-08-30"
---




# TASK-038：实现测试数据资产版本管理

## 任务目标

在 problem-service 实现不可变测试数据源资产：安全流式接收 ZIP、校验 `.in/.out`、生成 hash/manifest、
原子封存、列表/下载/绑定，并让失败与崩溃可恢复。

## 依据

落实 FEATURE-007 REQ-018～020/026/027 和 DECISION-014 文件存储选择；不写 judge 目录、不实现部署或
校准，不把 storageRef/正文暴露到浏览器 DTO。

## 可查看范围

以 front matter 的 `read_paths` 为准。

## 可修改范围

以 front matter 的 `write_paths` 为准。

## 禁止修改

以 front matter 的 `forbidden_paths` 为准。

## 依赖

以 front matter 的 `depends_on` 为准。

## 产出

- `TestDataAssetStore` 消费方接口、私有文件实现、配置校验、权限和启动恢复。
- multipart 内部上传端点及 UPLOADING→READY/FAILED 应用流程；ZIP 安全验证、包/文件 SHA-256、manifest。
- ADMIN 元信息列表、原包流式下载、READY 同题绑定；受控 asset stream 供 TASK-039 部署调用。
- 上传内容 hash 查重、取消/断流/磁盘满/DB 故障清理，结构化安全错误。
- ZIP fuzz/zip-slip/bomb、权限、原子性、crash recovery、敏感日志与 MySQL 集成测试。

## 完成标准

- [x] 合法平面 ZIP 得到 READY、正确 caseCount/totalBytes/包与文件 hash，下载逐字节一致且可重复。
- [x] 目录/symlink/父路径/重复/孤儿/NUL/非 UTF-8/超限/损坏/高压缩比全部拒绝且无 READY/残留临时资产。
- [x] READY ZIP/metadata 不可覆盖；同题相同内容 hash 在 finalize 行锁内复用既有 READY，不产生第二份资产。
- [x] DB/file 任一步失败保持可解释状态；启动只清配置根内过期临时/孤儿，不触碰 READY。
- [x] 仅 ADMIN 可读元信息/下载/绑定，storageRef、正文和标准答案不进 JSON/日志/审计。
- [x] 下载/asset stream 不整包进内存，取消后句柄关闭；测试和全后端 verify 通过。

## 验证

在临时文件根与 MySQL 8.4 运行表驱动/属性测试，覆盖所有恶意 ZIP、限额边界、并发同键、磁盘/DB 注入
故障、重启恢复、权限、下载中断和 canary；记录最大内存/句柄行为，执行 problem-service 与全量 verify。

## 风险

ZIP 与删除路径是高危边界；任何清理必须从已校验配置根和 DB 状态解析，禁止宽泛递归。若文件系统不
支持同盘原子 rename、需要对象存储/断点续传/后台扫描或 READY 删除，停止并重审。

## 执行记录

- 2026-08-30：创建任务。
- 2026-08-30：补全源资产存储、ZIP 安全、状态一致性、流式和恢复验收，等待批准。
- 2026-08-30：状态变更：todo → ready。原因：TASK-037 已完成，开始实现不可变测试数据资产上传、校验、下载与绑定
- 2026-08-30：状态变更：ready → doing。原因：私有测试数据存储、ZIP 安全校验、不可变封存、去重、下载、绑定和恢复已实现，进入完整验证
- 2026-08-30：状态变更：doing → done。原因：测试数据 ZIP 安全流式上传、不可变封存、并发去重、下载、绑定和崩溃恢复完成；文件/MySQL/权限及全后端验证通过
