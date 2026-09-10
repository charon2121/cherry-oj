---
id: "TASK-097"
type: "task"
title: "接入 Linux 后端并收敛文件输出与状态"
status: "done"
work: "WORK-048"
owners: ["codex/root"]
depends_on: ["TASK-094", "TASK-096"]
related: []
implements: ["IMPROVEMENT-004#REQ-003", "IMPROVEMENT-004#REQ-004", "IMPROVEMENT-004#REQ-005", "IMPROVEMENT-004#AC-002", "IMPROVEMENT-004#AC-003", "IMPROVEMENT-004#AC-005"]
verifies: []
tags: []
read_paths: ["CLAUDE.md", "docs/engineering", "development/works/WORK-048", "apps/judge-engine/internal/sandbox", "apps/judge-engine/internal/contract", "apps/judge-engine/internal/config", "apps/judge-engine/internal/judge/flow", "contracts/run.schema.json", "apps/judge-engine/internal/sandbox/container", "apps/judge-engine/internal/sandbox/runner", "apps/judge-engine/internal/sandbox/pool", "apps/judge-engine/internal/sandbox/store", "apps/judge-engine/internal/sandbox/api", "apps/judge-engine/cmd/sandbox", "apps/judge-engine/config.example.yaml"]
write_paths: ["development/works/WORK-048", "apps/judge-engine/internal/sandbox/container", "apps/judge-engine/internal/sandbox/runner", "apps/judge-engine/internal/sandbox/pool", "apps/judge-engine/internal/sandbox/store", "apps/judge-engine/internal/sandbox/api", "apps/judge-engine/internal/config", "apps/judge-engine/cmd/sandbox", "apps/judge-engine/config.example.yaml"]
forbidden_paths: ["apps/server", "apps/web", "contracts", "apps/judge-engine/internal/judge"]
created_at: "2026-09-09"
updated_at: "2026-09-09"
---

# TASK-097：接入 Linux 后端并收敛文件输出与状态

## 任务目标

将编译与普通命令共同接入 Linux 后端，并使所有资源与平台错误可正确返回。

## 依据

front matter 的 implements 条目、DESIGN-042 冻结结果与 PLAN-032。

## 可查看范围

以 read_paths 为准；写入范围也已列入可读范围。

## 可修改范围

以 write_paths 为准。Container 使用 helper 客户端；Pool 注入后端工厂，复用容量而非历史任务 cgroup。runner 接收可信终止事实/OOM 事件，处理 Wait 和 Close/Reset 错误。配置、HTTP body、排队、输入/产物/输出和 blob 总量都设置上限，Linux 正式模式只允许硬化后端。

## 禁止修改

以 forbidden_paths 为准；不得修改现有可用判题节点、全局安全策略或无关服务。

## 依赖

以 depends_on 为准。当前为 TASK-093 新拆分的实施材料，保持 todo；用户审核并明确允许这些具体实施范围后，依次推进 ready，不从已签的只读探测授权推断编码或部署许可。

## 产出

container/runner/pool/store/api/config 及 sandbox 启动接线、配置示例和回归测试。

## 完成标准

- [x] 编译与运行均调用 Linux 后端；不按发行版静默降级；host 仅显式可信开发模式。
- [x] CPU/墙钟、OOM、OLE、取消、普通 signal、启动失败、清理失败分别有事实与确定映射。
- [x] 输出超限立即触发整组停止，控制缓冲及内联产物读取有界；blob 有总量和保留期限。
- [x] 排队和并发正数、默认值与 0 不混淆；坏槽位不可复用；shutdown 取消在途任务并执行独立清理。
- [x] 输入、产物的 symlink/magic-link/硬链接/TOCTOU、FIFO/设备拒绝覆盖；正式提交与自定义运行无需两套沙箱逻辑。

## 验证

go test -race ./internal/sandbox/... ./internal/config/...，随后全模块 go test -race ./... 和 go vet ./...；用假 helper 注入失败，真机回归交 TASK-098。

## 风险

不能用成功 exit 0 覆盖启动/计量/回收错误；若现有接口表达不足，先返回 TASK-094 补契约。

## 执行记录

- 2026-09-09：TASK-093 根据只读探针和现有源码拆分；尚未编码、测试或部署。

- 2026-09-09：用户已阅读纯 Go 材料并明确表示“没有问题，你可以开始编码”；本地代码和测试实现已授权，按依赖推进。远端安装、机器重启及现有节点切换仍按具体任务边界执行。

- 2026-09-09：用户审查要求沿用原有 sandbox 分层。接线前必须先对齐 Container 的 PutFile/Start/Wait/GetFile/Reset/Close 与 helper 生命周期，不能把一次性指定输出协议直接扩散到 runner；本轮不执行本任务。

用户已明确继续接线并允许优化Container、pool。按DESIGN-042新增接线小节实施；移除复用Reset生命周期，采用每次Container关闭与容量池，原有职责边界不变。
- 2026-09-09：状态变更：todo → ready。原因：上游TASK-094/096完成，用户明确继续接线并允许Container/pool优化，设计和路径已明确
- 2026-09-09：状态变更：ready → doing。原因：开始实现单次Container、工厂注入容量池及有界状态文件接线

本轮完成单次Container/容量池、helper客户端适配、状态和文件错误传播、API/Store/配置边界及启动拒绝；本地全模块race/vet和Linux纯Go构建通过。协议完整链使用受控Unix socket替身，真实Linux整链、安全/资源矩阵继续由TASK-098验证，未切换现有节点。证据见VERIFY-049。
- 2026-09-09：状态变更：doing → done。原因：单次Container与容量池接线、状态/文件/Store/API边界完成；全模块race、vet和Linux纯Go构建通过，整链实机验证交TASK-098
