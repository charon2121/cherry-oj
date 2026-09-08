---
id: "TASK-090"
type: "task"
title: "统一本地judging服务启动入口"
status: "done"
work: "WORK-045"
owners: ["codex/root"]
depends_on: ["CHANGE-011"]
related: []
implements: ["CHANGE-011#REQ-001", "CHANGE-011#REQ-002", "CHANGE-011#REQ-003", "CHANGE-011#REQ-004"]
verifies: []
tags: []
read_paths: ["apps/server", ".gitignore", "development/works/WORK-045"]
write_paths: ["apps/server/.run/JudgingServiceApplication.run.xml", "apps/server/.run/SubmissionServiceApplication.run.xml", "apps/server/.run/ProblemServiceApplication.run.xml", "apps/server/.idea/runConfigurations/Local_JudgingServiceApplication.xml", "apps/server/.idea/workspace.xml", "apps/server/.local", "apps/server/judging-service/src/main/resources/application.yaml", "apps/server/README.md", ".gitignore", "development/works/WORK-045"]
forbidden_paths: ["apps/server/judging-service/src/main/java", "apps/server/judging-service/src/test", "apps/server/identity-security-support", "apps/web", "apps/judge-engine", "contracts"]
created_at: "2026-09-08"
updated_at: "2026-09-08"
---

# TASK-090：统一本地judging服务启动入口

## 任务目标

按 CHANGE-011 统一 judging 启动入口，迁移已有本地配置且不泄露凭据。

## 依据

CHANGE-011#REQ-001 至 REQ-004。

## 可查看范围

以 front matter 为准；读取凭据只用于迁移与比较，不打印。

## 可修改范围

以 front matter 为准；workspace.xml 仅调整 judging 对应运行项及引用，不改其他 IDE 状态。

## 禁止修改

不改任何服务业务实现，不清理 Redis，不提交或推送。

## 依赖

文档审核与 WORK-045 意图闸通过后执行。

## 产出

唯一共享启动配置、被忽略的私有 properties 和备份、无秘密示例及 README。

## 完成标准

- [x] 安全备份并等值迁移五项现有参数，正确处理 properties 转义。
- [x] 共享配置设置工作目录与 additional-location，移除重复 judging 项。
- [x] 本地文件与备份被忽略，示例与跟踪文件没有真实凭据。
- [x] 记录静态校验及实际启动验证；不能启动时保留明确未验证项。

## 验证

解析 XML；比较迁移前后属性值仅输出布尔值；git check-ignore 与 git diff --check；检查运行入口唯一性、工作目录及配置文件路径。具备空闲端口及既有依赖时验证启动和内部鉴权；不为验证绕过鉴权或终止用户进程。无需重复无关业务单元测试。

## 风险

发现未预期参数或冲突时保留原数据再处理，不覆盖。私有备份放入 .local 并确认忽略。

## 执行记录

- 2026-09-08：完成只读核对，提交方案审核，尚未实施。
- 2026-09-08：状态变更：todo → ready。原因：意图闸已签署且任务边界明确
- 2026-09-08：状态变更：ready → doing。原因：开始迁移本地启动配置

- 2026-09-08：已迁移五项参数到 .local/judging-service.properties，权限 600，迁移前 XML 已备份。唯一共享入口增加工作目录及必需 additional-location；删除 Local 项与 workspace 同名重复项，其他 workspace 内容逐字保持。Spring 配置加载、参数等值、缺文件失败、唯一入口及私有文件忽略检查通过。8084 被用户进程占用，未终止或重启，真实启动与运行验收待用户重启。
- 2026-09-08：状态变更：doing → done。原因：启动配置迁移与静态及Spring加载验证完成；现有用户进程未中断，真实重启验证在VERIFY明确保留

- 2026-09-08：用户明确授权修改本机 cherry_oj_submission 的密码并写入 submission 配置。按 CHANGE-011 补充边界执行；仅更新匹配本地连接的该账号密码，保留权限；新增私有配置和共享 SubmissionServiceApplication 入口，workspace 仅移除对应重复项。
- 2026-09-08：状态变更：done → doing。原因：开始执行已授权数据库密码和配置修复

- 2026-09-08：submission 账号密码已更新、权限不变；私有配置及唯一共享入口完成。真实 Spring 配置解析及 JDBC SELECT 1 通过，密码不入库、不输出。完整服务启动仍待用户重试。
- 2026-09-08：状态变更：doing → done。原因：用户授权的数据库密码更新和submission配置完成，TCP及Spring JDBC验证通过

- 范围补充：修复自定义运行首次 503，新增 problem 共享启动项并补齐双方私有服务凭据，workspace 可调整 problem 对应重复项；先按 CHANGE-011 扩大配置边界，不改任何业务实现。
- 2026-09-08：状态变更：done → doing。原因：恢复problem和submission配套鉴权配置

- 配套修复完成：三服务 Spring 凭据解析及匹配通过；真实 judging 401 仍因当前实例未加载新参数，VERIFY 留存未通过证据，等待用户刷新 IDE 并重启，不宣称运行已恢复。
- 2026-09-08：状态变更：doing → done。原因：配套配置恢复和Spring解析检查完成，运行实例未加载新参数的验证缺口已记录

- 根据 CHANGE-011 补充自动导入本地 judging 配置，消除直接启动主类漏加载参数的问题；只写 YAML，实际运行配置探针覆盖有无 VM 参数两种方式。
- 2026-09-08：状态变更：done → doing。原因：补充无需IDE参数的本地配置加载

- judging 本地配置自动导入完成，带/不带 IDE 参数及无本地文件三种实际 Spring 配置加载检查通过。当前进程未重启，运行验收仍未通过。
- 2026-09-08：状态变更：doing → done。原因：judging自动加载修复完成，三种Spring配置场景通过，真实重启验收保留
