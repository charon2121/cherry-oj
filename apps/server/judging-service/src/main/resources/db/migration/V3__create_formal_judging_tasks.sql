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
CREATE TABLE judge_attempt (
    task_id CHAR(36) CHARACTER SET ascii NOT NULL,
    attempt_no INT NOT NULL,
    lease_token CHAR(36) CHARACTER SET ascii NOT NULL,
    status VARCHAR(20) NOT NULL,
    error_code VARCHAR(64) NULL,
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
