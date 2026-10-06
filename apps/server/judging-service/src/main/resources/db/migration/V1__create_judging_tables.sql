-- 判题服务只记四类事实：在线节点、题目×语言的标定、判题任务、审计。
-- 没有判题环境、没有测试数据部署：测试数据由 problem-service 按协议写成目录，判题时按地址读取。

-- 串行化节点注册与续租，防止同一 nodeId 的两个进程竞争。
CREATE TABLE judge_node_registry_lock (id TINYINT NOT NULL PRIMARY KEY);
INSERT INTO judge_node_registry_lock VALUES (1);

-- 节点只带身份（nodeId + 会话）、访问地址和能判的语言。
CREATE TABLE judge_node (
    node_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    session_id BINARY(16) NOT NULL,
    endpoint VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    languages_json JSON NOT NULL,
    lease_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_node_online (lease_expires_at, node_id),
    CONSTRAINT ck_node_id CHECK (REGEXP_LIKE(node_id, '^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$', 'c')),
    CONSTRAINT ck_node_languages CHECK (JSON_TYPE(languages_json) = 'ARRAY'),
    CONSTRAINT ck_node_times CHECK (lease_expires_at > updated_at AND updated_at >= created_at)
) ENGINE = InnoDB;

-- 已被替换的进程不能通过重新注册夺回同一个 nodeId。
CREATE TABLE judge_node_session (
    node_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    session_id BINARY(16) NOT NULL,
    registered_at DATETIME(6) NOT NULL,
    PRIMARY KEY (node_id, session_id),
    CONSTRAINT fk_node_session_node FOREIGN KEY (node_id) REFERENCES judge_node(node_id)
) ENGINE = InnoDB;

-- 标定按「题目 × 语言」记录，并带着标定时的测试数据指纹：
-- 题目的测试数据换了（指纹变了），旧标定就不再对应它，需要重新标定。
-- 同一题目 × 语言最多一条 VALID，新的 VALID 批准后旧的转为 SUPERSEDED。
CREATE TABLE language_calibration (
    id BINARY(16) NOT NULL,
    problem_id BINARY(16) NOT NULL,
    language_id VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    test_data_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    valid_slot TINYINT GENERATED ALWAYS AS (CASE WHEN status = 'VALID' THEN 1 ELSE NULL END) STORED,
    source_type VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    cpu_ns BIGINT NULL,
    memory_bytes BIGINT NULL,
    clock_ns BIGINT NULL,
    benchmark_summary_json JSON NULL,
    approved_by BINARY(16) NULL,
    approved_at DATETIME(6) NULL,
    supersedes_id BINARY(16) NULL,
    error_message TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    CONSTRAINT uq_calibration_one_valid UNIQUE (problem_id, language_id, valid_slot),
    KEY idx_calibration_resolve (problem_id, language_id, status),
    KEY idx_calibration_supersedes (supersedes_id),
    CONSTRAINT fk_calibration_supersedes FOREIGN KEY (supersedes_id) REFERENCES language_calibration (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_calibration_language CHECK (REGEXP_LIKE(language_id, '^[a-z][a-z0-9-]{0,31}$', 'c')),
    CONSTRAINT ck_calibration_digest CHECK (REGEXP_LIKE(test_data_digest, '^[a-f0-9]{64}$', 'c')),
    CONSTRAINT ck_calibration_status CHECK (status IN ('DRAFT', 'RUNNING', 'VALID', 'FAILED', 'SUPERSEDED')),
    CONSTRAINT ck_calibration_source CHECK (source_type IN ('MANUAL', 'BENCHMARK')),
    CONSTRAINT ck_calibration_limits CHECK ((cpu_ns IS NULL OR cpu_ns > 0) AND (memory_bytes IS NULL OR memory_bytes > 0) AND (clock_ns IS NULL OR clock_ns > 0)),
    CONSTRAINT ck_calibration_valid CHECK (status <> 'VALID' OR (cpu_ns IS NOT NULL AND memory_bytes IS NOT NULL AND approved_by IS NOT NULL AND approved_at IS NOT NULL AND error_message IS NULL)),
    CONSTRAINT ck_calibration_failed CHECK (status <> 'FAILED' OR error_message IS NOT NULL),
    CONSTRAINT ck_calibration_benchmark CHECK (benchmark_summary_json IS NULL OR JSON_TYPE(benchmark_summary_json) = 'OBJECT'),
    CONSTRAINT ck_calibration_error_length CHECK (error_message IS NULL OR CHAR_LENGTH(error_message) <= 8192),
    CONSTRAINT ck_calibration_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_calibration_time CHECK (updated_at >= created_at)
) ENGINE = InnoDB;

CREATE TABLE judging_audit_event (
    id BINARY(16) NOT NULL,
    aggregate_type VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    aggregate_id BINARY(16) NOT NULL,
    actor_user_id BINARY(16) NULL,
    action VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    trace_id VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    detail_json JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_judging_audit_aggregate (aggregate_type, aggregate_id, created_at, id),
    KEY idx_judging_audit_actor (actor_user_id, created_at, id),
    CONSTRAINT ck_judging_audit_aggregate CHECK (aggregate_type IN ('CALIBRATION', 'TASK')),
    CONSTRAINT ck_judging_audit_detail CHECK (detail_json IS NULL OR JSON_TYPE(detail_json) = 'OBJECT')
) ENGINE = InnoDB;

CREATE TABLE judge_task (
    id CHAR(36) CHARACTER SET ascii PRIMARY KEY,
    submission_id CHAR(36) CHARACTER SET ascii NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL,
    attempt_no INT NOT NULL DEFAULT 0,
    lease_token CHAR(36) CHARACTER SET ascii NULL,
    lease_until DATETIME(6) NULL,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    trace_id CHAR(32) CHARACTER SET ascii NOT NULL,
    trace_parent VARCHAR(55) NULL,
    INDEX task_ready(status,next_attempt_at,lease_until)
);

-- test_data_digest：这次判题实际读取的测试数据指纹（judge 随结果返回），用来追溯「拿哪份数据判的」。
CREATE TABLE judge_attempt (
    task_id CHAR(36) CHARACTER SET ascii NOT NULL,
    attempt_no INT NOT NULL,
    lease_token CHAR(36) CHARACTER SET ascii NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(64) NULL,
    test_data_digest CHAR(64) CHARACTER SET ascii NULL,
    started_at DATETIME(6) NOT NULL,
    finished_at DATETIME(6) NULL,
    PRIMARY KEY(task_id,attempt_no),
    FOREIGN KEY(task_id) REFERENCES judge_task(id)
);
CREATE TABLE outbox_event (
    event_id CHAR(36) CHARACTER SET ascii PRIMARY KEY,
    message_key CHAR(36) CHARACTER SET ascii NOT NULL,
    payload JSON NOT NULL,
    trace_parent VARCHAR(55) NULL,
    published BOOLEAN NOT NULL DEFAULT FALSE,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX outbox_pending(published,next_attempt_at)
);
CREATE TABLE inbox_event (
    event_id CHAR(36) CHARACTER SET ascii PRIMARY KEY,
    created_at DATETIME(6) NOT NULL
);
