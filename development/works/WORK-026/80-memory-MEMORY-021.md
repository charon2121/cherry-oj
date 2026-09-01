---
id: "MEMORY-021"
type: "memory"
title: "为 Java 服务提供可直接启动的本地默认配置"
status: "checked"
work: "WORK-026"
owners: ["codex/root"]
depends_on: ["VERIFY-026"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-31"
updated_at: "2026-09-01"
---

# MEMORY-021：为 Java 服务提供可直接启动的本地默认配置

## 背景

本地启动便利与生产 Secret 安全是两条不同约束。application 中的空字符串不都代表缺陷：有些是有效
的“无密码/功能关闭”，有些变量则在缺失时会阻止 Bean 创建。未来新增配置必须按语义分类。

## 决定与原因

本地缺省使用进程内随机 RSA，production 使用显式稳定 PEM；只为启动必需变量提供默认，可选空值
保留。三个数据库服务在 production profile 中覆盖为无默认密码占位符，防止共享环境采用公开的本地
开发口令。judging 本地默认使用独立服务账号，不使用 MySQL root。

## 尝试与教训

固定开发私钥虽然方便，但一旦部署误用，任何取得仓库的人都能伪造 token。默认文件路径若不自动生成，
只是把“缺环境变量”换成“缺文件”。`${LOG_FILE}` 是 Spring 日志系统属性，不是要求开发者 export。

## 已知问题

本地临时密钥会在重启后变化，不支持多实例共享。application 默认不会创建 MySQL schema、账号或
Redis；每台开发机仍需一次性准备基础设施。配置扫描当前放在 user-service 测试中，因为根 reactor
没有独立测试模块，未来若建立构建约束模块可迁移。

## 重新考虑条件

统一 Secret manager/配置中心、容器化一键本地环境、多实例本地 user-service、身份算法或密钥轮换
模型变化时重新评估。

## 变更记录

- 2026-08-31：状态变更：draft → review。原因：已沉淀本地/生产配置分类、随机密钥边界和数据库准备事项，提交复核
- 2026-09-01：结构与内容校验通过，由工具置为 checked。
