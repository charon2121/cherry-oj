# cherry-oj MVP MySQL 物理数据模型

> 状态：MVP 目标设计，2026-08-20
> 产品需求真源：[`product.md`](./product.md)
> 服务与数据所有权：[architecture.md](./architecture.md)
> 领域模型：[data-model.md](./data-model.md)
> 后端技术基线：[backend.md](./backend.md)
> 跨服务字段真源：[`../contracts/`](../contracts/)

本文把 `data-model.md` 中的领域实体落成 MySQL 8.4 LTS 可实施的物理模型。它确定表、列、类型、
主键、服务内外键、唯一约束、检查约束、索引、事务边界和 Flyway 拆分方式；Java 实体、Mapper 和
Migration 必须以本文为依据，但跨服务 DTO 的字段与可选性仍以 `contracts/*.json` 为最高优先级。

---

## 1. 范围与结论

### 1.1 本次覆盖

MVP 有四个持久化服务，每个服务拥有独立 database/schema 和账号：

```text
user-service        → cherry_oj_user
problem-service     → cherry_oj_problem
submission-service  → cherry_oj_submission
judging-service     → cherry_oj_judging
```

`gateway-service` 的浏览器 Session 存 Redis，不建立业务表。Go judge 与 sandbox 不读取 Java 服务
数据库。测试数据包、生成产物和大报告进入私有文件/对象存储，MySQL 只保存引用、版本、摘要和状态。

本设计覆盖传统 OJ 纵向 MVP：用户、不可变题目版本、ACM/CORE 模板、测试数据版本元信息、环境、部署、
人工标定、提交快照、Outbox/Inbox、判题任务与尝试。PRD 阶段 2 的生成器、校验器、oracle、参考程序、
原始基准样本和验证报告需要在题目工厂开工前另行扩展，不在首批 migration 中提前建空表。

### 1.2 明确不做

- 不建立 workspace、tenant、班级、竞赛、Agent 或多管理员协作表。
- 不跨 database 建外键、JOIN、视图或共享 Mapper。
- 不使用 XA/2PC、数据库触发器、存储过程或事件调度器承载业务流程。
- 不用 JPA `ddl-auto` 或应用启动时自动改表；所有 DDL 只通过 Flyway。
- 不把测试数据正文、标准答案、完整编译产物、JWT、Cookie 或密钥写入业务表。
- 不为 `caseResults` 提前建立子表；MVP 保持受限 JSON 快照。

---

## 2. 全局物理约定

### 2.1 引擎、字符集与时区

- MySQL 版本固定为 8.4 LTS，存储引擎固定为 InnoDB。
- database 默认字符集为 `utf8mb4`，默认排序规则为 `utf8mb4_0900_ai_ci`。
- 稳定 token、hash、slug、语言 ID、事件类型和指纹列显式使用 `ascii_bin`，避免大小写折叠。
- 用户名使用 `utf8mb4_0900_as_ci`：大小写不敏感但重音敏感；应用层还要在写入前做 Unicode 规范化。
- 所有连接会话固定 `time_zone = '+00:00'`；时间点使用 `DATETIME(6)`，Java 使用 `Instant`。
- 不依赖 `ON UPDATE CURRENT_TIMESTAMP` 隐式写时间，所有时间由应用显式传入。

database 由部署层创建，而不是由某个业务服务创建：

```sql
CREATE DATABASE cherry_oj_user
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE cherry_oj_problem
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE cherry_oj_submission
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE DATABASE cherry_oj_judging
  CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

生产环境建议把 Flyway 迁移账号与运行账号分开：迁移账号拥有本 schema DDL 权限；运行账号只拥有本
schema 所需的 `SELECT / INSERT / UPDATE / DELETE`，没有其它 schema 权限。

### 2.2 类型映射

| 语义 | MySQL 类型 | 说明 |
|---|---|---|
| UUIDv7 | `BINARY(16)` | Java/API 使用标准 UUID 字符串；统一 MyBatis TypeHandler |
| SHA-256 | `BINARY(32)` | API 输出时编码为 64 位小写 hex |
| 时间点 | `DATETIME(6)` | UTC，微秒精度 |
| CPU/墙钟 ns | `BIGINT` | 正值限制或非负测量值，必须带 `CHECK` |
| 内存 bytes | `BIGINT` | 正值限制或非负测量值，必须带 `CHECK` |
| 状态/类型 | `VARCHAR` + `CHECK` | 不使用 MySQL `ENUM`，降低兼容演进成本 |
| Markdown/源码 | `MEDIUMTEXT` | API 仍必须在入库前执行更小的业务大小限制 |
| 结构化快照 | `JSON` | 仅用于 tags、manifest、结果、事件 payload 和审计详情 |
| 乐观锁 | `BIGINT` | 从 0 开始，更新使用 `WHERE row_version = ?` |

UUID 以 RFC 4122/9562 网络字节序原样保存，不使用 `UUID_TO_BIN(uuid, 1)` 的时序重排模式。UUIDv7 本身
已经时间有序；Java TypeHandler、测试数据和排障 SQL 必须使用同一种 16 字节布局。

### 2.3 命名、外键与删除

- 表、列、约束和索引统一 `lower_snake_case`；表名必须使用单数形式。用户主表使用
  `user_account`，不使用含义容易与数据库账号混淆的 `user`。
- 主键名为 `PRIMARY`；唯一索引使用 `uq_<table>_<meaning>`；普通索引使用
  `idx_<table>_<meaning>`；外键使用 `fk_<child>_<parent>`。
- 服务内稳定关系建立外键，默认 `ON DELETE RESTRICT ON UPDATE RESTRICT`。
- 跨服务 UUID 只保存值，不建立外键。例如 `submission.user_id` 不引用 user-service 数据库。
- 不使用 `CASCADE` 删除历史。已发布版本、提交、JudgeInput、任务、Attempt 和审计默认不物理删除。
- 未发布草稿的删除由应用按依赖顺序显式执行，便于记录审计并避免意外级联。

### 2.4 JSON 使用边界

以下内容使用 JSON：

- `problem_version.tags_json`
- `test_data_version.manifest_json`
- `language_calibration.benchmark_summary_json`
- `submission.case_results_json`
- `judge_attempt.judge_result_json`
- `outbox_event.payload_json`
- 各服务审计详情

核心关联、状态、环境指纹、绝对限制、租约和查询条件必须使用普通列。事件和结果 JSON 在进入数据库前
先按 contracts 校验；Outbox 整条序列化消息以及生命周期结果均限制为 1 MiB。

---

## 3. user-service：`cherry_oj_user`

### 3.1 表关系

```text
user_account
  └─ user_audit_event（actor/target；允许系统事件为空）
```

Gateway Session 在 Redis；`session_version` 是使既有 Session/JWT 失效的持久化版本号。JWT 私钥属于
部署 Secret，不进入 MySQL。

### 3.2 字段字典

每张物理表都必须维护字段字典；DDL 增删或改变字段语义时，本节与 migration 同步更新。

#### `user_account`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `username` | `VARCHAR(64)` | 否 | 登录用户名；按项目规则规范化后做全局唯一比较。 |
| `password_hash` | `VARCHAR(255)` | 否 | Spring Security 自适应密码摘要；禁止通过 API、日志或事件返回。 |
| `role` | `VARCHAR(16)` | 否 | 用户角色，MVP 仅允许 USER 或 ADMIN。 |
| `status` | `VARCHAR(16)` | 否 | 该记录当前状态；允许值和必需字段组合由 CHECK 约束。 |
| `session_version` | `BIGINT` | 否 | 会话失效版本；密码修改或封禁时递增，使旧 Session/JWT 失效。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `updated_at` | `DATETIME(6)` | 否 | 记录最后更新时间，使用 UTC。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `user_audit_event`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `actor_user_id` | `BINARY(16)` | 是 | 执行该操作的用户 UUID；系统自动操作时可以为空。 |
| `target_user_id` | `BINARY(16)` | 是 | 安全审计所针对的用户 UUID；无法解析用户时可以为空。 |
| `action` | `VARCHAR(64)` | 否 | 稳定的审计操作类型。 |
| `trace_id` | `VARCHAR(128)` | 是 | 跨服务调用与日志关联标识。 |
| `detail_json` | `JSON` | 是 | 受控审计详情；不得包含密码、源码、JWT 或其它敏感正文。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |

### 3.3 DDL

```sql
CREATE TABLE user_account (
    id                BINARY(16) NOT NULL,
    username          VARCHAR(64) COLLATE utf8mb4_0900_as_ci NOT NULL,
    password_hash     VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status            VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_version   BIGINT NOT NULL DEFAULT 0,
    created_at        DATETIME(6) NOT NULL,
    updated_at        DATETIME(6) NOT NULL,
    row_version       BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_user_account_username UNIQUE (username),
    CONSTRAINT ck_user_account_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_account_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_user_account_session_version CHECK (session_version >= 0),
    CONSTRAINT ck_user_account_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_user_account_time_order CHECK (updated_at >= created_at)
) ENGINE = InnoDB;

