---
id: "DESIGN-032"
type: "design"
title: "兼容常见测试数据 ZIP 并返回可操作校验错误"
status: "checked"
work: "WORK-038"
owners: ["codex/root"]
depends_on: ["ISSUE-010"]
related: ["DESIGN-019", "FEATURE-007"]
implements: []
verifies: []
tags: []
created_at: "2026-09-05"
updated_at: "2026-09-05"
---

# DESIGN-032：兼容常见测试数据 ZIP 并返回可操作校验错误

## 背景

ISSUE-010 已用真实 `/Users/charon/Downloads/testin.zip` 和数据库失败记录确认：身份链与 multipart
传输正常，problem-service 因 Finder ZIP 中的外层目录和元数据返回 `TEST_DATA_INVALID_ZIP_ENTRY`；
Gateway 随后丢弃上游 detail。原始安全边界来自 FEATURE-007/DESIGN-019，但把“物理 ZIP 根目录”直接
等同于“逻辑测例根目录”使常见用户操作无法使用。

## 目标与限制

- 保持 `<case>.in/.out` 成对、普通 UTF-8 文本、安全 case 名的逻辑数据模型。
- 保持原 ZIP 不可变存储、原字节 hash/下载、流式上传以及部署前二次校验。
- 只兼容一个可选外层目录和明确列出的 macOS 元数据，不接受任意目录树或任意隐藏文件。
- 忽略项永不解压、不进 manifest；ZIP 总大小、条目数和 central-directory 解析仍受限。
- 不改数据库、公开成功 DTO、Session/身份架构、Web 组件或 judge-engine。

## 整体方案

校验器先把每个 ZIP entry 分类为 `CASE_FILE`、`IGNORABLE_METADATA`、`DIRECTORY_MARKER` 或
`INVALID`，再从所有 case file 推导唯一逻辑根：

1. case file 要么全部直接位于根目录，要么全部位于同一个安全外层目录，不能混用；
2. 去掉可选外层目录后，文件名仍须匹配现有 ASCII `<case>.in|out` 规则；
3. `.DS_Store`、`__MACOSX/**` 与 `._*` 仅作为已知元数据跳过，绝不打开其 entry stream；
4. 目录 entry 只有在它是推导出的外层目录或已知元数据目录时才合法；任何测例多层目录、绝对路径、
   `.`/`..`、反斜杠、NUL、软链接和重复逻辑名均拒绝；
5. manifest 使用归一后的逻辑名。原始 ZIP hash 与下载字节保持不变。

problem-service 和 judging-service 分别在自己的存储边界实现该小型归一化器，并用同一组场景对齐；
不新增跨服务运行时共享业务模块。judging-service 根据逻辑名查 manifest 并写入受控根目录。

## 模块与数据

- `problem-service/FileTestDataAssetStore`：分类条目、推导逻辑根、计算 case 文件摘要并生成逻辑 manifest；
  `TestDataService` 把内部失败码映射为可操作 detail。
- `judging-service/FileTestDataDeploymentStore`：重复相同归一化和安全检查，按逻辑名核对 manifest，仅解压
  case files；这是对源资产与部署目录之间事实链的二次验证。
- `gateway-service/ProblemServiceClient`：读取受信 problem-service 的 `detail`，做长度/控制字符兜底；
  `ProblemApiErrors` 只在 code 属于既有安全 allowlist 时向浏览器保留它。
- `contracts/web-api.openapi.json`：把“ZIP 根目录只允许”改成逻辑根语义，并说明单一外层目录与已知
  macOS 元数据兼容行为。没有表结构和存量行迁移。

## 接口与状态

成功响应、`TestDataVersion`、manifest 结构及 HTTP 201 不变。失败继续使用现有 HTTP 413/422 和稳定
`INVALID_TEST_DATA_ARCHIVE` / `PAYLOAD_TOO_LARGE` code；本次只让 422 detail 表达具体可修复原因。
Gateway 不新增字段，`meta.requestId` 继续由公开请求统一生成。

## 安全与失败

兼容不等于任意解压：只有 case file 会被读取/摘要/部署，元数据只跳过；所有 entry（包括目录和忽略项）
都计入 `maxFiles`，整个 ZIP 仍受 `maxArchiveSize`，case file 仍受单文件、总展开量、压缩率和 UTF-8
限制。逻辑名去重发生在去前缀之后，防止两个物理路径覆盖同一目标。任何歧义都返回 422，并沿用现有
临时文件清理与 FAILED 状态。错误 detail 只用固定文案，不拼接 entry 名、正文或底层异常。

## 监控与部署

现有 requestId 足以关联公开错误、FAILED 数据行和审计。无需新增指标或发布步骤；服务按
problem-service → judging-service → Gateway 任意顺序滚动均可，因为旧平面 ZIP 始终兼容，而新形态只会
在 problem-service 新版本接受后产生。部署新形态前 judging-service 必须已升级，因此建议先升级
judging-service，再升级 problem-service 和 Gateway。

## 迁移与兼容

已有平面 ZIP、manifest 和 READY/已部署目录不变。新上传的包装目录 ZIP 保存原始字节，但 manifest 使用
逻辑根文件名；升级后的 judging-service 能从原包还原同一逻辑内容。若回退 problem-service，不影响已存
数据的读取/下载；若需要部署新形态资产，必须保留新版 judging-service 或重新上传平面 ZIP。

## 备选方案

1. **只在页面提示用户重新打包**：改动最小，但 Finder 的默认操作持续制造失败，而且服务端依旧只能给
   泛化错误；不选。
2. **服务端重写为标准平面 ZIP**：可让下游简单，但会破坏“下载原始包/原始 hash”的既有承诺并增加
   二次压缩成本；不选。
3. **接受任意目录并递归寻找 `.in/.out`**：用户最宽松，但多个目录出现同名 case 时语义不确定，也扩大
   路径攻击面；不选。

## 风险与重审条件

最大风险是 problem/judging 两处规则漂移，因此验收必须用同一物理 ZIP 和 manifest 做跨服务部署测试，
并覆盖去前缀后的重复名。若未来需要多组数据目录、非文本文件、checker 资源或 Windows/macOS 之外的
更多打包器元数据，应重新定义测试数据包 manifest，而不是继续向忽略名单无限加例外。

## 变更记录

- 2026-09-05：状态变更：draft → review。原因：单外层目录归一化、macOS 元数据忽略、双服务一致校验和错误 detail 方案已完成，提交意图审核
- 2026-09-05：结构与内容校验通过，由工具置为 checked。
