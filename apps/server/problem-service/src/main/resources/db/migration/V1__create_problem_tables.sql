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