CREATE TABLE user_audit_event (
    id                BINARY(16) NOT NULL,
    actor_user_id     BINARY(16) NULL,
    target_user_id    BINARY(16) NULL,
    action            VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id          VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    detail_json       JSON NULL,
    created_at        DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_user_audit_target_created (target_user_id, created_at, id),
    KEY idx_user_audit_actor_created (actor_user_id, created_at, id),
    CONSTRAINT fk_user_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_audit_target FOREIGN KEY (target_user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_audit_detail CHECK (
        detail_json IS NULL OR JSON_TYPE(detail_json) = 'OBJECT'
    )
) ENGINE = InnoDB;
```

审计详情禁止包含密码、密码摘要、JWT、Cookie 或完整登录请求。未知用户名的失败登录可以让
`target_user_id` 为空，只记录受限、不可逆的主体摘要；该字段以后按安全需求增加，不在 MVP DDL 中
擅自保存明文登录输入。

---

## 4. problem-service：`cherry_oj_problem`

### 4.1 表关系

```text
problem
  ├─ test_data_version
  ├─ problem_version
  │    ├─ problem_sample
  │    └─ problem_version_language
  └─ problem_audit_event

problem.current_published_version_id ──► problem_version.id
problem_version.test_data_version_id ──► test_data_version.id
```

`ProblemJudgeSnapshot` 不建表。它从 `problem.current_published_version_id` 出发，在一个只读查询中连接
已发布版本、目标语言和 READY 测试数据，返回 contracts 定义的不可变 DTO。

### 4.2 字段字典

每张物理表都必须维护字段字典；DDL 增删或改变字段语义时，本节与 migration 同步更新。

#### `problem`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `slug` | `VARCHAR(128)` | 否 | 题目的全局唯一、大小写敏感短标识。 |
| `visibility` | `VARCHAR(16)` | 否 | 题目可见性，PRIVATE 或 PUBLIC。 |
| `status` | `VARCHAR(16)` | 否 | 该记录当前状态；允许值和必需字段组合由 CHECK 约束。 |
| `current_published_version_id` | `BINARY(16)` | 是 | 当前对用户生效的已发布题目版本 UUID。 |
| `created_by` | `BINARY(16)` | 否 | 创建人的 user-service UUID，仅作跨服务引用。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `updated_at` | `DATETIME(6)` | 否 | 记录最后更新时间，使用 UTC。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `test_data_version`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `problem_id` | `BINARY(16)` | 否 | 稳定题目 UUID；跨服务表中仅保存值，不建立跨库外键。 |
| `status` | `VARCHAR(16)` | 否 | 测试数据版本状态：UPLOADING、READY 或 FAILED。 |
| `source_type` | `VARCHAR(32)` | 否 | 测试数据来源；首批 migration 仅允许 MANUAL_UPLOAD。 |
| `storage_ref` | `VARCHAR(1024)` | 否 | 私有文件或对象存储引用，不包含访问凭证。 |
| `content_sha256` | `BINARY(32)` | 是 | READY 测试数据包内容的 SHA-256 二进制摘要。 |
| `case_count` | `INT UNSIGNED` | 是 | 测试数据版本包含的测试点数量。 |
| `total_bytes` | `BIGINT` | 是 | 测试数据版本全部文件的总字节数。 |
| `manifest_json` | `JSON` | 是 | 受 schema 约束的测试数据文件清单、大小与摘要。 |
| `created_by` | `BINARY(16)` | 否 | 创建人的 user-service UUID，仅作跨服务引用。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `ready_at` | `DATETIME(6)` | 是 | 测试数据版本封存为 READY 的时间。 |
| `error_message` | `TEXT` | 是 | 受长度限制且可安全展示/聚合的失败摘要。 |

#### `problem_version`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `problem_id` | `BINARY(16)` | 否 | 稳定题目 UUID；跨服务表中仅保存值，不建立跨库外键。 |
| `version_no` | `INT UNSIGNED` | 否 | 题目内部从 1 开始递增的版本号。 |
| `status` | `VARCHAR(32)` | 否 | 题目版本生命周期状态。 |
| `code_mode` | `VARCHAR(8)` | 否 | 提交代码模式，ACM 或 CORE。 |
| `title` | `VARCHAR(512)` | 否 | 该题目版本冻结的标题。 |
| `statement_markdown` | `MEDIUMTEXT` | 否 | 题目正文 Markdown。 |
| `input_description_markdown` | `MEDIUMTEXT` | 否 | 输入格式说明 Markdown。 |
| `output_description_markdown` | `MEDIUMTEXT` | 否 | 输出格式说明 Markdown。 |
| `constraints_markdown` | `MEDIUMTEXT` | 是 | 数据范围与约束 Markdown。 |
| `hint_markdown` | `MEDIUMTEXT` | 是 | 题目提示 Markdown。 |
| `difficulty` | `VARCHAR(16)` | 否 | 题目难度快照。 |
| `tags_json` | `JSON` | 否 | 题目标签字符串数组。 |
| `checker_type` | `VARCHAR(16)` | 否 | 判题比对器类型；MVP 固定 DEFAULT。 |
| `test_data_version_id` | `BINARY(16)` | 是 | 该题目版本绑定的本服务 TestDataVersion UUID。 |
| `change_summary` | `TEXT` | 是 | 该题目版本相对上一版本的修改说明。 |
| `created_by` | `BINARY(16)` | 否 | 创建人的 user-service UUID，仅作跨服务引用。 |
| `published_by` | `BINARY(16)` | 是 | 批准发布的 user-service 用户 UUID。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `updated_at` | `DATETIME(6)` | 否 | 记录最后更新时间，使用 UTC。 |
| `published_at` | `DATETIME(6)` | 是 | 题目版本正式发布并进入不可变状态的时间。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `problem_sample`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `problem_version_id` | `BINARY(16)` | 否 | 不可变题目版本 UUID；跨服务表中仅保存值。 |
| `ordinal` | `INT UNSIGNED` | 否 | 样例从 1 开始的展示顺序。 |
| `input_text` | `MEDIUMTEXT` | 否 | 样例标准输入文本。 |
| `expected_output_text` | `MEDIUMTEXT` | 否 | 样例期望标准输出文本。 |
| `explanation_markdown` | `MEDIUMTEXT` | 是 | 样例解释 Markdown。 |

#### `problem_version_language`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `problem_version_id` | `BINARY(16)` | 否 | 不可变题目版本 UUID；跨服务表中仅保存值。 |
| `language_id` | `VARCHAR(32)` | 否 | 稳定语言 token，例如 cpp。 |
| `display_order` | `SMALLINT UNSIGNED` | 否 | 该题目版本中语言选项的展示顺序。 |
| `starter_code` | `MEDIUMTEXT` | 否 | 用户编辑器初始代码。 |
| `judge_template` | `MEDIUMTEXT` | 是 | CORE 完整源码模板；必须恰含一个用户代码占位符，ACM 为空。 |

#### `problem_audit_event`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `problem_id` | `BINARY(16)` | 否 | 稳定题目 UUID；跨服务表中仅保存值，不建立跨库外键。 |
| `problem_version_id` | `BINARY(16)` | 是 | 不可变题目版本 UUID；跨服务表中仅保存值。 |
| `actor_user_id` | `BINARY(16)` | 否 | 执行题目操作的 user-service 用户 UUID，仅作跨服务引用。 |
| `action` | `VARCHAR(64)` | 否 | 稳定的审计操作类型。 |
| `trace_id` | `VARCHAR(128)` | 是 | 跨服务调用与日志关联标识。 |
| `detail_json` | `JSON` | 是 | 受控审计详情；不得包含密码、源码、JWT 或其它敏感正文。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |

### 4.3 DDL

```sql
CREATE TABLE problem (
    id                              BINARY(16) NOT NULL,
    slug                            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    visibility                      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status                          VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    current_published_version_id    BINARY(16) NULL,
    created_by                      BINARY(16) NOT NULL,
    created_at                      DATETIME(6) NOT NULL,
    updated_at                      DATETIME(6) NOT NULL,
    row_version                     BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_problem_slug UNIQUE (slug),
    KEY idx_problem_current_version (current_published_version_id),
    KEY idx_problem_listing (visibility, status, updated_at, id),
    CONSTRAINT ck_problem_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT ck_problem_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_problem_public_version CHECK (
        visibility <> 'PUBLIC' OR current_published_version_id IS NOT NULL
    ),
    CONSTRAINT ck_problem_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_problem_time_order CHECK (updated_at >= created_at)
) ENGINE = InnoDB;

CREATE TABLE test_data_version (
    id                  BINARY(16) NOT NULL,
    problem_id          BINARY(16) NOT NULL,
    status              VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    source_type         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    storage_ref         VARCHAR(1024) COLLATE utf8mb4_bin NOT NULL,
    content_sha256      BINARY(32) NULL,
    case_count          INT UNSIGNED NULL,
    total_bytes         BIGINT NULL,
    manifest_json       JSON NULL,
    created_by          BINARY(16) NOT NULL,
    created_at          DATETIME(6) NOT NULL,
    ready_at            DATETIME(6) NULL,
    error_message       TEXT NULL,

    PRIMARY KEY (id),
    KEY idx_test_data_problem_created (problem_id, created_at, id),
    KEY idx_test_data_status_created (status, created_at, id),
    CONSTRAINT fk_test_data_problem FOREIGN KEY (problem_id)
        REFERENCES problem (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_test_data_status CHECK (status IN ('UPLOADING', 'READY', 'FAILED')),
    CONSTRAINT ck_test_data_source CHECK (source_type IN ('MANUAL_UPLOAD')),
    CONSTRAINT ck_test_data_total_bytes CHECK (total_bytes IS NULL OR total_bytes >= 0),
    CONSTRAINT ck_test_data_manifest CHECK (
        manifest_json IS NULL OR JSON_TYPE(manifest_json) = 'OBJECT'
    ),
    CONSTRAINT ck_test_data_error_length CHECK (
        error_message IS NULL OR CHAR_LENGTH(error_message) <= 8192
    ),
    CONSTRAINT ck_test_data_ready CHECK (
        status <> 'READY' OR (
            content_sha256 IS NOT NULL
            AND case_count IS NOT NULL AND case_count > 0
            AND total_bytes IS NOT NULL AND total_bytes >= 0
            AND manifest_json IS NOT NULL
            AND ready_at IS NOT NULL
            AND error_message IS NULL
        )
    ),
    CONSTRAINT ck_test_data_failed CHECK (
        status <> 'FAILED' OR error_message IS NOT NULL
    )
) ENGINE = InnoDB;

CREATE TABLE problem_version (
    id                              BINARY(16) NOT NULL,
    problem_id                      BINARY(16) NOT NULL,
    version_no                      INT UNSIGNED NOT NULL,
    status                          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_mode                       VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    title                           VARCHAR(512) NOT NULL,
    statement_markdown              MEDIUMTEXT NOT NULL,
    input_description_markdown      MEDIUMTEXT NOT NULL,
    output_description_markdown     MEDIUMTEXT NOT NULL,
    constraints_markdown            MEDIUMTEXT NULL,
    hint_markdown                   MEDIUMTEXT NULL,
    difficulty                      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    tags_json                       JSON NOT NULL,
    checker_type                    VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    test_data_version_id            BINARY(16) NULL,
    change_summary                  TEXT NULL,
    created_by                      BINARY(16) NOT NULL,
    published_by                    BINARY(16) NULL,
    created_at                      DATETIME(6) NOT NULL,
    updated_at                      DATETIME(6) NOT NULL,
    published_at                    DATETIME(6) NULL,
    row_version                     BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_problem_version_no UNIQUE (problem_id, version_no),
    KEY idx_problem_version_status_updated (status, updated_at, id),
    KEY idx_problem_version_test_data (test_data_version_id),
    CONSTRAINT fk_problem_version_problem FOREIGN KEY (problem_id)
        REFERENCES problem (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_problem_version_test_data FOREIGN KEY (test_data_version_id)
        REFERENCES test_data_version (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_problem_version_version_no CHECK (version_no > 0),
    CONSTRAINT ck_problem_version_status CHECK (
        status IN ('DRAFT', 'VALIDATING', 'READY_FOR_REVIEW', 'PUBLISHED', 'ARCHIVED')
    ),
    CONSTRAINT ck_problem_version_code_mode CHECK (code_mode IN ('ACM', 'CORE')),
    CONSTRAINT ck_problem_version_difficulty CHECK (
        difficulty IN ('UNRATED', 'EASY', 'MEDIUM', 'HARD')
    ),
    CONSTRAINT ck_problem_version_checker CHECK (checker_type IN ('DEFAULT')),
    CONSTRAINT ck_problem_version_tags CHECK (JSON_TYPE(tags_json) = 'ARRAY'),
    CONSTRAINT ck_problem_version_published CHECK (
        status NOT IN ('PUBLISHED', 'ARCHIVED') OR (
            test_data_version_id IS NOT NULL
            AND published_by IS NOT NULL
            AND published_at IS NOT NULL
        )
    ),
    CONSTRAINT ck_problem_version_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_problem_version_time_order CHECK (
        updated_at >= created_at
        AND (published_at IS NULL OR published_at >= created_at)
    )
) ENGINE = InnoDB;

CREATE TABLE problem_sample (
    id                          BINARY(16) NOT NULL,
    problem_version_id          BINARY(16) NOT NULL,
    ordinal                     INT UNSIGNED NOT NULL,
    input_text                  MEDIUMTEXT NOT NULL,
    expected_output_text        MEDIUMTEXT NOT NULL,
    explanation_markdown        MEDIUMTEXT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_problem_sample_ordinal UNIQUE (problem_version_id, ordinal),
    CONSTRAINT fk_problem_sample_version FOREIGN KEY (problem_version_id)
        REFERENCES problem_version (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_problem_sample_ordinal CHECK (ordinal > 0)
) ENGINE = InnoDB;

CREATE TABLE problem_version_language (
    problem_version_id      BINARY(16) NOT NULL,
    language_id             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_order           SMALLINT UNSIGNED NOT NULL,
    starter_code            MEDIUMTEXT NOT NULL,
    judge_template          MEDIUMTEXT NULL,

    PRIMARY KEY (problem_version_id, language_id),
    CONSTRAINT uq_problem_language_order UNIQUE (problem_version_id, display_order),
    CONSTRAINT fk_problem_language_version FOREIGN KEY (problem_version_id)
        REFERENCES problem_version (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_problem_language_id CHECK (
        REGEXP_LIKE(language_id, '^[a-z][a-z0-9-]{0,31}$', 'c')
    )
) ENGINE = InnoDB;

CREATE TABLE problem_audit_event (
    id                      BINARY(16) NOT NULL,
    problem_id              BINARY(16) NOT NULL,
    problem_version_id      BINARY(16) NULL,
    actor_user_id           BINARY(16) NOT NULL,
    action                  VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id                VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    detail_json             JSON NULL,
    created_at              DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_problem_audit_problem_created (problem_id, created_at, id),
    KEY idx_problem_audit_actor_created (actor_user_id, created_at, id),
    CONSTRAINT fk_problem_audit_problem FOREIGN KEY (problem_id)
        REFERENCES problem (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_problem_audit_version FOREIGN KEY (problem_version_id)
        REFERENCES problem_version (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_problem_audit_detail CHECK (
        detail_json IS NULL OR JSON_TYPE(detail_json) = 'OBJECT'
    )
) ENGINE = InnoDB;

ALTER TABLE problem
    ADD CONSTRAINT fk_problem_current_version
    FOREIGN KEY (current_published_version_id)
    REFERENCES problem_version (id)
    ON DELETE RESTRICT ON UPDATE RESTRICT;
```

### 4.4 数据库不能单独表达的不变量

以下规则跨行或跨表，必须由 problem-service 在事务中校验，并用 MySQL 集成测试钉住：

1. `current_published_version_id` 必须属于同一个 Problem，且版本状态为 `PUBLISHED`。
2. `problem_version.test_data_version_id` 必须属于同一个 Problem，发布时对应数据必须是 `READY`。
3. ACM 的 `judge_template` 必须为空；CORE 的每门允许语言都必须有模板，且模板恰好包含一个字面量
   `{{USER_CODE}}`。
4. `PUBLISHED` 后 ProblemVersion、Sample、Language 和 TestDataVersion 不允许 UPDATE；修改时创建新版本。
5. 发布使用 `SELECT ... FOR UPDATE` 锁定 Problem 与目标版本，在同一事务内更新版本状态、当前指针和
   `problem_audit_event`。

---

## 5. submission-service：`cherry_oj_submission`

### 5.1 表关系

```text
submission
  ├─ judge_input             1:1，不可变完整送判输入
  └─ submission_request      创建幂等记录

outbox_event                发布 JudgeRequested
inbox_event                 消费 JudgeStarted/Completed/Failed
```

`effectiveLimits` 在物理表中拆为 `limit_cpu_ns / limit_memory_bytes / limit_clock_ns`，HTTP DTO 再组装为
contracts 中的对象。这三列是核心执行事实，不能只埋在 JSON 中。用户原始源码只在 `submission`，CORE
合并后的完整源码只在 `judge_input`；两者都不进入 Kafka。

### 5.2 字段字典

每张物理表都必须维护字段字典；DDL 增删或改变字段语义时，本节与 migration 同步更新。

#### `submission`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `user_id` | `BINARY(16)` | 否 | user-service 用户 UUID；仅作跨服务引用。 |
| `problem_id` | `BINARY(16)` | 否 | 稳定题目 UUID；跨服务表中仅保存值，不建立跨库外键。 |
| `problem_version_id` | `BINARY(16)` | 否 | 不可变题目版本 UUID；跨服务表中仅保存值。 |
| `problem_version_no` | `INT UNSIGNED` | 否 | 创建 Submission 时冻结的题目版本号。 |
| `problem_title` | `VARCHAR(512)` | 否 | 创建 Submission 时冻结的题目标题。 |
| `test_data_version_id` | `BINARY(16)` | 否 | 实际绑定的不可变测试数据版本 UUID。 |
| `language_id` | `VARCHAR(32)` | 否 | 稳定语言 token，例如 cpp。 |
| `code_mode` | `VARCHAR(8)` | 否 | 提交代码模式，ACM 或 CORE。 |
| `language_calibration_id` | `BINARY(16)` | 否 | 本次绝对限制来源的语言标定 UUID。 |
| `judge_environment_id` | `BINARY(16)` | 否 | 本次使用或关联的判题环境 UUID。 |
| `environment_fingerprint` | `VARCHAR(256)` | 否 | 判题环境不可变指纹快照。 |
| `limit_cpu_ns` | `BIGINT` | 否 | 每个测试点 CPU 时间绝对上限，单位 ns。 |
| `limit_memory_bytes` | `BIGINT` | 否 | 每个测试点内存绝对上限，单位 bytes。 |
| `limit_clock_ns` | `BIGINT` | 是 | 可选墙钟绝对上限，单位 ns。 |
| `source` | `MEDIUMTEXT` | 否 | 用户提交的原始源码；CORE 不包含服务端模板。 |
| `status` | `VARCHAR(16)` | 否 | 用户可见生命周期状态：PENDING、JUDGING 或 DONE。 |
| `verdict` | `VARCHAR(8)` | 是 | 用户程序最终判题结论。 |
| `cpu_ns` | `BIGINT` | 是 | 全部测试点 CPU 时间最大值，单位 ns。 |
| `memory_bytes` | `BIGINT` | 是 | 全部测试点内存峰值最大值，单位 bytes。 |
| `score` | `INT UNSIGNED` | 是 | 用户可见得分；MVP 为 AC 100、其它 0。 |
| `message` | `TEXT` | 是 | 受长度和泄密规则限制的用户可见判题摘要。 |
| `case_results_json` | `JSON` | 是 | 受 contracts 和 reveal 策略约束的测试点结果数组。 |
| `case_results_bytes` | `INT UNSIGNED` | 是 | case_results_json 序列化字节数，用于执行 1 MiB 上限。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `started_at` | `DATETIME(6)` | 是 | 首次开始处理或执行的时间。 |
| `finished_at` | `DATETIME(6)` | 是 | 记录进入终态或执行完成的时间。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `judge_input`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `submission_id` | `BINARY(16)` | 否 | Submission UUID；在相关服务中作为稳定外部关联键。 |
| `contract_version` | `VARCHAR(8)` | 否 | JudgeInput 跨服务契约版本。 |
| `problem_id` | `BINARY(16)` | 否 | 稳定题目 UUID；跨服务表中仅保存值，不建立跨库外键。 |
| `problem_version_id` | `BINARY(16)` | 否 | 不可变题目版本 UUID；跨服务表中仅保存值。 |
| `test_data_version_id` | `BINARY(16)` | 否 | 实际绑定的不可变测试数据版本 UUID。 |
| `language_id` | `VARCHAR(32)` | 否 | 稳定语言 token，例如 cpp。 |
| `complete_source` | `MEDIUMTEXT` | 否 | 实际送往 Go judge 的完整源码；CORE 已合并模板。 |
| `source_sha256` | `BINARY(32)` | 否 | complete_source UTF-8 字节的 SHA-256 摘要。 |
| `judge_environment_id` | `BINARY(16)` | 否 | 本次使用或关联的判题环境 UUID。 |
| `environment_fingerprint` | `VARCHAR(256)` | 否 | 判题环境不可变指纹快照。 |
| `language_calibration_id` | `BINARY(16)` | 否 | 本次绝对限制来源的语言标定 UUID。 |
| `limit_cpu_ns` | `BIGINT` | 否 | 每个测试点 CPU 时间绝对上限，单位 ns。 |
| `limit_memory_bytes` | `BIGINT` | 否 | 每个测试点内存绝对上限，单位 bytes。 |
| `limit_clock_ns` | `BIGINT` | 是 | 可选墙钟绝对上限，单位 ns。 |
| `created_at` | `DATETIME(6)` | 否 | JudgeInput 与 Submission 同事务冻结的时间。 |

#### `submission_request`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `user_id` | `BINARY(16)` | 否 | user-service 用户 UUID；仅作跨服务引用。 |
| `idempotency_key` | `VARCHAR(128)` | 否 | 客户端创建请求幂等键；同一次网络重试必须复用。 |
| `request_digest` | `BINARY(32)` | 否 | problemId、languageId 与 source 的规范化请求摘要。 |
| `submission_id` | `BINARY(16)` | 否 | Submission UUID；在相关服务中作为稳定外部关联键。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |

#### `outbox_event`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `event_id` | `BINARY(16)` | 否 | Kafka 事件 UUIDv7；同时作为 Inbox 幂等键。 |
| `topic` | `VARCHAR(128)` | 否 | 目标或来源 Kafka Topic。 |
| `message_key` | `VARCHAR(128)` | 否 | Kafka message key；判题链路固定为 submissionId 字符串。 |
| `event_type` | `VARCHAR(64)` | 否 | 稳定事件类型。 |
| `event_version` | `INT UNSIGNED` | 否 | 事件 payload 版本。 |
| `aggregate_id` | `BINARY(16)` | 否 | 事件聚合 UUID；判题链路固定为 submissionId。 |
| `trace_id` | `VARCHAR(128)` | 否 | 跨服务调用与日志关联标识。 |
| `occurred_at` | `DATETIME(6)` | 否 | 业务事件发生时间。 |
| `payload_json` | `JSON` | 否 | 事件 payload JSON；不包含信封列和敏感正文。 |
| `payload_bytes` | `INT UNSIGNED` | 否 | 完整事件序列化字节数，用于执行 1 MiB 上限。 |
| `status` | `VARCHAR(16)` | 否 | 事件发布状态：PENDING、PUBLISHING、PUBLISHED 或 FAILED。 |
| `attempt_count` | `INT UNSIGNED` | 否 | Outbox 已执行的发布尝试次数。 |
| `next_attempt_at` | `DATETIME(6)` | 否 | 下一次允许领取或重试的时间。 |
| `lease_token` | `BINARY(16)` | 是 | 当前 Relay 领取事件的 fencing token。 |
| `lease_until` | `DATETIME(6)` | 是 | 当前 Relay 领取租约到期时间。 |
| `last_error_message` | `TEXT` | 是 | 最近一次失败的安全摘要。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `published_at` | `DATETIME(6)` | 是 | Outbox 事件成功发布到 Kafka 的时间。 |

#### `inbox_event`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `event_id` | `BINARY(16)` | 否 | Kafka 事件 UUIDv7；同时作为 Inbox 幂等键。 |
| `topic` | `VARCHAR(128)` | 否 | 目标或来源 Kafka Topic。 |
| `kafka_partition` | `INT` | 否 | 消息所在 Kafka 分区。 |
| `kafka_offset` | `BIGINT` | 否 | 消息在分区内的 offset。 |
| `event_type` | `VARCHAR(64)` | 否 | 稳定事件类型。 |
| `event_version` | `INT UNSIGNED` | 否 | 事件 payload 版本。 |
| `aggregate_id` | `BINARY(16)` | 否 | 已消费事件的聚合 UUID；判题链路固定为 submissionId。 |
| `trace_id` | `VARCHAR(128)` | 否 | 跨服务调用与日志关联标识。 |
| `occurred_at` | `DATETIME(6)` | 否 | 业务事件发生时间。 |
| `processed_at` | `DATETIME(6)` | 否 | Inbox 事件和业务更新成功提交的时间。 |

### 5.3 DDL

```sql
CREATE TABLE submission (
    id                          BINARY(16) NOT NULL,
    user_id                     BINARY(16) NOT NULL,
    problem_id                  BINARY(16) NOT NULL,
    problem_version_id          BINARY(16) NOT NULL,
    problem_version_no          INT UNSIGNED NOT NULL,
    problem_title               VARCHAR(512) NOT NULL,
    test_data_version_id        BINARY(16) NOT NULL,
    language_id                 VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    code_mode                   VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    language_calibration_id     BINARY(16) NOT NULL,
    judge_environment_id        BINARY(16) NOT NULL,
    environment_fingerprint     VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    limit_cpu_ns                BIGINT NOT NULL,
    limit_memory_bytes          BIGINT NOT NULL,
    limit_clock_ns              BIGINT NULL,
    source                      MEDIUMTEXT NOT NULL,
    status                      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    verdict                     VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NULL,
    cpu_ns                      BIGINT NULL,
    memory_bytes                BIGINT NULL,
    score                       INT UNSIGNED NULL,
    message                     TEXT NULL,
    case_results_json           JSON NULL,
    case_results_bytes          INT UNSIGNED NULL,
    created_at                  DATETIME(6) NOT NULL,
    started_at                  DATETIME(6) NULL,
    finished_at                 DATETIME(6) NULL,
    row_version                 BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    KEY idx_submission_user_created (user_id, created_at, id),
    KEY idx_submission_problem_version_created (problem_version_id, created_at, id),
    KEY idx_submission_status_created (status, created_at, id),
    KEY idx_submission_user_problem_created (user_id, problem_id, created_at, id),
    CONSTRAINT ck_submission_problem_version_no CHECK (problem_version_no > 0),
    CONSTRAINT ck_submission_language CHECK (
        REGEXP_LIKE(language_id, '^[a-z][a-z0-9-]{0,31}$', 'c')
    ),
    CONSTRAINT ck_submission_code_mode CHECK (code_mode IN ('ACM', 'CORE')),
    CONSTRAINT ck_submission_limits CHECK (
        limit_cpu_ns > 0
        AND limit_memory_bytes > 0
        AND (limit_clock_ns IS NULL OR limit_clock_ns > 0)
    ),
    CONSTRAINT ck_submission_status CHECK (status IN ('PENDING', 'JUDGING', 'DONE')),
    CONSTRAINT ck_submission_verdict CHECK (
        verdict IS NULL OR verdict IN ('AC', 'WA', 'TLE', 'MLE', 'OLE', 'RE', 'CE', 'PE', 'SE', 'RAN')
    ),
    CONSTRAINT ck_submission_usage CHECK (
        (cpu_ns IS NULL OR cpu_ns >= 0)
        AND (memory_bytes IS NULL OR memory_bytes >= 0)
    ),
    CONSTRAINT ck_submission_message_length CHECK (
        message IS NULL OR CHAR_LENGTH(message) <= 8192
    ),
    CONSTRAINT ck_submission_case_results CHECK (
        (case_results_json IS NULL AND case_results_bytes IS NULL)
        OR (
            case_results_json IS NOT NULL
            AND JSON_TYPE(case_results_json) = 'ARRAY'
            AND case_results_bytes IS NOT NULL
            AND case_results_bytes <= 1048576
        )
    ),
    CONSTRAINT ck_submission_terminal CHECK (
        (status = 'DONE' AND verdict IS NOT NULL AND finished_at IS NOT NULL)
        OR (status <> 'DONE' AND verdict IS NULL AND finished_at IS NULL)
    ),
    CONSTRAINT ck_submission_time_order CHECK (
        (started_at IS NULL OR started_at >= created_at)
        AND (finished_at IS NULL OR finished_at >= created_at)
    ),
    CONSTRAINT ck_submission_row_version CHECK (row_version >= 0)
) ENGINE = InnoDB;

CREATE TABLE judge_input (
    submission_id              BINARY(16) NOT NULL,
    contract_version           VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    problem_id                 BINARY(16) NOT NULL,
    problem_version_id         BINARY(16) NOT NULL,
    test_data_version_id       BINARY(16) NOT NULL,
    language_id                VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    complete_source            MEDIUMTEXT NOT NULL,
    source_sha256              BINARY(32) NOT NULL,
    judge_environment_id       BINARY(16) NOT NULL,
    environment_fingerprint    VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    language_calibration_id    BINARY(16) NOT NULL,
    limit_cpu_ns               BIGINT NOT NULL,
    limit_memory_bytes         BIGINT NOT NULL,
    limit_clock_ns             BIGINT NULL,
    created_at                 DATETIME(6) NOT NULL,

    PRIMARY KEY (submission_id),
    CONSTRAINT fk_judge_input_submission FOREIGN KEY (submission_id)
        REFERENCES submission (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_judge_input_contract CHECK (contract_version IN ('2')),
    CONSTRAINT ck_judge_input_language CHECK (
        REGEXP_LIKE(language_id, '^[a-z][a-z0-9-]{0,31}$', 'c')
    ),
    CONSTRAINT ck_judge_input_limits CHECK (
        limit_cpu_ns > 0
        AND limit_memory_bytes > 0
        AND (limit_clock_ns IS NULL OR limit_clock_ns > 0)
    )
) ENGINE = InnoDB;

CREATE TABLE submission_request (
    user_id             BINARY(16) NOT NULL,
    idempotency_key     VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    request_digest      BINARY(32) NOT NULL,
    submission_id       BINARY(16) NOT NULL,
    created_at          DATETIME(6) NOT NULL,

    PRIMARY KEY (user_id, idempotency_key),
    CONSTRAINT uq_submission_request_submission UNIQUE (submission_id),
    CONSTRAINT fk_submission_request_submission FOREIGN KEY (submission_id)
        REFERENCES submission (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_submission_request_key CHECK (CHAR_LENGTH(idempotency_key) > 0)
) ENGINE = InnoDB;

CREATE TABLE outbox_event (
    event_id                BINARY(16) NOT NULL,
    topic                   VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message_key             VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type              VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_version           INT UNSIGNED NOT NULL,
    aggregate_id            BINARY(16) NOT NULL,
    trace_id                VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at             DATETIME(6) NOT NULL,
    payload_json            JSON NOT NULL,
    payload_bytes           INT UNSIGNED NOT NULL,
    status                  VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count           INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at         DATETIME(6) NOT NULL,
    lease_token             BINARY(16) NULL,
    lease_until             DATETIME(6) NULL,
    last_error_message      TEXT NULL,
    created_at              DATETIME(6) NOT NULL,
    published_at            DATETIME(6) NULL,

    PRIMARY KEY (event_id),
    KEY idx_outbox_delivery (status, next_attempt_at, created_at, event_id),
    KEY idx_outbox_lease (status, lease_until, event_id),
    KEY idx_outbox_aggregate (aggregate_id, created_at, event_id),
    CONSTRAINT ck_outbox_event_version CHECK (event_version > 0),
    CONSTRAINT ck_outbox_payload CHECK (
        JSON_TYPE(payload_json) = 'OBJECT' AND payload_bytes <= 1048576
    ),
    CONSTRAINT ck_outbox_status CHECK (
        status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT ck_outbox_lease CHECK (
        (status = 'PUBLISHING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PUBLISHING' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_outbox_published CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CONSTRAINT ck_outbox_error_length CHECK (
        last_error_message IS NULL OR CHAR_LENGTH(last_error_message) <= 8192
    )
) ENGINE = InnoDB;

CREATE TABLE inbox_event (
    event_id            BINARY(16) NOT NULL,
    topic               VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kafka_partition     INT NOT NULL,
    kafka_offset        BIGINT NOT NULL,
    event_type          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_version       INT UNSIGNED NOT NULL,
    aggregate_id        BINARY(16) NOT NULL,
    trace_id            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at         DATETIME(6) NOT NULL,
    processed_at        DATETIME(6) NOT NULL,

    PRIMARY KEY (event_id),
    CONSTRAINT uq_inbox_kafka_position UNIQUE (topic, kafka_partition, kafka_offset),
    KEY idx_inbox_aggregate (aggregate_id, processed_at, event_id),
    CONSTRAINT ck_inbox_partition CHECK (kafka_partition >= 0),
    CONSTRAINT ck_inbox_offset CHECK (kafka_offset >= 0),
    CONSTRAINT ck_inbox_event_version CHECK (event_version > 0)
) ENGINE = InnoDB;
```

submission-service 和 judging-service 各自创建同名 Outbox/Inbox 表；它们是不同 database 中的独立表，
不通过共享 Java 业务模块复用实体或 Mapper。

### 5.4 创建提交事务

创建正式提交必须按以下顺序执行：

1. 先完成 problem-service 与 judging-service 的同步解析，事务内不发 HTTP。
2. 开启 submission-service 本地事务。
3. 插入 `submission`、`judge_input`、`submission_request` 和 `JudgeRequested` 对应的
   `outbox_event`。
4. 任一唯一约束冲突或写入失败则整体回滚。
5. 同一 `(user_id, idempotency_key)` 冲突后读取既有记录：摘要相同返回原 Submission，摘要不同返回
   409；不能先查后插来代替唯一约束。

`judge_input` 创建后禁止 UPDATE。该不变量不通过触发器实现，而是通过 Mapper 不提供更新方法、代码
审查和 Testcontainers 集成测试保证。

### 5.5 生命周期事件投影

- 消费事件时先尝试插入 `inbox_event`；主键冲突表示已处理，事务幂等返回。
- Inbox 插入与 Submission 条件更新在同一个事务。
- `JudgeStarted` 只允许 `PENDING → JUDGING`，重复 Started 不覆盖 `started_at`。
- `JudgeCompleted/JudgeFailed` 只允许非终态进入 `DONE`；条件为 `status <> 'DONE' AND row_version = ?`。
- 最终基础设施失败保存为 `DONE + SE`。`DONE` 永不回退。

---

## 6. judging-service：`cherry_oj_judging`

### 6.1 表关系

```text
judge_environment
  ├─ judge_environment_language
  ├─ test_data_deployment
  ├─ language_calibration
  └─ judge_attempt

judge_task
  └─ judge_attempt

outbox_event     发布 JudgeStarted/Completed/Failed
inbox_event      消费 JudgeRequested
judging_audit_event
```

`ExecutionProfile` 不建表。它从唯一 ACTIVE environment 出发，连接启用语言、READY deployment 和当前
VALID calibration，返回冻结所需字段。`active_slot` 与 `valid_slot` 是生成列：MySQL 唯一索引允许多个
NULL，但只允许一个值为 1，因此可以在数据库层约束“最多一个 ACTIVE”与“每个组合最多一个 VALID”。

### 6.2 字段字典

每张物理表都必须维护字段字典；DDL 增删或改变字段语义时，本节与 migration 同步更新。

#### `judge_environment`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `name` | `VARCHAR(128)` | 否 | 判题环境的管理员可读名称。 |
| `fingerprint` | `VARCHAR(256)` | 否 | 由硬件、系统、judge、sandbox 与执行配置生成的环境唯一指纹。 |
| `status` | `VARCHAR(16)` | 否 | 环境状态：REGISTERED、ACTIVE 或 RETIRED。 |
| `active_slot` | `TINYINT` | 生成列 | 仅 ACTIVE 行生成 1 的内部列，用唯一索引限制最多一个 ACTIVE 环境。 |
| `architecture` | `VARCHAR(32)` | 否 | CPU/操作系统架构，例如 amd64。 |
| `cpu_model` | `VARCHAR(256)` | 否 | 影响标定的 CPU 型号或稳定性能标识。 |
| `os_version` | `VARCHAR(128)` | 否 | 判题节点操作系统版本。 |
| `kernel_version` | `VARCHAR(128)` | 否 | 判题节点内核版本。 |
| `judge_version` | `VARCHAR(128)` | 否 | Go judge 构建版本或摘要。 |
| `sandbox_version` | `VARCHAR(128)` | 否 | Go sandbox 构建版本或摘要。 |
| `config_digest` | `VARCHAR(128)` | 否 | 所有影响执行语义配置的摘要。 |
| `endpoint_ref` | `VARCHAR(512)` | 否 | 受控环境路由标识；禁止包含凭证。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `activated_at` | `DATETIME(6)` | 是 | 环境切换为 ACTIVE 的时间。 |
| `retired_at` | `DATETIME(6)` | 是 | 环境切换为 RETIRED 的时间。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `judge_environment_language`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `judge_environment_id` | `BINARY(16)` | 否 | 本次使用或关联的判题环境 UUID。 |
| `language_id` | `VARCHAR(32)` | 否 | 稳定语言 token，例如 cpp。 |
| `toolchain_version` | `VARCHAR(256)` | 否 | 该环境中编译器或运行时版本。 |
| `language_config_digest` | `VARCHAR(128)` | 否 | 语言编译与运行配置摘要。 |
| `enabled` | `BOOLEAN` | 否 | 该环境当前是否接受此语言。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `updated_at` | `DATETIME(6)` | 否 | 记录最后更新时间，使用 UTC。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `test_data_deployment`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `test_data_version_id` | `BINARY(16)` | 否 | 实际绑定的不可变测试数据版本 UUID。 |
| `judge_environment_id` | `BINARY(16)` | 否 | 本次使用或关联的判题环境 UUID。 |
| `expected_sha256` | `BINARY(32)` | 否 | 部署任务从 problem-service 接收并冻结的测试数据摘要。 |
| `status` | `VARCHAR(16)` | 否 | 数据部署状态：PENDING、DEPLOYING、READY 或 FAILED。 |
| `deployed_sha256` | `BINARY(32)` | 是 | 判题环境实际部署完成后的测试数据摘要。 |
| `deployed_at` | `DATETIME(6)` | 是 | 测试数据部署完成并校验成功的时间。 |
| `error_message` | `TEXT` | 是 | 最近部署失败的安全摘要。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `updated_at` | `DATETIME(6)` | 否 | 记录最后更新时间，使用 UTC。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `language_calibration`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `problem_version_id` | `BINARY(16)` | 否 | 不可变题目版本 UUID；跨服务表中仅保存值。 |
| `language_id` | `VARCHAR(32)` | 否 | 稳定语言 token，例如 cpp。 |
| `judge_environment_id` | `BINARY(16)` | 否 | 本次使用或关联的判题环境 UUID。 |
| `status` | `VARCHAR(16)` | 否 | 标定状态：DRAFT、RUNNING、VALID、FAILED 或 SUPERSEDED。 |
| `valid_slot` | `TINYINT` | 生成列 | 仅 VALID 行生成 1 的内部列，用唯一索引限制同组合最多一个有效标定。 |
| `source_type` | `VARCHAR(16)` | 否 | 限制来源：MANUAL 或 BENCHMARK。 |
| `cpu_ns` | `BIGINT` | 是 | 批准的每测试点 CPU 时间上限，单位 ns。 |
| `memory_bytes` | `BIGINT` | 是 | 批准的每测试点内存上限，单位 bytes。 |
| `clock_ns` | `BIGINT` | 是 | 可选墙钟上限，单位 ns。 |
| `benchmark_summary_json` | `JSON` | 是 | 标定样本与统计摘要；MVP 手工标定时可以为空。 |
| `approved_by` | `BINARY(16)` | 是 | 批准该标定生效的 user-service 用户 UUID。 |
| `approved_at` | `DATETIME(6)` | 是 | 该标定被批准为 VALID 的时间。 |
| `supersedes_id` | `BINARY(16)` | 是 | 被当前标定替代的本服务旧标定 UUID。 |
| `error_message` | `TEXT` | 是 | 标定失败的安全摘要。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `updated_at` | `DATETIME(6)` | 否 | 记录最后更新时间，使用 UTC。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `judge_task`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `submission_id` | `BINARY(16)` | 否 | Submission UUID；在相关服务中作为稳定外部关联键。 |
| `judge_input_contract_version` | `VARCHAR(8)` | 否 | JudgeRequested 指定的 JudgeInput 契约版本。 |
| `status` | `VARCHAR(32)` | 否 | 内部调度状态：READY、RUNNING、RETRY_WAITING、SUCCEEDED 或 DEAD。 |
| `attempt_no` | `INT UNSIGNED` | 否 | 该任务已经开始的尝试次数。 |
| `lease_token` | `BINARY(16)` | 是 | 当前 Worker 的 fencing token。 |
| `lease_until` | `DATETIME(6)` | 是 | 当前 Worker 租约到期时间。 |
| `next_attempt_at` | `DATETIME(6)` | 否 | READY 或 RETRY_WAITING 任务最早可领取时间。 |
| `last_error_code` | `VARCHAR(64)` | 是 | 最近一次稳定、可聚合的系统错误码。 |
| `last_error_message` | `TEXT` | 是 | 最近一次失败的安全摘要。 |
| `trace_id` | `VARCHAR(128)` | 否 | 跨服务调用与日志关联标识。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `updated_at` | `DATETIME(6)` | 否 | 记录最后更新时间，使用 UTC。 |
| `finished_at` | `DATETIME(6)` | 是 | 任务进入 SUCCEEDED 或 DEAD 的时间。 |
| `row_version` | `BIGINT` | 否 | 乐观锁版本；每次成功更新递增。 |

#### `judge_attempt`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `task_id` | `BINARY(16)` | 否 | 本服务 JudgeTask UUID。 |
| `attempt_no` | `INT UNSIGNED` | 否 | 该 Attempt 在所属任务中的递增序号。 |
| `lease_token` | `BINARY(16)` | 否 | 创建本 Attempt 时冻结的 Worker fencing token。 |
| `judge_environment_id` | `BINARY(16)` | 否 | 本次使用或关联的判题环境 UUID。 |
| `started_at` | `DATETIME(6)` | 否 | 首次开始处理或执行的时间。 |
| `finished_at` | `DATETIME(6)` | 是 | 本次调用完成或失败的时间。 |
| `outcome` | `VARCHAR(32)` | 是 | 本次 Attempt 的完成结果分类。 |
| `judge_result_json` | `JSON` | 是 | Go judge 返回且通过 contracts 校验的结果快照。 |
| `judge_result_bytes` | `INT UNSIGNED` | 是 | judge_result_json 序列化字节数。 |
| `error_code` | `VARCHAR(64)` | 是 | 本次 Attempt 的稳定系统错误码。 |
| `error_message` | `TEXT` | 是 | 本次 Attempt 的安全失败摘要。 |

#### `outbox_event`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `event_id` | `BINARY(16)` | 否 | Kafka 事件 UUIDv7；同时作为 Inbox 幂等键。 |
| `topic` | `VARCHAR(128)` | 否 | 目标或来源 Kafka Topic。 |
| `message_key` | `VARCHAR(128)` | 否 | Kafka message key；判题链路固定为 submissionId 字符串。 |
| `event_type` | `VARCHAR(64)` | 否 | 稳定事件类型。 |
| `event_version` | `INT UNSIGNED` | 否 | 事件 payload 版本。 |
| `aggregate_id` | `BINARY(16)` | 否 | 事件聚合 UUID；判题链路固定为 submissionId。 |
| `trace_id` | `VARCHAR(128)` | 否 | 跨服务调用与日志关联标识。 |
| `occurred_at` | `DATETIME(6)` | 否 | 业务事件发生时间。 |
| `payload_json` | `JSON` | 否 | 事件 payload JSON；不包含信封列和敏感正文。 |
| `payload_bytes` | `INT UNSIGNED` | 否 | 完整事件序列化字节数，用于执行 1 MiB 上限。 |
| `status` | `VARCHAR(16)` | 否 | 事件发布状态：PENDING、PUBLISHING、PUBLISHED 或 FAILED。 |
| `attempt_count` | `INT UNSIGNED` | 否 | Outbox 已执行的发布尝试次数。 |
| `next_attempt_at` | `DATETIME(6)` | 否 | 下一次允许领取或重试的时间。 |
| `lease_token` | `BINARY(16)` | 是 | 当前 Relay 领取事件的 fencing token。 |
| `lease_until` | `DATETIME(6)` | 是 | 当前 Relay 领取租约到期时间。 |
| `last_error_message` | `TEXT` | 是 | 最近一次失败的安全摘要。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |
| `published_at` | `DATETIME(6)` | 是 | Outbox 事件成功发布到 Kafka 的时间。 |

#### `inbox_event`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `event_id` | `BINARY(16)` | 否 | Kafka 事件 UUIDv7；同时作为 Inbox 幂等键。 |
| `topic` | `VARCHAR(128)` | 否 | 目标或来源 Kafka Topic。 |
| `kafka_partition` | `INT` | 否 | 消息所在 Kafka 分区。 |
| `kafka_offset` | `BIGINT` | 否 | 消息在分区内的 offset。 |
| `event_type` | `VARCHAR(64)` | 否 | 稳定事件类型。 |
| `event_version` | `INT UNSIGNED` | 否 | 事件 payload 版本。 |
| `aggregate_id` | `BINARY(16)` | 否 | 已消费事件的聚合 UUID；判题链路固定为 submissionId。 |
| `trace_id` | `VARCHAR(128)` | 否 | 跨服务调用与日志关联标识。 |
| `occurred_at` | `DATETIME(6)` | 否 | 业务事件发生时间。 |
| `processed_at` | `DATETIME(6)` | 否 | Inbox 事件和业务更新成功提交的时间。 |

#### `judging_audit_event`

| 字段 | MySQL 类型 | 可空 | 功能 |
|---|---|---:|---|
| `id` | `BINARY(16)` | 否 | 该记录的 UUIDv7 主键。 |
| `aggregate_type` | `VARCHAR(32)` | 否 | 审计对象类型，例如环境、部署、标定或任务。 |
| `aggregate_id` | `BINARY(16)` | 否 | 被审计的环境、部署、标定或任务 UUID。 |
| `actor_user_id` | `BINARY(16)` | 是 | 触发管理操作的 user-service 用户 UUID；系统任务时为空。 |
| `action` | `VARCHAR(64)` | 否 | 稳定的审计操作类型。 |
| `trace_id` | `VARCHAR(128)` | 是 | 跨服务调用与日志关联标识。 |
| `detail_json` | `JSON` | 是 | 受控审计详情；不得包含密码、源码、JWT 或其它敏感正文。 |
| `created_at` | `DATETIME(6)` | 否 | 记录创建时间，使用 UTC。 |

### 6.3 DDL

```sql
CREATE TABLE judge_environment (
    id                      BINARY(16) NOT NULL,
    name                    VARCHAR(128) NOT NULL,
    fingerprint             VARCHAR(256) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status                  VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    active_slot             TINYINT GENERATED ALWAYS AS (
                                CASE WHEN status = 'ACTIVE' THEN 1 ELSE NULL END
                            ) STORED,
    architecture            VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cpu_model               VARCHAR(256) NOT NULL,
    os_version              VARCHAR(128) NOT NULL,
    kernel_version          VARCHAR(128) NOT NULL,
    judge_version           VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    sandbox_version         VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    config_digest           VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    endpoint_ref            VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    created_at              DATETIME(6) NOT NULL,
    activated_at            DATETIME(6) NULL,
    retired_at              DATETIME(6) NULL,
    row_version             BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_judge_environment_fingerprint UNIQUE (fingerprint),
    CONSTRAINT uq_judge_environment_one_active UNIQUE (active_slot),
    KEY idx_judge_environment_status_created (status, created_at, id),
    CONSTRAINT ck_judge_environment_status CHECK (
        status IN ('REGISTERED', 'ACTIVE', 'RETIRED')
    ),
    CONSTRAINT ck_judge_environment_times CHECK (
        (status <> 'ACTIVE' OR (activated_at IS NOT NULL AND retired_at IS NULL))
        AND (status <> 'RETIRED' OR retired_at IS NOT NULL)
    ),
    CONSTRAINT ck_judge_environment_row_version CHECK (row_version >= 0)
) ENGINE = InnoDB;

CREATE TABLE judge_environment_language (
    judge_environment_id       BINARY(16) NOT NULL,
    language_id                VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    toolchain_version          VARCHAR(256) NOT NULL,
    language_config_digest     VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    enabled                    BOOLEAN NOT NULL,
    created_at                 DATETIME(6) NOT NULL,
    updated_at                 DATETIME(6) NOT NULL,
    row_version                BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (judge_environment_id, language_id),
    KEY idx_environment_language_enabled (language_id, enabled, judge_environment_id),
    CONSTRAINT fk_environment_language_environment FOREIGN KEY (judge_environment_id)
        REFERENCES judge_environment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_environment_language_id CHECK (
        REGEXP_LIKE(language_id, '^[a-z][a-z0-9-]{0,31}$', 'c')
    ),
    CONSTRAINT ck_environment_language_enabled CHECK (enabled IN (0, 1)),
    CONSTRAINT ck_environment_language_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_environment_language_time CHECK (updated_at >= created_at)
) ENGINE = InnoDB;

CREATE TABLE test_data_deployment (
    test_data_version_id       BINARY(16) NOT NULL,
    judge_environment_id       BINARY(16) NOT NULL,
    expected_sha256            BINARY(32) NOT NULL,
    status                     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    deployed_sha256            BINARY(32) NULL,
    deployed_at                DATETIME(6) NULL,
    error_message              TEXT NULL,
    created_at                 DATETIME(6) NOT NULL,
    updated_at                 DATETIME(6) NOT NULL,
    row_version                BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (test_data_version_id, judge_environment_id),
    KEY idx_deployment_environment_status (judge_environment_id, status, updated_at),
    CONSTRAINT fk_deployment_environment FOREIGN KEY (judge_environment_id)
        REFERENCES judge_environment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_deployment_status CHECK (
        status IN ('PENDING', 'DEPLOYING', 'READY', 'FAILED')
    ),
    CONSTRAINT ck_deployment_ready CHECK (
        status <> 'READY' OR (
            deployed_sha256 IS NOT NULL
            AND deployed_sha256 = expected_sha256
            AND deployed_at IS NOT NULL
            AND error_message IS NULL
        )
    ),
    CONSTRAINT ck_deployment_failed CHECK (
        status <> 'FAILED' OR error_message IS NOT NULL
    ),
    CONSTRAINT ck_deployment_error_length CHECK (
        error_message IS NULL OR CHAR_LENGTH(error_message) <= 8192
    ),
    CONSTRAINT ck_deployment_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_deployment_time CHECK (updated_at >= created_at)
) ENGINE = InnoDB;

CREATE TABLE language_calibration (
    id                      BINARY(16) NOT NULL,
    problem_version_id      BINARY(16) NOT NULL,
    language_id             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    judge_environment_id    BINARY(16) NOT NULL,
    status                  VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    valid_slot              TINYINT GENERATED ALWAYS AS (
                                CASE WHEN status = 'VALID' THEN 1 ELSE NULL END
                            ) STORED,
    source_type             VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cpu_ns                  BIGINT NULL,
    memory_bytes            BIGINT NULL,
    clock_ns                BIGINT NULL,
    benchmark_summary_json  JSON NULL,
    approved_by             BINARY(16) NULL,
    approved_at             DATETIME(6) NULL,
    supersedes_id           BINARY(16) NULL,
    error_message           TEXT NULL,
    created_at              DATETIME(6) NOT NULL,
    updated_at              DATETIME(6) NOT NULL,
    row_version             BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_calibration_one_valid UNIQUE (
        problem_version_id, language_id, judge_environment_id, valid_slot
    ),
    KEY idx_calibration_resolve (
        problem_version_id, language_id, judge_environment_id, status
    ),
    KEY idx_calibration_environment_status (judge_environment_id, status, updated_at),
    KEY idx_calibration_supersedes (supersedes_id),
    CONSTRAINT fk_calibration_environment FOREIGN KEY (judge_environment_id)
        REFERENCES judge_environment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_calibration_supersedes FOREIGN KEY (supersedes_id)
        REFERENCES language_calibration (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_calibration_language CHECK (
        REGEXP_LIKE(language_id, '^[a-z][a-z0-9-]{0,31}$', 'c')
    ),
    CONSTRAINT ck_calibration_status CHECK (
        status IN ('DRAFT', 'RUNNING', 'VALID', 'FAILED', 'SUPERSEDED')
    ),
    CONSTRAINT ck_calibration_source CHECK (source_type IN ('MANUAL', 'BENCHMARK')),
    CONSTRAINT ck_calibration_limits CHECK (
        (cpu_ns IS NULL OR cpu_ns > 0)
        AND (memory_bytes IS NULL OR memory_bytes > 0)
        AND (clock_ns IS NULL OR clock_ns > 0)
    ),
    CONSTRAINT ck_calibration_valid CHECK (
        status <> 'VALID' OR (
            cpu_ns IS NOT NULL
            AND memory_bytes IS NOT NULL
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND error_message IS NULL
        )
    ),
    CONSTRAINT ck_calibration_failed CHECK (
        status <> 'FAILED' OR error_message IS NOT NULL
    ),
    CONSTRAINT ck_calibration_benchmark CHECK (
        benchmark_summary_json IS NULL OR JSON_TYPE(benchmark_summary_json) = 'OBJECT'
    ),
    CONSTRAINT ck_calibration_error_length CHECK (
        error_message IS NULL OR CHAR_LENGTH(error_message) <= 8192
    ),
    CONSTRAINT ck_calibration_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_calibration_time CHECK (updated_at >= created_at)
) ENGINE = InnoDB;

CREATE TABLE judge_task (
    id                              BINARY(16) NOT NULL,
    submission_id                   BINARY(16) NOT NULL,
    judge_input_contract_version    VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status                          VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_no                      INT UNSIGNED NOT NULL DEFAULT 0,
    lease_token                     BINARY(16) NULL,
    lease_until                     DATETIME(6) NULL,
    next_attempt_at                 DATETIME(6) NOT NULL,
    last_error_code                 VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    last_error_message              TEXT NULL,
    trace_id                        VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    created_at                      DATETIME(6) NOT NULL,
    updated_at                      DATETIME(6) NOT NULL,
    finished_at                     DATETIME(6) NULL,
    row_version                     BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_judge_task_submission UNIQUE (submission_id),
    KEY idx_judge_task_available (status, next_attempt_at, id),
    KEY idx_judge_task_expired_lease (status, lease_until, id),
    CONSTRAINT ck_judge_task_contract CHECK (judge_input_contract_version IN ('2')),
    CONSTRAINT ck_judge_task_status CHECK (
        status IN ('READY', 'RUNNING', 'RETRY_WAITING', 'SUCCEEDED', 'DEAD')
    ),
    CONSTRAINT ck_judge_task_lease CHECK (
        (status = 'RUNNING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'RUNNING' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_judge_task_finished CHECK (
        (status IN ('SUCCEEDED', 'DEAD') AND finished_at IS NOT NULL)
        OR (status NOT IN ('SUCCEEDED', 'DEAD') AND finished_at IS NULL)
    ),
    CONSTRAINT ck_judge_task_error_code CHECK (
        last_error_code IS NULL OR REGEXP_LIKE(last_error_code, '^[A-Z][A-Z0-9_]{0,63}$', 'c')
    ),
    CONSTRAINT ck_judge_task_error_length CHECK (
        last_error_message IS NULL OR CHAR_LENGTH(last_error_message) <= 8192
    ),
    CONSTRAINT ck_judge_task_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_judge_task_time CHECK (
        updated_at >= created_at
        AND (finished_at IS NULL OR finished_at >= created_at)
    )
) ENGINE = InnoDB;

CREATE TABLE judge_attempt (
    id                          BINARY(16) NOT NULL,
    task_id                     BINARY(16) NOT NULL,
    attempt_no                  INT UNSIGNED NOT NULL,
    lease_token                 BINARY(16) NOT NULL,
    judge_environment_id        BINARY(16) NOT NULL,
    started_at                  DATETIME(6) NOT NULL,
    finished_at                 DATETIME(6) NULL,
    outcome                     VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    judge_result_json           JSON NULL,
    judge_result_bytes          INT UNSIGNED NULL,
    error_code                  VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    error_message               TEXT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_judge_attempt_no UNIQUE (task_id, attempt_no),
    KEY idx_judge_attempt_environment_started (judge_environment_id, started_at, id),
    CONSTRAINT fk_judge_attempt_task FOREIGN KEY (task_id)
        REFERENCES judge_task (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_judge_attempt_environment FOREIGN KEY (judge_environment_id)
        REFERENCES judge_environment (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_judge_attempt_no CHECK (attempt_no > 0),
    CONSTRAINT ck_judge_attempt_outcome CHECK (
        outcome IS NULL OR outcome IN (
            'COMPLETED', 'RETRYABLE_FAILURE', 'TERMINAL_FAILURE', 'STALE'
        )
    ),
    CONSTRAINT ck_judge_attempt_result CHECK (
        (judge_result_json IS NULL AND judge_result_bytes IS NULL)
        OR (
            judge_result_json IS NOT NULL
            AND JSON_TYPE(judge_result_json) = 'OBJECT'
            AND judge_result_bytes IS NOT NULL
            AND judge_result_bytes <= 1048576
        )
    ),
    CONSTRAINT ck_judge_attempt_finished CHECK (
        (finished_at IS NULL AND outcome IS NULL)
        OR (finished_at IS NOT NULL AND outcome IS NOT NULL AND finished_at >= started_at)
    ),
    CONSTRAINT ck_judge_attempt_error_code CHECK (
        error_code IS NULL OR REGEXP_LIKE(error_code, '^[A-Z][A-Z0-9_]{0,63}$', 'c')
    ),
    CONSTRAINT ck_judge_attempt_error_length CHECK (
        error_message IS NULL OR CHAR_LENGTH(error_message) <= 8192
    )
) ENGINE = InnoDB;

CREATE TABLE outbox_event (
    event_id                BINARY(16) NOT NULL,
    topic                   VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    message_key             VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type              VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_version           INT UNSIGNED NOT NULL,
    aggregate_id            BINARY(16) NOT NULL,
    trace_id                VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at             DATETIME(6) NOT NULL,
    payload_json            JSON NOT NULL,
    payload_bytes           INT UNSIGNED NOT NULL,
    status                  VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt_count           INT UNSIGNED NOT NULL DEFAULT 0,
    next_attempt_at         DATETIME(6) NOT NULL,
    lease_token             BINARY(16) NULL,
    lease_until             DATETIME(6) NULL,
    last_error_message      TEXT NULL,
    created_at              DATETIME(6) NOT NULL,
    published_at            DATETIME(6) NULL,

    PRIMARY KEY (event_id),
    KEY idx_outbox_delivery (status, next_attempt_at, created_at, event_id),
    KEY idx_outbox_lease (status, lease_until, event_id),
    KEY idx_outbox_aggregate (aggregate_id, created_at, event_id),
    CONSTRAINT ck_outbox_event_version CHECK (event_version > 0),
    CONSTRAINT ck_outbox_payload CHECK (
        JSON_TYPE(payload_json) = 'OBJECT' AND payload_bytes <= 1048576
    ),
    CONSTRAINT ck_outbox_status CHECK (
        status IN ('PENDING', 'PUBLISHING', 'PUBLISHED', 'FAILED')
    ),
    CONSTRAINT ck_outbox_lease CHECK (
        (status = 'PUBLISHING' AND lease_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PUBLISHING' AND lease_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_outbox_published CHECK (
        (status = 'PUBLISHED' AND published_at IS NOT NULL)
        OR (status <> 'PUBLISHED' AND published_at IS NULL)
    ),
    CONSTRAINT ck_outbox_error_length CHECK (
        last_error_message IS NULL OR CHAR_LENGTH(last_error_message) <= 8192
    )
) ENGINE = InnoDB;

CREATE TABLE inbox_event (
    event_id            BINARY(16) NOT NULL,
    topic               VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    kafka_partition     INT NOT NULL,
    kafka_offset        BIGINT NOT NULL,
    event_type          VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_version       INT UNSIGNED NOT NULL,
    aggregate_id        BINARY(16) NOT NULL,
    trace_id            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    occurred_at         DATETIME(6) NOT NULL,
    processed_at        DATETIME(6) NOT NULL,

    PRIMARY KEY (event_id),
    CONSTRAINT uq_inbox_kafka_position UNIQUE (topic, kafka_partition, kafka_offset),
    KEY idx_inbox_aggregate (aggregate_id, processed_at, event_id),
    CONSTRAINT ck_inbox_partition CHECK (kafka_partition >= 0),
    CONSTRAINT ck_inbox_offset CHECK (kafka_offset >= 0),
    CONSTRAINT ck_inbox_event_version CHECK (event_version > 0)
) ENGINE = InnoDB;

CREATE TABLE judging_audit_event (
    id                  BINARY(16) NOT NULL,
    aggregate_type      VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_id        BINARY(16) NOT NULL,
    actor_user_id       BINARY(16) NULL,
    action              VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    detail_json         JSON NULL,
    created_at          DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_judging_audit_aggregate (aggregate_type, aggregate_id, created_at, id),
    KEY idx_judging_audit_actor (actor_user_id, created_at, id),
    CONSTRAINT ck_judging_audit_aggregate CHECK (
        aggregate_type IN ('ENVIRONMENT', 'DEPLOYMENT', 'CALIBRATION', 'TASK')
    ),
    CONSTRAINT ck_judging_audit_detail CHECK (
        detail_json IS NULL OR JSON_TYPE(detail_json) = 'OBJECT'
    )
) ENGINE = InnoDB;
```

### 6.4 ExecutionProfile 解析

解析请求携带 `problem_version_id + test_data_version_id + expected_sha256 + language_id`。实现必须在
同一只读事务/一致性视图中完成：

1. 读取唯一 `status = 'ACTIVE'` 的 environment；没有或多于一个都视为配置错误。
2. 读取 `(environment_id, language_id)` 且 `enabled = 1` 的语言配置。
3. 读取 deployment，要求 `status = 'READY'`、`expected_sha256 = deployed_sha256`，并且请求 hash 与
   两者一致。
4. 读取对应组合唯一的 `status = 'VALID'` calibration。
5. 返回环境 ID/指纹、标定 ID 和拆列后的绝对限制；任一缺失都返回明确不可提交原因，不生成默认值。

### 6.5 环境切换与标定生效

- 激活环境时先锁定当前 ACTIVE 与目标 REGISTERED 行，完成覆盖检查后，先把旧环境改为 RETIRED，再把
  新环境改为 ACTIVE；`uq_judge_environment_one_active` 是并发最后防线。
- 生效新标定时锁定同一 `(problem_version_id, language_id, environment_id)` 的所有记录，先把旧 VALID
  改为 SUPERSEDED，再插入/更新新 VALID；`uq_calibration_one_valid` 防止并发双生效。
- 跨服务的 problemVersionId、testDataVersionId、approvedBy 只保存 UUID，不建立外键。
- 历史 JudgeInput 已冻结旧环境和旧标定；环境退休或标定被替代不能修改历史输入。

### 6.6 租约、Attempt 与迟到结果

任务领取分两步，外部 HTTP 调用不在数据库事务内：

1. 使用 `(status, next_attempt_at, id)` 索引选择 READY/RETRY_WAITING 且已到期的任务，短事务内条件更新为
   RUNNING，写入新 UUIDv7 `lease_token`、`lease_until`，递增 `attempt_no` 和 `row_version`。
2. 插入对应 `judge_attempt` 后提交事务，再拉 JudgeInput、调用 Go judge。
3. 完成时使用 `WHERE id = ? AND status = 'RUNNING' AND lease_token = ?` 条件更新；匹配失败说明租约已过期，
   该 Attempt 记为 STALE，不能发布完成事件。
4. 成功结果、Task 终态和 lifecycle Outbox 在同一事务提交；可重试失败清空租约并设置
   `RETRY_WAITING + next_attempt_at`。
5. 独立恢复器使用 `(status, lease_until, id)` 索引扫描过期 RUNNING，不与普通领取查询混在一个 OR 条件。

---

## 7. 关键查询与索引依据

索引只为 MVP 已知查询建立，不为每列机械添加单列索引。分页统一追加主键作为稳定排序尾键，优先使用
keyset pagination；管理后台确需跳页时才使用受限 offset pagination。

### 7.1 problem-service

- 题库列表：`visibility + status + updated_at + id` 使用 `idx_problem_listing`。
- 当前可判题版本：先由 Problem 主键读取 `current_published_version_id`，再通过各表主键/唯一键连接；
  `problem_version_language` 的复合主键直接支持语言解析。
- 版本历史：`uq_problem_version_no(problem_id, version_no)` 同时支持题内版本倒序查询。
- 测试数据历史：`idx_test_data_problem_created(problem_id, created_at, id)`。
- 发布审计：`idx_problem_audit_problem_created(problem_id, created_at, id)`。

### 7.2 submission-service

- 我的提交：`idx_submission_user_created(user_id, created_at, id)`。
- 某题尝试历史/通过状态：`idx_submission_user_problem_created(user_id, problem_id, created_at, id)`；通过状态
  用是否存在 `verdict = 'AC'` 派生，规模增长后再根据 EXPLAIN 决定是否增加专用索引或投影表。
- 版本结果分布：`idx_submission_problem_version_created(problem_version_id, created_at, id)`。
- 状态巡检：`idx_submission_status_created(status, created_at, id)`。
- Outbox Relay：`idx_outbox_delivery(status, next_attempt_at, created_at, event_id)`；过期 Relay 租约使用
  `idx_outbox_lease`。

### 7.3 judging-service

- ACTIVE 环境：生成列唯一索引使 `WHERE status = 'ACTIVE'` 最多返回一行。
- ExecutionProfile：environment language 主键、deployment 主键和 calibration resolve 索引覆盖三次精确查询。
- 可领取任务：`idx_judge_task_available(status, next_attempt_at, id)`。
- 过期租约：`idx_judge_task_expired_lease(status, lease_until, id)`。
- 环境任务/Attempt 排障：`idx_judge_attempt_environment_started`。

任何新联合索引都必须附带真实 SQL、`EXPLAIN ANALYZE` 和代表性数据量。不能只因为管理页面可能筛选某列
就提前增加索引；每个二级索引都会放大 Submission、Task 和 Outbox 的写成本。

---

## 8. 事务、锁与幂等边界

### 8.1 本地事务清单

必须原子提交的组合：

- problem-service：版本发布 + 当前版本指针 + 题目审计。
- submission-service：Submission + JudgeInput + SubmissionRequest + JudgeRequested Outbox。
- judging-service：JudgeRequested Inbox + 唯一 JudgeTask。
- judging-service：Attempt 完成 + Task 状态 + lifecycle Outbox。
- submission-service：lifecycle Inbox + Submission 条件更新。
- judging-service：环境切换 + 审计；标定替代 + 审计。

HTTP、Kafka 发布、对象存储传输和 Go judge 调用都不允许发生在上述数据库事务内部。

### 8.2 隔离级别

- 默认使用 MySQL `READ COMMITTED`，减少长事务和范围锁；需要稳定多表快照的只读解析显式在同一事务中
  完成。
- 发布、环境切换、标定生效和任务领取使用精确主键/唯一键 `SELECT ... FOR UPDATE`。
- Relay 和 Worker 批量领取允许使用 `FOR UPDATE SKIP LOCKED`，批次必须小，领取后立即提交。
- 乐观更新同时校验 `row_version`；状态机更新还必须在 `WHERE` 中带当前状态，不能只按 ID 覆盖。

### 8.3 数据库约束与应用约束分工

数据库直接约束：

- 本服务内引用存在性、唯一用户名/slug/版本号/幂等键/Submission Task。
- 状态和基础字段取值、正数资源限制、终态字段组合。
- 最多一个 ACTIVE 环境、每个组合最多一个 VALID 标定。
- EventId Inbox 去重、Kafka position 唯一、任务和 Attempt 次数唯一。

应用事务约束：

- Problem 当前版本属于自身且已发布。
- TestDataVersion 与 ProblemVersion 属于同题。
- ACM/CORE 模板条件和唯一占位符。
- 发布后内容、JudgeInput 和历史有效事实不可修改。
- deployment 的请求 hash 与 problem-service 快照 hash 一致。
- lifecycle 事件的 `aggregateId`、Kafka key 与 payload submissionId 一致。
- 只有当前 fencing token 可以接受 Attempt 结果。

禁止用“先 SELECT 看起来不存在，再 INSERT”代替唯一约束。并发正确性以数据库唯一约束或条件更新作为
最后防线。

---

## 9. Flyway 落地方案

每个服务独立维护 migration，不在父工程创建共享业务 migration：

```text
apps/server/user-service/src/main/resources/db/migration/
  V1__create_user_tables.sql

apps/server/problem-service/src/main/resources/db/migration/
  V1__create_problem_tables.sql

apps/server/submission-service/src/main/resources/db/migration/
  V1__create_submission_tables.sql
  V2__create_submission_messaging_tables.sql

apps/server/judging-service/src/main/resources/db/migration/
  V1__create_judging_environment_tables.sql
  V2__create_judging_task_and_messaging_tables.sql
```

拆成 V1/V2 只表达同一服务内清晰的基础设施边界，不意味着运行时可以缺少 V2。首个正式环境从空库执行
所有 migration；已有 migration 发布后禁止改写校验和，只能追加新版本。

### 9.1 Migration 规则

- 一个 migration 只操作本服务 database，不写 `USE other_database`。
- 不在业务 migration 中 `CREATE DATABASE`、`CREATE USER` 或 `GRANT`；这些属于部署层。
- 建表顺序遵守服务内外键依赖；`problem.current_published_version_id` 的循环外键最后 `ALTER TABLE`。
- DDL 明确写 `ENGINE = InnoDB`、字符集敏感列的 collation 和全部约束名。
- 生产 migration 不夹带演示用户、密码或业务题目。C++ A+B 验收数据使用独立的开发 profile seed，不能
  混入生产 migration。
- 禁止 `flyway.clean` 连接非临时数据库；测试每次使用 Testcontainers 新实例。

### 9.2 集成测试最低集合

每个服务至少使用 Testcontainers MySQL 8.4 验证：

1. 空库 Flyway 全量迁移成功，重复 `migrate` 幂等。
2. 所有 `CHECK`、唯一约束和服务内 FK 确实拒绝非法数据；不使用 H2 替代。
3. UUIDv7 TypeHandler 写入/读取的 16 字节布局一致，标准字符串可往返。
4. problem 发布并发只有一个当前指针结果，已发布记录的应用层更新被拒绝。
5. Submission 创建事务在任一步故障时不留下半条数据；幂等键并发只产生一个 Submission。
6. Inbox 重投不重复推进状态；DONE 不回退。
7. 环境并发激活只允许一个 ACTIVE；同组合并发标定只允许一个 VALID。
8. 两个 Worker 竞争只会有一个租约成功；旧 token 的迟到结果不能改变 Task 或发布完成事件。
9. Outbox `SKIP LOCKED` 多 Relay 领取不重复占用同一行，过期租约可以恢复。
10. 关键查询使用预期索引；准备代表性数据后保存 `EXPLAIN ANALYZE` 断言或基线报告。

---

## 10. 安全、容量与保留

### 10.1 敏感数据

- `user_account.password_hash`、`submission.source`、`judge_input.complete_source` 是敏感列；普通日志、审计详情、
  Kafka payload 和错误消息不得复制其正文。
- `judge_template` 只允许 problem-service 内部和受保护的快照接口读取，不进入普通用户 API。
- `endpoint_ref` 只能是受控路由标识，不能嵌入用户名、密码、token 或私钥。
- 数据库备份、传输和磁盘需要加密；生产账号使用最小权限，并分别轮换。
- `case_results_json` 和 `judge_result_json` 在入库前执行 reveal 策略，禁止隐藏输入和标准答案全文。

### 10.2 大字段与大小限制

`MEDIUMTEXT` 是数据库物理上限，不是 API 允许值。首个实现必须在应用配置中给 source、completeSource、
Markdown、模板、样例和错误摘要设置更小的业务上限。Outbox 与判题结果保存序列化字节数，并强制不超过
1 MiB；测试数据正文不进入任何 JSON 列。

### 10.3 保留与归档

MVP 不自动物理删除以下记录：

- 已发布/归档 ProblemVersion 及其样例、语言模板和 READY TestDataVersion 元信息。
- Submission、JudgeInput、SubmissionRequest。
- JudgeTask、JudgeAttempt、Inbox、审计事件。

Outbox 的 PUBLISHED 行可以在具备监控和备份后按策略归档；Inbox 保留时间必须覆盖 Kafka 最大重放窗口和
运维回放窗口。具体天数要依据容量数据另行决定，不能在无测量时写死。用户删除、源码保留期限和合规
匿名化属于后续独立设计，不通过 cascade 临时解决。

---

## 11. 从物理表到服务 DTO

### 11.1 ProblemJudgeSnapshot

由以下表查询组装，不单独持久化：

```text
problem
  → current PUBLISHED problem_version
  → problem_version_language(languageId)
  → READY test_data_version
```

`content_sha256 BINARY(32)` 转小写 hex；UUID 转标准带连字符字符串；CORE 返回 `judge_template`，ACM 不
返回。普通用户题目详情只能返回 starterCode，不能复用内部快照序列化器泄漏 judgeTemplate。

### 11.2 ExecutionProfile

由以下表查询组装，不单独持久化：

```text
unique ACTIVE judge_environment
  → enabled judge_environment_language
  → READY test_data_deployment with matching hash
  → unique VALID language_calibration
```

calibration 的 `cpu_ns / memory_bytes / clock_ns` 组装为 contracts 的 `effectiveLimits`。解析结果随后被
submission-service 同时冻结到 Submission 和 JudgeInput；未来环境或标定变化不能回写历史记录。

### 11.3 JudgeInput 与 JudgeRequest

`judge_input` 保存内部 contracts v2 所需的完整源码和限制。judging-service 拉取后构造 Go JudgeRequest：

- `complete_source` → `source`
- `limit_cpu_ns / limit_memory_bytes / limit_clock_ns` → `limits`
- `test_data_version_id` UUID → 标准小写 UUID 字符串目录键
- `environment_fingerprint` 只用于返回结果一致性验证，不要求 Go judge 从请求回显

---

## 12. 设计验收清单

- [x] 四个有状态服务分别拥有独立 database/schema；Gateway 无业务数据库。
- [x] 服务内关系有 FK，跨服务 UUID 无 FK、无 JOIN。
- [x] UUIDv7 使用 `BINARY(16)`；hash 使用 `BINARY(32)`；UTC 时间使用 `DATETIME(6)`。
- [x] Problem 与不可变 ProblemVersion 分离，当前发布版本使用稳定指针。
- [x] ACM/CORE 模板按 ProblemVersion × Language 保存，发布后不可修改。
- [x] 大测试数据不进 MySQL，只保存 storageRef、manifest、hash 和版本状态。
- [x] 环境、数据部署和语言标定属于 judging-service，绝对限制按环境保存。
- [x] 数据库约束最多一个 ACTIVE environment 和每组合一个 VALID calibration。
- [x] Submission 冻结题目、数据、语言、环境、标定和绝对限制快照。
- [x] 用户源码与完整送判源码分开；JudgeInput 与 Submission 一对一且同事务创建。
- [x] 创建幂等键、Inbox eventId、Task submissionId 和 Attempt 次数都有唯一约束。
- [x] Outbox/Inbox、任务租约、fencing token 和迟到结果边界可由物理列支持。
- [x] DONE 不回退、有效限制为正数、READY/VALID/终态所需字段有检查约束。
- [x] 关键查询都有基于真实访问路径的联合索引，不为所有列机械建索引。
- [x] Flyway 按服务拆分，Testcontainers MySQL 8.4 是 DDL、Mapper、锁和索引的验证边界。

---

## 13. 后续实现顺序

```text
1. 评审本物理模型并冻结首批 DDL
2. 父 POM 统一 MyBatis / Flyway / Connector/J / Testcontainers 版本
3. 四个服务分别加入 datasource 与 Flyway；先做空库 migration 测试
4. 实现 UUIDv7 生成器与各服务自己的 MyBatis UUID TypeHandler
5. 实现 problem-service 的 ProblemJudgeSnapshot 查询
6. 实现 judging-service 的 ExecutionProfile 查询
7. 实现 submission-service 的 Submission + JudgeInput + 幂等 + Outbox 事务
8. 实现 judging-service 的 Inbox + JudgeTask + 租约 + Attempt + lifecycle Outbox
9. 跑通 C++ ACM A+B，再加入 CORE 模板合并
```

不得跳过 migration/约束测试直接靠 Java 内存模型模拟数据库；也不得把本文 DDL 复制成一个所有服务共享
的 schema。物理表一旦进入 Flyway 并被部署，后续演进必须追加 migration，而不是回改 V1。
