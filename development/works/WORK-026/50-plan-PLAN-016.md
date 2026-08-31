---
id: "PLAN-016"
type: "plan"
title: "为 Java 服务提供可直接启动的本地默认配置"
status: "approved"
work: "WORK-026"
owners: ["codex/root"]
depends_on: ["CAPABILITY-007", "EXPERIENCE-014", "DESIGN-020", "DECISION-015"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-31"
updated_at: "2026-08-31"
---


# PLAN-016：为 Java 服务提供可直接启动的本地默认配置

## 目标

按 CAPABILITY-007 和 DESIGN-020 补齐本地启动必需默认、生产安全分支、测试与启动说明，不改公开接口
或数据结构。

## 改动区域

- 五服务 `application*.yaml` 的环境变量分类与必要默认。
- user-service TokenConfig/配置类的本地临时 RSA 分支及测试。
- judging-service 数据源默认与应用上下文测试。
- 五服务配置扫描/聚合回归，以及 `apps/server/README.md`、`TOOLCHAIN.md`。

## 阶段与顺序

1. 固化配置清单和可空白名单测试，先让测试准确描述当前缺口。
2. 补 judging 数据库本地默认，并验证环境变量覆盖。
3. 实现 user-service `generated:local` 随机密钥与 production 拒绝逻辑，覆盖 JWKS/签发和负向场景。
4. 运行五模块上下文与聚合验证，更新本地/生产启动说明并复核敏感内容。

## 并行与依赖

数据库默认与 RSA 分支可分别实现，但配置扫描测试依赖最终清单；聚合验证最后串行执行。WORK-025
正在修改 problem/judging 配置，实施时必须基于现有工作树做增量并保留其内容。

## 迁移与上线

无数据迁移。仓库实现完成后，本地开发可立即使用；生产发布必须先确认 active production profile、
数据库 Secret、稳定 PEM 与文件权限，再滚动重启。未给出部署目标和授权时不执行生产上线。

## 风险

主要风险为覆盖 WORK-025 未提交配置、把个人密码写入文档/日志、生产误用临时密钥和测试把有效空值
误报。实施前后都执行路径 diff、敏感模式扫描和 production 缺失配置负向测试。

## 验证

模块级验证 user-service 随机/显式/production 三条路径和 judging 默认/覆盖；配置扫描验证五个
application 文件；随后从 `apps/server` 执行 `./mvnw clean verify`。补充无 `CHERRY_*` 的实际启动
smoke（外部基础设施可用时）以及 `scripts/work check`、`git diff --check` 和 Secret 扫描。

## 回退

删除新增默认、随机密钥分支和测试，恢复原先必须 export 的配置；数据库与接口无变化。生产若已发布，
保留原 Secret 环境变量即可安全回退，现有显式路径不经过新分支。

## 变更记录

- 2026-08-31：状态进入 review；形成配置盘点、分支实现、跨服务验证和生产发布前置检查计划。
- 2026-08-31：状态变更：review → approved。原因：负责人确认实施顺序、验证范围、回退和 WORK-025 改动保护要求
