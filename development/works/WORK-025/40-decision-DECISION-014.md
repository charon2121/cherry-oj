---
id: "DECISION-014"
type: "decision"
title: "交付题库、题目与测试数据管理"
status: "approved"
work: "WORK-025"
owners: ["codex/root"]
depends_on: ["DESIGN-019"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-30"
updated_at: "2026-08-30"
---


# DECISION-014：交付题库、题目与测试数据管理

## 要决定什么

确认匿名题库、首版题目管理范围、测试数据存储/部署、跨服务授权和分页模型。

## 背景

产品允许匿名阅读公开题目，并要求管理员显式发布不可变版本。大测试数据不能进入 MySQL/Kafka，
problem-service 拥有源资产元信息，judging-service 拥有环境部署和标定。当前没有对象存储或机器身份；
若不先决定，Gateway、两个资源服务和 Web 会形成不同的存储、授权与发布假设。

## 候选方案

### 匿名读取

- 登录后转发用户 JWT：复用现有鉴权，但违背匿名浏览基线。
- 新增匿名机器 JWT：统一验 token，但为公开数据扩大密钥和身份体系。
- Gateway 唯一公开入口 + problem-service 窄 public GET（推荐）：只返回白名单字段，8082 保持内网。

### 测试数据存储

- 正文进 MySQL：事务直接，但违反已确认容量与安全边界。
- 首版绑定 S3/MinIO：扩展性好，但供应商、凭据、部署均未确定。
- problem-service 定义 `TestDataAssetStore`，首版私有文件实现（推荐）：数据库只存 opaque ref、hash、
  manifest；未来增加对象存储实现，不改变领域/API。

### 测试数据部署

- problem-service 直接写 judge 目录：少一次 HTTP，但破坏 judging-service 的部署所有权与环境隔离。
- 先建设机器身份：长期适合自动任务，但引入 client credentials、密钥和轮换。
- ADMIN JWT 委托的同步流式部署（推荐）：problem-service 代表当前 ADMIN 调 judging-service，后者独立
  验证角色/audience、校验资产并审计 actor。未来异步化时再建设服务身份。

### 管理范围与分页

- 首版题目管理创建 C++ ACM，只使用当前唯一 ACTIVE 环境，手工填写绝对限制并提交临时参考程序验证。
  环境注册/激活、CORE、多语言和自动标定另行设计。
- 列表采用稳定 cursor；offset+总数易在发布中重复/遗漏并增加 count 成本。

## 决定

建议采用窄 public GET、私有文件 `TestDataAssetStore`、ADMIN JWT 同步委托、C++ ACM + 当前 ACTIVE 环境
和稳定 cursor。测试数据是不可变 ZIP；judging-service 通过流式请求部署，不共享 problem 数据库或直接
暴露 storageRef。公开与管理 DTO 分离，Gateway 仍是浏览器唯一入口。

**状态：负责人已确认并授权按 `PLAN-015` 实施。**

## 理由

公开题目无需身份，安全重点是字段隔离；窄端点和统一 404 比伪造身份直接。消费方存储接口满足当前
私有部署又保留对象存储演进点。ADMIN 委托适合当前同步人工动作，避免提前建设机器身份，同时让
judging-service 独立验权和审计。C++ ACM/当前环境与项目第一个纵向切片一致，cursor 与既有索引一致。

## 影响与风险

文件实现要求部署方提供独立权限、容量、备份和同文件系统原子 rename。JWT 委托不适合后台异步任务，
请求必须在短 token 有效时开始，且有严格大小/超时。8082/8084 不得暴露公网。环境由运维准备；没有
ACTIVE 环境时管理端必须明确阻止部署/发布。cursor 不提供页码跳转或精确总数。

负责人已确认：

- [x] 接受公开题库无需登录，浏览器仍只访问 Gateway，problem-service 只精确放行 public GET。
- [x] 接受公开字段白名单与统一 404，不公开历史版本、隐藏数据、模板、作者或审计。
- [x] 接受稳定 cursor，不提供页码跳转和总数。
- [x] 接受首版私有文件 `TestDataAssetStore`，READY ZIP 不可覆盖，未来对象存储通过新实现接入。
- [x] 接受 ADMIN JWT 仅委托同步部署/验证；未来自动化/异步任务另建服务身份设计。
- [x] 接受管理端首版只创建 C++ ACM、只使用当前 ACTIVE 环境、手工绝对限制和临时参考程序。
- [x] 接受环境注册/激活、CORE、多语言、自动标定和对象存储供应商不在本工作。
- [x] 安全边界与权限影响已经由负责人确认，并允许按 `PLAN-015` 实施。
- [x] 测试数据存储、部署与发布就绪边界已经由负责人确认。

## 重新考虑条件

problem-service/judging-service 需要暴露公网、出现租户/付费可见性、使用对象存储、需要断点续传或异步/
多环境部署、服务身份、CORE/多语言、自动标定、精确总数或专用搜索时重新决策。

## 变更记录

- 2026-08-30：状态变更：draft → review。原因：匿名公开读取边界与稳定游标候选方案及推荐结论已列明，等待负责人确认
- 2026-08-30：根据范围反馈增加测试数据存储、部署授权、首版题目管理和发布就绪决策。
- 2026-08-30：负责人确认全部边界并明确允许按计划实施。
- 2026-08-30：状态变更：review → approved。原因：负责人已确认全部决策、安全权限、存储部署与发布边界
