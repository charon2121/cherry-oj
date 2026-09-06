-- 串行化首次注册，防止空环境集合的并发注册竞争 ACTIVE 槽位。
CREATE TABLE judge_node_registry_lock (id TINYINT NOT NULL PRIMARY KEY);
INSERT INTO judge_node_registry_lock VALUES (1);

CREATE TABLE judge_node (
    node_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL PRIMARY KEY,
    judge_environment_id BINARY(16) NOT NULL,
    session_id BINARY(16) NOT NULL,
    endpoint VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
    metadata_json JSON NOT NULL,
    lease_expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    KEY idx_node_online (judge_environment_id, lease_expires_at, node_id),
    CONSTRAINT fk_node_environment FOREIGN KEY (judge_environment_id) REFERENCES judge_environment(id),
    CONSTRAINT ck_node_id CHECK (REGEXP_LIKE(node_id, '^[a-zA-Z0-9][a-zA-Z0-9._-]{0,63}$', 'c')),
    CONSTRAINT ck_node_metadata CHECK (JSON_TYPE(metadata_json) = 'OBJECT'),
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

CREATE TABLE test_data_node_deployment (
    test_data_version_id BINARY(16) NOT NULL,
    node_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    expected_sha256 BINARY(32) NOT NULL,
    session_id BINARY(16) NOT NULL,
    file_count INT NOT NULL,
    available BOOLEAN NOT NULL,
    deployed_at DATETIME(6) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (test_data_version_id, node_id),
    KEY idx_node_deployment_available (node_id, available, test_data_version_id),
    CONSTRAINT fk_node_deployment_node FOREIGN KEY (node_id) REFERENCES judge_node(node_id),
    CONSTRAINT ck_node_deployment_count CHECK (file_count BETWEEN 2 AND 2000),
    CONSTRAINT ck_node_deployment_available CHECK (available IN (0, 1))
) ENGINE = InnoDB;
