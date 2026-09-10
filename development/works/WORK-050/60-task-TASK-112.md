---
id: "TASK-112"
type: "task"
title: "自动验证新节点校准与真实业务闭环"
status: "todo"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-111"]
related: []
implements: ["CAPABILITY-008#REQ-004", "CAPABILITY-008#REQ-005", "CAPABILITY-008#REQ-006", "CAPABILITY-008#AC-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "development/works/WORK-050", "docs/engineering", ".github/workflows", "apps/judge-engine", "deploy/sandbox-linux", "contracts", "apps/server", "apps/web", "scripts/identity-keys", "deploy/backend"]
write_paths: ["development/works/WORK-050", "deploy/sandbox-linux/ci", "apps/web/e2e-live", "apps/web/playwright.live.config.ts", ".github/workflows/ci.yml"]
forbidden_paths: ["apps/judge-engine/internal", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "contracts", "compose.yaml", "compose.legacy.yaml", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md", "apps/server", "apps/web/src", "apps/web/e2e", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/playwright.config.ts", "apps/web/vite.config.ts"]
created_at: "2026-09-10"
updated_at: "2026-09-10"
---

# TASK-112：自动验证新节点校准与真实业务闭环

## 任务目标

每轮从空白独立后端和新Linux节点完成数据部署/校准/发布到真实页面运行与正式提交。

## 依据

CAPABILITY-008 REQ-004/005/006与AC-004，WORK-048/TASK-100，DESIGN-044业务环境与数据规则。

## 可查看范围

以 front matter 的 read_paths 为准；已有实现只读用于核对不变量。

## 可修改范围

以 write_paths 为准。仅测试编排、工作流、报告和用例适配，不实现另一套沙箱或业务服务。远端动作仅指审核后的一次性 GitHub VM，不能连接现有服务器或 IDEA。

## 禁止修改

以 forbidden_paths 为准。禁止修改生产代码、协议、限额/权限来迁就测试；禁止使用旧私有配置、数据和凭据；不得清理未知所有权资源。发现真实缺陷先记录并另拆修复边界。

## 依赖

依 depends_on 顺序推进；本轮只是文档，需人工意图闸及后续实施授权，当前 todo 不可直接执行。

## 产出

ci内独立Java/MySQL/Redis/Kafka和原生节点编排、合成6对数据及业务准备/只读取证；apps/web/e2e-live与playwright.live.config.ts中的真实浏览器用例；业务job报告。复用现有依赖，不新增前端包或改生产代码。

## 完成标准

- [ ] 空白四业务库/Redis/Kafka、五真实Java服务及原生Linux节点使用运行时凭据和独占资源，不依赖IDEA或旧ZIP。
- [ ] 正常bootstrap/登录/CSRF、节点注册与环境选用、6对数据上传/部署、独立校准及发布通过；不直接写业务表。
- [ ] 8类自定义覆盖stdout/stderr、CE/RE/普通SIGKILL、CPU/MLE/OLE及紧接空程序；CPU运行墙钟与HTTP总耗时分开取证。
- [ ] 正式AC 6/6和WA走真实Kafka链，确认版本/环境/数据/校准关联，代码回看与编辑草稿保持正确。
- [ ] 页面测试禁止route.fulfill/模拟后端；审计和请求ID来自本轮，报告不包含凭据/会话。
- [ ] 最终无任务进程/cgroup/挂载/工作文件残留；全栈和独占卷退出清理，正式提交不自动重试掩盖失败。

## 验证

本地验证夹具ZIP结构、配置生成和有界请求/清理编排；一次性GitHub VM实际启动完整栈，逐case跑真实页面并核对后台资源事实。原生/内核层失败不得在业务层换成trusted-host；必须记录当前SHA和新身份。

## 风险

旧node-e2e不足以覆盖现有Kafka与Linux链；避免复制硬编码ID和管理员凭据。不得借自动化增加Java测试后门或忽略校准/发布门禁。

## 执行记录

- 2026-09-10：确认现有页面用例模拟响应、TASK-100夹具依赖手工环境；本轮仅设计。
