---
id: "TASK-112"
type: "task"
title: "自动验证新节点校准与真实业务闭环"
status: "doing"
work: "WORK-050"
owners: ["codex/root"]
depends_on: ["TASK-111", "TASK-116"]
related: []
implements: ["CAPABILITY-008#REQ-004", "CAPABILITY-008#REQ-005", "CAPABILITY-008#REQ-006", "CAPABILITY-008#AC-004"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "AGENTS.md", "development/README.md", "development/works/WORK-048", "development/works/WORK-049", "development/works/WORK-050", "docs/engineering", ".github/workflows", "apps/judge-engine", "deploy/sandbox-linux", "contracts", "apps/server", "apps/web", "scripts/identity-keys", "deploy/backend", "development/works/WORK-052", "development/works/WORK-053", "compose.yaml", "development/works/WORK-054"]
write_paths: ["development/works/WORK-050", "deploy/sandbox-linux/ci", "apps/web/e2e-live", "apps/web/playwright.live.config.ts", "apps/web/tsconfig.node.json", "apps/web/eslint.config.js", ".github/workflows/ci.yml", "development/works/WORK-052", "development/works/WORK-053", "development/works/WORK-054"]
forbidden_paths: ["apps/judge-engine/internal", "apps/judge-engine/cmd", "apps/judge-engine/go.mod", "apps/judge-engine/go.sum", "contracts", "compose.yaml", "compose.legacy.yaml", "deploy/backend", "development/works/WORK-048", "AGETNTS.local.md", "apps/server", "apps/web/src", "apps/web/e2e", "apps/web/package.json", "apps/web/package-lock.json", "apps/web/playwright.config.ts", "apps/web/vite.config.ts"]
created_at: "2026-09-10"
updated_at: "2026-09-12"
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

WORK-050意图闸及实施授权已存在；TASK-111/116已完成，WORK-052验收已由用户签署，本轮明确开始TASK-112。只推进本任务，不启动WORK-049。

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

- 2026-09-11：按PLAN-034的新内核通信失败分支增加TASK-116依赖。TASK-111原生10项已通过，但必须先完成WORK-052的EINTR修复及验收交回，不能用后一轮偶然全绿关闭已观察失败。TASK-112保持todo，未实施。

- 2026-09-11：核验WORK-052人工验收passed，按PLAN补充只读依据与工具交回路径后开始实施；用户签闸产生的未提交记录与原ZIP保留。
- 2026-09-11：状态变更：todo → ready。原因：WORK-050意图与实施授权存在，TASK-111/116完成且WORK-052已由用户验收；用户明确开始真实业务CI
- 2026-09-11：状态变更：ready → doing。原因：开始独立全栈准备、真实浏览器业务断言与所有权清理
- 2026-09-11：完成业务job、全栈所有权编排、正常API准备与只读取证、11项真实页面断言和失败回收接入；本地74项基础单测、Node24类型/lint、Playwright发现及actionlint通过。真实VM、独立复核与本批提交推送尚未执行，完成标准保持未勾选；详见VERIFY-051本轮记录。
- 2026-09-11：用户明确授权本批独立复核、修正后commit/push到origin/main并运行和处理GitHub CI；开始只读独立复核和发布核对，授权不涉及现有服务器、生产代码或WORK-049重构。

- 2026-09-11：按用户授权完成只读独立复核，修正bootstrap生命周期与Action固定SHA后提交7c8f9a2、诊断3c0b0cc并推送main。两轮其余9job成功，业务首次改密503；诊断确认身份配置不一致，全部资源清理通过。WORK-053已形成独立最小修复材料待人工闸，不扩大本任务Java权限；完成标准仍不勾选。
- 2026-09-11：核验WORK-053验收passed并刷新verified，恢复本任务。31b4019真实登录/改密/重登及部署校准发布已通过；浏览器退出1，先按DESIGN-044补固定阶段/源码位置诊断，再在独立VM继续原断言。现有TASK-112独立复核、修正后提交推送及CI授权持续有效，生产写边界不扩大。

- 2026-09-11：浏览器诊断提交3a74fc7，本地82基础检查、Web168测试与构建、独立复核通过。CI34571258539的两次尝试中，三个Linux job均在锁定rootfs下载TLS握手超时，浏览器诊断尚未实跑；记录外部环境阻断并保留原业务失败，不连续盲目重跑或改变软件包来源。
- 2026-09-11：状态变更：doing → blocked。原因：CI34571258539两次尝试的三个Linux VM均在rootfs锁定包TLS握手超时，未进入浏览器；需下载恢复或另行确认准备方案，保留原断言与所有失败
- 2026-09-11：状态变更：blocked → doing。原因：用户要求继续；来源HTTPS只读可达，正在同SHA新VM核验下载恢复并继续浏览器诊断，已有失败保留

- 2026-09-11：第三轮同SHA失败job仍为下载TLS超时。本地只读DNS端点对照有一超时五成功，未确认GitHub失败peer；按PLAN仅新增WORK-054修复文档，意图闸待用户，不扩大本任务下载器写权限。
- 2026-09-11：状态变更：doing → blocked。原因：第三轮Linux准备仍TLS超时，完成只读端点对照并形成WORK-054有限同源连接回退材料；待人工意图闸和实施许可

- 2026-09-11：WORK-054修复68c91d4已授权推送；CI34580313653原生全部56包及10项通过，但kernel/business仍在240秒下载总期限终止（8job通过、2失败）。真实浏览器诊断仍未运行，TASK-112保持blocked；连接回退已生效但不足以全面恢复，后续先重审有界逐包诊断方案，见WORK-054/VERIFY-055。

- 2026-09-11：WORK-054诊断d001f03/CI34582727482：kernel全56包及63项通过，native仅GCC包正文未完成导致下载240秒终止；business本轮全56包、真实MySQL认证8项、环境/部署/校准通过，浏览器io失败现有诊断support.ts:45:24，11项未运行；finally完整回收确认。后续TASK-112需核对该源码位置与失败原因，本轮未改apps；完整CI仍8PASS/2FAIL，基线未冻结。

- 2026-09-11：WORK-054方案1 d795c7f两轮CI34584984978/34586094583：6VM下载4完整、2在600秒仍超时，试验未恢复稳定下载。business两轮均部署/校准通过、io support.ts:45:24失败，最终清理确认。首轮kernel在static-identity读取/proc/8376/ns/mnt时FileNotFoundError（6PASS/4FAIL/53NOT_RUN）；第二轮63项通过不能覆盖首轮失败，需在后续测试边界调查进程采样原因。本批未修改相关源码，TASK-112未完成、重构基线未冻结。

- 2026-09-12：WORK-057人工验收通过，认证两轮8/8通过，恢复本任务。首个io尚未发送运行请求，即support.ts等待编辑器可见失败。先按DESIGN-044追加固定计数/布尔/HTTP状态诊断，保持隐私白名单与测试断言；沿本任务已有独立复核、提交推送和CI授权，未扩大生产写权限。
- 2026-09-12：状态变更：blocked → doing。原因：下载与认证阻断已解除且WORK-057已验收，继续已批准的真实浏览器诊断
