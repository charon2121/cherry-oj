---
id: "MEMORY-010"
type: "memory"
title: "建立用户身份与访问控制服务"
status: "approved"
work: "WORK-013"
owners: ["codex/root"]
depends_on: ["VERIFY-013"]
related: []
implements: []
verifies: []
tags: []
created_at: "2026-08-26"
updated_at: "2026-08-26"
---



# MEMORY-010：建立用户身份与访问控制服务

## 背景

WORK-013 在管理员开通账号、浏览器 BFF 和资源服务尚无统一身份的基础上，交付了自建用户身份底座。
浏览器 Session、登录授权和内部 JWT 是三个不同对象：Redis Session 绑定浏览器并承载 Cookie/CSRF；
MySQL 登录授权摘要表达单端/全端撤销事实；120 秒 RS256 JWT 只用于资源服务离线验权。把三者合并会让
浏览器接触内部 token、让资源服务相信裸头，或失去可审计的撤销真源。

## 决定与原因

长期基线采用 DECISION-009 的 Gateway Redis Session + user-service 登录授权摘要 + 短 JWT：Cookie
`HttpOnly + SameSite=Lax + Path=/api`，生产必须启用 Secure；Session 空闲 30 分钟、绝对 12 小时；JWT
固定 RS256、issuer `cherry-oj-user-service`、audience `cherry-oj-internal`、TTL 120 秒、偏差最多 30 秒。
密码用 Argon2id，数据库只存摘要；首次管理员只从离线命令标准输入创建，普通 API 只能创建 USER。

登录、退出、改密、重置、停用与角色/状态变更各自更新正确真源：退出清当前 Redis Session 并尽力撤销
当前授权；改密/重置/停用递增 sessionVersion 且撤销全部授权；资源服务拒绝 `pwd=true`，从而在服务端
落实首次改密门禁。刷新临时 503 保留 Session，明确 401 才清理；Session 查询进入刷新窗口时也确认
撤销事实，避免 UI 长期显示过期身份。

## 尝试与教训

失败登录不能在同一个声明式事务里“更新后抛异常”，否则 Spring 会把退避计数和审计一起回滚。这里用
TransactionTemplate 在事务内返回失败事实并提交，事务外再抛统一认证错误。MySQL 同一 `UPDATE` 的赋值
顺序也会影响后续表达式：锁定判断必须在计数递增前计算，否则阈值会从第 5 次悄悄变成第 4 次；真实
MySQL 回归比内存 mock 更早暴露这种差异。

JWKS 重叠发布不仅要求资源服务缓存旧公钥，user-service 自己消费管理/改密 token 时也必须用完整公开
JWKSet 验证当前与上一 `kid`。资源服务对未知 kid 获取失败返回 503、对签名/claim 无效返回 401；不能
把密钥基础设施故障伪装成用户登出。Gateway 的刷新单飞只解决同 Session 并发，不替代 MySQL 乐观锁。

## 已知问题

生产 Secret 管理、TLS/内网隔离、Redis/MySQL HA、真实 Argon2/限速容量、时钟同步和公网策略仍未验证；
完整账号恢复、删除/匿名化、设备管理和零窗口撤销不在本工作。problem/submission/judging 的业务端点和
Gateway 资源转发尚属于 WORK-002，当前只交付并验证资源服务器安全入口，不能把安全 probe 当业务 E2E。

## 重新考虑条件

出现企业 SSO/外部 API、多租户、更多角色、邮箱验证、移动端、即时撤销、合规删除或目标环境无法承担
选定哈希成本时，重新审查身份模型、token audience、会话存储与权限体系。

## 变更记录

- 2026-08-26：状态变更：draft → review。原因：已沉淀三类身份对象、安全参数、事务与 MySQL 教训、轮换策略和生产风险
- 2026-08-26：状态变更：review → approved。原因：长期身份边界、实现教训、已知问题与重新考虑条件已完成复核
