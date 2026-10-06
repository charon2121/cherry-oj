-- 题目没有版本：题面、样例、语言和测试数据地址都直接属于 problem。
-- 测试数据只存地址（协议见 docs/testdata-protocol.md），数据本身与它的指纹、测试点数都在地址下的 testdata.json 里，
-- 数据库不再抄一份，避免两处不一致。
CREATE TABLE problem (
    id                              BINARY(16) NOT NULL,
    slug                            VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    visibility                      VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status                          VARCHAR(16) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
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
    test_data_location              VARCHAR(1024) COLLATE utf8mb4_bin NULL,
    test_data_updated_at            DATETIME(6) NULL,
    created_by                      BINARY(16) NOT NULL,
    created_at                      DATETIME(6) NOT NULL,
    updated_at                      DATETIME(6) NOT NULL,
    -- 第一次公开的时间。从没公开过的题目不可能有提交，所以只有它们可以被物理删除。
    published_at                    DATETIME(6) NULL,
    -- 乐观锁计数，与题目版本无关。
    row_version                     BIGINT NOT NULL DEFAULT 0,

    PRIMARY KEY (id),
    CONSTRAINT uq_problem_slug UNIQUE (slug),
    KEY idx_problem_listing (visibility, status, updated_at, id),
    CONSTRAINT ck_problem_visibility CHECK (visibility IN ('PRIVATE', 'PUBLIC')),
    CONSTRAINT ck_problem_status CHECK (status IN ('ACTIVE', 'ARCHIVED')),
    CONSTRAINT ck_problem_code_mode CHECK (code_mode IN ('ACM', 'CORE')),
    CONSTRAINT ck_problem_difficulty CHECK (difficulty IN ('UNRATED', 'EASY', 'MEDIUM', 'HARD')),
    CONSTRAINT ck_problem_checker CHECK (checker_type IN ('DEFAULT')),
    CONSTRAINT ck_problem_tags CHECK (JSON_TYPE(tags_json) = 'ARRAY'),
    CONSTRAINT ck_problem_public CHECK (
        visibility <> 'PUBLIC' OR (test_data_location IS NOT NULL AND published_at IS NOT NULL)
    ),
    CONSTRAINT ck_problem_test_data CHECK (
        (test_data_location IS NULL) = (test_data_updated_at IS NULL)
    ),
    CONSTRAINT ck_problem_row_version CHECK (row_version >= 0),
    CONSTRAINT ck_problem_time_order CHECK (
        updated_at >= created_at
        AND (published_at IS NULL OR published_at >= created_at)
    )
) ENGINE = InnoDB;

CREATE TABLE problem_sample (
    id                          BINARY(16) NOT NULL,
    problem_id                  BINARY(16) NOT NULL,
    ordinal                     INT UNSIGNED NOT NULL,
    input_text                  MEDIUMTEXT NOT NULL,
    expected_output_text        MEDIUMTEXT NOT NULL,
    explanation_markdown        MEDIUMTEXT NULL,

    PRIMARY KEY (id),
    CONSTRAINT uq_problem_sample_ordinal UNIQUE (problem_id, ordinal),
    CONSTRAINT fk_problem_sample_problem FOREIGN KEY (problem_id)
        REFERENCES problem (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_problem_sample_ordinal CHECK (ordinal > 0)
) ENGINE = InnoDB;

CREATE TABLE problem_language (
    problem_id              BINARY(16) NOT NULL,
    language_id             VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_order           SMALLINT UNSIGNED NOT NULL,
    starter_code            MEDIUMTEXT NOT NULL,
    judge_template          MEDIUMTEXT NULL,

    PRIMARY KEY (problem_id, language_id),
    CONSTRAINT uq_problem_language_order UNIQUE (problem_id, display_order),
    CONSTRAINT fk_problem_language_problem FOREIGN KEY (problem_id)
        REFERENCES problem (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_problem_language_id CHECK (
        REGEXP_LIKE(language_id, '^[a-z][a-z0-9-]{0,31}$', 'c')
    )
) ENGINE = InnoDB;

CREATE TABLE problem_audit_event (
    id                      BINARY(16) NOT NULL,
    problem_id              BINARY(16) NOT NULL,
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
    CONSTRAINT ck_problem_audit_detail CHECK (
        detail_json IS NULL OR JSON_TYPE(detail_json) = 'OBJECT'
    )
) ENGINE = InnoDB;
