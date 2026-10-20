# 服务身份协议

规定服务之间的内部 HTTP 调用如何证明调用方身份与所代表的用户，以及 gateway 与 user-service 之间的登录会话接口。

## 快速使用

judging-service 调用 problem-service（不代表用户）：

```http
GET /internal/judging/problems/{id}/test-data HTTP/1.1
Host: problem-service
Authorization: Bearer <judging-service 签发的调用令牌，aud=problem-service>
```

gateway 代表用户调用 submission-service：

```http
POST /api/submissions HTTP/1.1
Host: submission-service
Authorization: Bearer <gateway-service 签发的调用令牌，aud=submission-service，带 user>
```

调用令牌示例（解码后）：

```json
头部: { "alg": "RS256", "typ": "JWT", "kid": "gateway-service" }
内容: {
  "iss": "gateway-service", "sub": "gateway-service", "aud": "submission-service",
  "iat": 1760947200, "exp": 1760947260, "jti": "5b1e9c2a-0f8e-4b7d-9a51-3c2f4e6d8a10",
  "user": { "id": "d0e35399-6487-4ac8-8138-8d5bd60eb003", "role": "USER" }
}
```

## 术语

| 概念 | 名称 | 定义 |
|---|---|---|
| 调用令牌 | call token | 调用方签发、随一次内部 HTTP 调用发送的 JWT |
| 服务名 | service name | `gateway-service`、`user-service`、`problem-service`、`submission-service`、`judging-service` |
| 用户上下文 | user | 调用令牌中的 `user` 声明，表示该调用所代表的用户 |
| 端点策略 | endpoint policy | 一个端点声明的允许调用方、是否需要用户、所需角色 |
| 登录会话 | session | 一次登录，由 `sessionId` 标识 |

## 1. 参与方

- **调用方**：任一服务，调用其他服务时签发调用令牌。
- **被调方**：任一服务，校验调用令牌并按端点策略决定是否处理请求。
- **gateway-service** 与 **user-service**：通过第 4 节的接口建立、核对、吊销登录会话。

## 2. 密钥

- 每个服务有一对 RSA 密钥（不少于 2048 位），用私钥签发自己的调用令牌。
- 每个服务的公钥以 `<服务名>-public.pem`（X.509 SubjectPublicKeyInfo，PEM）发布在公钥目录中，每个服务名对应一把公钥。
- 被调方以调用令牌头部 `kid` 指明的服务名，从公钥目录取公钥验签。

## 3. 调用令牌

### 3.1 格式

JWS 紧凑序列化，置于请求头 `Authorization: Bearer <调用令牌>`。

头部：

| 字段 | 值 |
|---|---|
| `alg` | `RS256` |
| `typ` | `JWT` |
| `kid` | 调用方服务名 |

声明：

| 声明 | 类型 | 必需 | 规则 |
|---|---|---|---|
| `iss` | string | 是 | 调用方服务名 |
| `sub` | string | 是 | 调用方服务名 |
| `aud` | string | 是 | 被调方服务名 |
| `iat` | number | 是 | 签发时间，Unix 秒 |
| `exp` | number | 是 | 过期时间，Unix 秒；`exp - iat` 不超过 60 |
| `jti` | string | 是 | UUID |
| `user` | object | 否 | `{"id": <用户 ID，UUID>, "role": "USER" \| "ADMIN"}`，不含其他字段 |

服务名匹配 `^[a-z][a-z0-9-]{1,62}$`。

### 3.2 校验

被调方对每个内部请求校验以下各项，任一不满足即拒绝：

1. 令牌为 RS256 签名的 JWT，头部 `kid` 是已发布公钥的服务名，签名可用该公钥验证；
2. `iss`、`sub` 均等于 `kid`；
3. `aud` 等于被调方服务名；
4. 以 30 秒容差：当前时间不晚于 `exp`，`iat` 不晚于当前时间；`exp - iat` 不超过 60 秒；
5. `jti` 非空；
6. 存在 `user` 时，其结构符合 3.1；
7. 满足该端点的策略（第 4 节）。

### 3.3 失败响应

| 条件 | 状态 | `code` |
|---|---|---|
| 3.2 第 1–6 项任一不满足 | 401 | `CALL_UNAUTHENTICATED` |
| 调用方不在端点允许的调用方中 | 403 | `CALLER_FORBIDDEN` |
| 端点需要用户，令牌没有 `user` | 401 | `USER_REQUIRED` |
| `user.role` 不满足端点所需角色 | 403 | `FORBIDDEN` |

`Content-Type: application/problem+json`，响应体：

```json
{ "type": "about:blank", "title": "<说明>", "status": 401, "code": "CALL_UNAUTHENTICATED" }
```

## 4. 端点策略

- 每个接受调用令牌的端点声明三项：允许的调用方（服务名集合）、是否需要用户、需要用户时所需的角色。
- 被调方按声明处理：调用方不在允许集合中返回 `CALLER_FORBIDDEN`；需要用户而令牌没有 `user` 返回 `USER_REQUIRED`；
  `user.role` 不满足所需角色返回 `FORBIDDEN`（见 3.3）。
- 调用方代表用户调用下游时，下游调用令牌中的 `user` 必须与其收到的调用令牌中的 `user` 相同。
- 没有声明策略的端点不接受调用令牌。

## 5. 登录会话接口

gateway-service 调用，user-service 提供；调用方须为 gateway-service，不需要用户。请求与响应均为 `application/json`。

| 端点 | 请求体 | 成功响应 |
|---|---|---|
| `POST /internal/auth/authenticate` | `{"username", "password"}` | 200 `{"sessionId", "user", "sessionExpiresAt"}` |
| `POST /internal/auth/validate` | `{"sessionId"}` | 200 `{"user", "sessionExpiresAt"}` |
| `POST /internal/auth/revoke` | `{"sessionId"}` | 204 |

| 字段 | 类型 | 规则 |
|---|---|---|
| `username` | string | 1–64 字符 |
| `password` | string | 1–128 字符 |
| `sessionId` | string | 40–128 个 ASCII 字符，不透明 |
| `sessionExpiresAt` | string | ISO-8601 时刻，会话的绝对过期时间 |
| `user` | object | `{"id", "username", "role", "status", "passwordChangeRequired", "createdAt", "updatedAt", "rowVersion"}` |

- `revoke` 之后，或会话过期、被吊销后，`validate` 与 `revoke` 对该 `sessionId` 返回 `SESSION_INVALID`。
- `sessionId` 只在 gateway-service 与 user-service 之间传递。

失败：

| 条件 | 状态 | `code` |
|---|---|---|
| 用户名或密码错误，或账号不可登录 | 401 | `AUTHENTICATION_FAILED` |
| `sessionId` 不存在、已吊销或已过期 | 401 | `SESSION_INVALID` |
| 请求体不符合字段规则 | 422 | `VALIDATION_FAILED` |

响应体 `{"code", "message"}`。

## 6. 版本与兼容

- 本协议没有版本号字段。
- 被调方忽略调用令牌中未知的顶层声明；`user` 中出现未知字段时拒绝。
- 令牌格式、校验规则、失败响应、会话接口的任何变化都是协议变更：先修改本文，再在同一次改动中修改双方实现及其测试。

## 7. 真源

以本文为准。没有机读 schema。

## 8. 实测记录

- 2026-10-20，本机（macOS，`scripts/e2e.py`）：五个服务各用自己的私钥签发调用令牌、按公钥目录校验；
  登录、会话核对、改密码后重新登录、管理端、公开题库、正式提交（ACM 与 CORE）、自测、标定、
  USER/ADMIN 权限与账号切换前置检查全部通过。
