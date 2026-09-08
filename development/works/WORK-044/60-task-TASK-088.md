---
id: "TASK-088"
type: "task"
title: "自定义运行安全与端到端验收"
status: "done"
work: "WORK-044"
owners: ["codex/root"]
depends_on: ["TASK-085", "TASK-086", "TASK-087", "PLAN-030"]
related: []
implements: ["FEATURE-012#REQ-001", "FEATURE-012#REQ-002", "FEATURE-012#REQ-003", "FEATURE-012#REQ-004", "FEATURE-012#REQ-005", "FEATURE-012#REQ-006", "FEATURE-012#REQ-007", "FEATURE-012#REQ-008", "FEATURE-012#REQ-009", "FEATURE-012#REQ-010", "FEATURE-012#REQ-011", "FEATURE-012#REQ-012", "FEATURE-012#AC-001", "FEATURE-012#AC-002", "FEATURE-012#AC-003", "FEATURE-012#AC-004", "FEATURE-012#AC-005", "FEATURE-012#AC-006", "FEATURE-012#AC-007"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs", "contracts", "development/works/WORK-044", "apps/server", "apps/judge-engine", "apps/web", "scripts", ".github/workflows"]
write_paths: ["apps/server/gateway-service/src/test", "apps/server/submission-service/src/test", "apps/server/judging-service/src/test", "apps/web/e2e", "apps/judge-engine/internal/judge/flow", "apps/judge-engine/internal/judge/api", "development/works/WORK-044"]
forbidden_paths: ["apps/server/user-service", "apps/server/identity-security-support", "apps/server/problem-service", "apps/judge-engine/internal/sandbox", "apps/web/design-system", "apps/web/src/components/ui"]
created_at: "2026-09-07"
updated_at: "2026-09-09"
---

# TASK-088：自定义运行安全与端到端验收

## 任务目标

只补充测试与验证材料（Go 范围仅 *_test.go）。生产缺陷退回对应实现任务；不借此扩大业务边界。完成实际路由检查、Linux 执行与清理、私有数据/正式提交隔离、截图八问、回退材料和人工独立复核。

## 依据

FEATURE-012、EXPERIENCE-020、DESIGN-038、DECISION-025、PLAN-030；implements 关联全部验收锚点，按上述目标分工。

## 可查看范围

以 read_paths 为准；编码前读取对应 Java/Go/TypeScript 规范与工具链。Web 另读设计系统 PROMPT。

## 可修改范围

以 write_paths 为上限，只改本任务所需文件；production 修改仅前置实现任务执行。配置只增加自测相关配置，不调整正在运行的环境。

## 禁止修改

以 forbidden_paths 为准；无数据库迁移、身份信任链修改或新 UI 原语。跨边界先修订设计和任务。

## 依赖

以 depends_on 为准；意图闸和后续实施授权前保持 todo，执行前必须 ready。

## 产出

上述实现/测试与对应 VERIFY-045 实际证据。

## 完成标准

- [x] 对应功能及边界已实现，契约与类型一致。
- [x] 正常/失败/权限/容量场景测试通过，正式提交行为不回归。
- [x] 命令、环境、结果和未执行项记录 VERIFY-045。
- [x] TASK-088 统一交付真实端到端、截图八问、独立复核和回退检查（本项在前置任务表示交接，在 TASK-088 表示完成）。

## 验证

执行 PLAN-030 的相关命令，加入真实 stdout/stderr、死循环/OLE、取消释放和自测压力下正式任务推进；用户实际环境验收必须单独记录，不能由 API fixtures 替代。

## 风险

发现需修改范围外文件、扩大限额、改变用户流程时先更新上游。测试中不打印源码、输入、输出、凭据。

## 执行记录

- 2026-09-07：完成设计回合任务拆分，尚未实施。
- 2026-09-08：状态变更：todo → ready。原因：三个实施任务完成
- 2026-09-08：状态变更：ready → doing。原因：开展Linux容器与交付验证
- 2026-09-08：状态变更：doing → blocked。原因：真实路由测试发现trial异常处理未覆盖，退回TASK-086修复
- 2026-09-08：状态变更：blocked → doing。原因：继续真实Linux及交付验证

- 2026-09-08：基础 Linux C++、正式任务共存、断连恢复及27项浏览器回归通过；大输出后空程序误判MLE（281927680 bytes），复现已写 trial_isolation_test.go。sandbox 在 forbidden_paths 内，等待范围确认；未完成真实账号联调与人工独立复核。详见 VERIFY-045。
- 2026-09-08：状态变更：doing → blocked。原因：Linux大输出后内存统计污染误判后续程序MLE；修复涉及明确禁止的sandbox路径，待用户确认范围

- 用户阅读排查后决定：暂不修复 sandbox 内存统计，留待未来 cgroup、namespace 隔离沙箱建设。保留显式失败回归与已知限制，撤销此前“必须先修复 sandbox”的阻塞；本任务剩余真实账号联调与人工独立复核，不代签验收。
- 2026-09-08：状态变更：blocked → doing。原因：用户明确暂缓sandbox修复，撤销该修复前置条件，保留已知限制并继续剩余验收

- 2026-09-09：WORK-047 完成真实运行、超时恢复与正式提交 AC（6/6），用户确认功能完成。技术交付完成；人工最终复核与已知风险接受保留给验收闸，不以 TASK done 代签。见 VERIFY-045 收束更新。

- 人工体验复核依据：用户在自行重启服务后明确确认“自定义运行终于完成了”，并要求提供全部签署命令；此为产品体验确认，不冒称独立代码审计。技术复核见 VERIFY-048，正式批准仍以用户执行验收闸为准。
- 2026-09-09：状态变更：doing → done。原因：技术验证及用户真实体验确认均已收到，正式批准由验收闸签署
