CREATE TABLE user_account (
    id                         BINARY(16) NOT NULL,
    username                   VARCHAR(64) COLLATE utf8mb4_0900_as_cs NOT NULL,
    username_normalized        VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_hash              VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role                       VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status                     VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    password_change_required   BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_count         SMALLINT UNSIGNED NOT NULL DEFAULT 0,
    last_failed_login_at       DATETIME(6) NULL,
    locked_until               DATETIME(6) NULL,
    session_version            BIGINT NOT NULL DEFAULT 0,
    created_at                 DATETIME(6) NOT NULL,
    updated_at                 DATETIME(6) NOT NULL,
    row_version                BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_user_account_username_normalized UNIQUE (username_normalized),
    CONSTRAINT ck_user_account_role CHECK (role IN ('USER', 'ADMIN')),
    CONSTRAINT ck_user_account_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT ck_user_account_failed_login_count CHECK (failed_login_count <= 1000),
    CONSTRAINT ck_user_account_session_version CHECK (session_version >= 0),
    CONSTRAINT ck_user_account_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_user_account_time_order CHECK (updated_at >= created_at)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE user_login_session (
    id                    BINARY(16) NOT NULL,
    user_id               BINARY(16) NOT NULL,
    grant_hash            BINARY(32) NOT NULL,
    session_version       BIGINT NOT NULL,
    created_at            DATETIME(6) NOT NULL,
    last_used_at          DATETIME(6) NOT NULL,
    idle_expires_at       DATETIME(6) NOT NULL,
    absolute_expires_at   DATETIME(6) NOT NULL,
    revoked_at            DATETIME(6) NULL,
    revoke_reason         VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NULL,
    row_version           BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_user_login_session_grant_hash UNIQUE (grant_hash),
    KEY idx_user_login_session_user_active (user_id, revoked_at, absolute_expires_at),
    KEY idx_user_login_session_expiry (revoked_at, absolute_expires_at),
    CONSTRAINT fk_user_login_session_user FOREIGN KEY (user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_login_session_version CHECK (session_version >= 0),
    CONSTRAINT ck_user_login_session_time_order CHECK (
        last_used_at >= created_at
        AND idle_expires_at > created_at
        AND absolute_expires_at >= idle_expires_at
    ),
    CONSTRAINT ck_user_login_session_revoke CHECK (
        (revoked_at IS NULL AND revoke_reason IS NULL)
        OR (revoked_at IS NOT NULL AND revoke_reason IS NOT NULL)
    ),
    CONSTRAINT ck_user_login_session_row_version CHECK (row_version >= 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;

CREATE TABLE user_audit_event (
    id                BINARY(16) NOT NULL,
    actor_user_id     BINARY(16) NULL,
    target_user_id    BINARY(16) NULL,
    action            VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    subject_digest    BINARY(32) NULL,
    trace_id          VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NULL,
    detail_json       JSON NULL,
    created_at        DATETIME(6) NOT NULL,

    PRIMARY KEY (id),
    KEY idx_user_audit_target_created (target_user_id, created_at, id),
    KEY idx_user_audit_actor_created (actor_user_id, created_at, id),
    KEY idx_user_audit_action_created (action, created_at, id),
    CONSTRAINT fk_user_audit_actor FOREIGN KEY (actor_user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT fk_user_audit_target FOREIGN KEY (target_user_id)
        REFERENCES user_account (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_user_audit_detail CHECK (
        detail_json IS NULL OR JSON_TYPE(detail_json) = 'OBJECT'
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4;
