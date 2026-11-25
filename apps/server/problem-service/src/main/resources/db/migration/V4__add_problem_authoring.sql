CREATE TABLE problem_authoring_job (
    id BINARY(16) NOT NULL PRIMARY KEY,
    problem_id BINARY(16) NOT NULL,
    request_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    snapshot_digest CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    row_version BIGINT NOT NULL,
    kind VARCHAR(8) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(10) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    stage VARCHAR(12) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    attempt INT NOT NULL,
    input_json JSON NOT NULL,
    result_json JSON NULL,
    error_code VARCHAR(40) NULL,
    report MEDIUMTEXT NULL,
    archive MEDIUMBLOB NULL,
    applied_json JSON NULL,
    applied_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_authoring_problem FOREIGN KEY (problem_id) REFERENCES problem (id) ON DELETE CASCADE,
    CONSTRAINT uq_authoring_request UNIQUE (problem_id, request_digest),
    CONSTRAINT ck_authoring_kind CHECK (kind IN ('BUILD', 'PROPOSAL')),
    CONSTRAINT ck_authoring_status CHECK (status IN ('QUEUED', 'RUNNING', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT ck_authoring_attempt CHECK (attempt BETWEEN 1 AND 5),
    KEY idx_authoring_listing (problem_id, created_at, id),
    KEY idx_authoring_queue (status, updated_at, id)
) ENGINE = InnoDB;

-- 单行租约序列化并发认领。尝试号隔离取消、重试之后才到达的进度和结果。
CREATE TABLE problem_authoring_queue (
    id INT NOT NULL PRIMARY KEY,
    job_id BINARY(16) NULL,
    attempt INT NULL,
    lease_until DATETIME(6) NULL,
    CONSTRAINT ck_authoring_queue_owner CHECK (
        (job_id IS NULL AND attempt IS NULL AND lease_until IS NULL)
        OR (job_id IS NOT NULL AND attempt IS NOT NULL AND lease_until IS NOT NULL)
    )
) ENGINE = InnoDB;
INSERT INTO problem_authoring_queue (id) VALUES (1);
