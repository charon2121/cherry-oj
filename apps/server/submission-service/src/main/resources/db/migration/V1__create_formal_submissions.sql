CREATE TABLE submission (
    id CHAR(36) CHARACTER SET ascii PRIMARY KEY,
    user_id CHAR(36) CHARACTER SET ascii NOT NULL,
    problem_id CHAR(36) CHARACTER SET ascii NOT NULL,
    status VARCHAR(16) NOT NULL,
    read_model JSON NOT NULL,
    source MEDIUMTEXT NOT NULL,
    task_id CHAR(36) CHARACTER SET ascii NULL,
    attempt_no INT NOT NULL DEFAULT 0,
    row_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    INDEX submission_owner (user_id, created_at),
    INDEX submission_pending (status, created_at)
);
CREATE TABLE judge_input (
    submission_id CHAR(36) CHARACTER SET ascii PRIMARY KEY,
    payload JSON NOT NULL,
    FOREIGN KEY (submission_id) REFERENCES submission(id)
);
CREATE TABLE submission_request (
    user_id CHAR(36) CHARACTER SET ascii NOT NULL,
    idempotency_key CHAR(36) CHARACTER SET ascii NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii NOT NULL,
    submission_id CHAR(36) CHARACTER SET ascii NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (user_id, idempotency_key),
    FOREIGN KEY (submission_id) REFERENCES submission(id)
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
    INDEX outbox_pending (published, next_attempt_at)
);
CREATE TABLE inbox_event (
    event_id CHAR(36) CHARACTER SET ascii PRIMARY KEY,
    created_at DATETIME(6) NOT NULL
);
