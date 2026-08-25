---
id: "VERIFY-003"
type: "verify"
title: "按工作项聚合开发文档"
status: "approved"
work: "WORK-003"
owners: ["codex/root"]
depends_on: ["TASK-003"]
related: []
implements: []
verifies: ["CHANGE-002", "TASK-003"]
tags: []
result: "pass"
created_at: "2026-08-24"
updated_at: "2026-08-24"
---


# VERIFY-003：按工作项聚合开发文档

## 验证对象

单工作项目录迁移、层级文件名、`scripts/work` 的路径行为、开发规范修订及现有文档完整性。

## 对应要求

覆盖 CHANGE-002 的 REQ-001 至 REQ-009、AC-001 至 AC-005，以及 TASK-003 的全部完成标准。

## 检查与结果

环境：macOS，本地仓库根目录，Python 3.12。

- `python3 scripts/work_test.py`：通过，12 个端到端场景全部成功；覆盖新建工作、`new-doc` 共置、层级
  命名、旧类型目录与跨 WORK 拒绝、归档原地保留、引用、依赖环、状态和上下文。
- `scripts/work check`：通过，25 份开发文档无错误、无进行中提示。
- `python3 scripts/docs_test.py`：通过，53 份 Markdown 文档入口和本地链接有效。
- `git diff --check`：通过，没有空白错误。
- `python3 -m py_compile scripts/work scripts/work_test.py scripts/docs_test.py`：通过。
- 文件树与全文检索：三个 WORK 均只有一个独立目录；旧开发类型目录已删除，现行入口不再引用
  `development/tasks/` 等旧路径。

## 未通过项

暂无。

## 范围检查

变更限于 TASK-003 的 `write_paths`：开发文档、文档工具与测试、项目规则和一处全局前端规范链接。
未修改业务源码、contracts、部署配置或依赖锁文件。实现与 CHANGE-002 / DESIGN-003 一致。

## 遗留问题

暂无。

## 剩余风险

没有已知功能性风险。规模增长后，全量扫描和单 WORK 扁平目录的可用性仍需观察；当前 25 份受管理
文档下无性能或浏览问题。

## 结论

通过。新目录结构、工具行为、迁移结果和规范入口满足验收要求，可以将 TASK-003 与 WORK-003 标记为
verified。

## 变更记录

- 2026-08-24：状态变更：draft → approved。原因：11 个工具测试、25 份开发文档校验和 53 份链接检查全部通过
