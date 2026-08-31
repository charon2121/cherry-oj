package com.cherryoj.problemservice.bootstrap;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@Profile("dev")
public class DevProblemSeed implements ApplicationRunner {

    private static final String PROBLEM_ID = "019c8e42-7f70-7000-8000-000000000101";
    private static final String VERSION_ID = "019c8e42-7f70-7000-8000-000000000102";
    private static final String TEST_DATA_ID = "019c8e42-7f70-7000-8000-000000000103";
    private static final String SAMPLE_ID = "019c8e42-7f70-7000-8000-000000000104";
    private static final String USER_ID = "019c8e42-7f70-7000-8000-000000000001";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;

    public DevProblemSeed(JdbcTemplate jdbc, TransactionTemplate transactions) {
        this.jdbc = jdbc;
        this.transactions = transactions;
    }

    @Override
    public void run(ApplicationArguments args) {
        transactions.executeWithoutResult(status -> seed());
    }

    private void seed() {
        jdbc.update("""
                INSERT IGNORE INTO problem
                    (id, slug, visibility, status, current_published_version_id,
                     created_by, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(?), 'a-plus-b', 'PRIVATE', 'ACTIVE', NULL,
                        UUID_TO_BIN(?), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, PROBLEM_ID, USER_ID);
        jdbc.update("""
                INSERT IGNORE INTO test_data_version
                    (id, problem_id, status, source_type, storage_ref, content_sha256,
                     case_count, total_bytes, manifest_json, created_by, created_at, ready_at, error_message)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'READY', 'MANUAL_UPLOAD', 'dev/a-plus-b.zip',
                        UNHEX(REPEAT('0', 64)), 1, 8,
                        JSON_OBJECT('caseCount', 1, 'totalBytes', 8, 'files', JSON_ARRAY()),
                        UUID_TO_BIN(?), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), NULL)
                """, TEST_DATA_ID, PROBLEM_ID, USER_ID);
        jdbc.update("""
                INSERT IGNORE INTO problem_version
                    (id, problem_id, version_no, status, code_mode, title, statement_markdown,
                     input_description_markdown, output_description_markdown, constraints_markdown,
                     hint_markdown, difficulty, tags_json, checker_type, test_data_version_id,
                     change_summary, created_by, published_by, created_at, updated_at, published_at, row_version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 1, 'PUBLISHED', 'ACM', 'A+B Problem',
                        '计算两个整数之和。', '输入两个整数。', '输出它们的和。',
                        '整数在 32 位有符号范围内。', NULL, 'EASY', JSON_ARRAY('入门'), 'DEFAULT',
                        UUID_TO_BIN(?), '开发环境幂等验收题', UUID_TO_BIN(?), UUID_TO_BIN(?),
                        UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, VERSION_ID, PROBLEM_ID, TEST_DATA_ID, USER_ID, USER_ID);
        jdbc.update("""
                INSERT IGNORE INTO problem_sample
                    (id, problem_version_id, ordinal, input_text, expected_output_text, explanation_markdown)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 1, '1 2\n', '3\n', NULL)
                """, SAMPLE_ID, VERSION_ID);
        jdbc.update("""
                INSERT IGNORE INTO problem_version_language
                    (problem_version_id, language_id, display_order, starter_code, judge_template)
                VALUES (UUID_TO_BIN(?), 'cpp', 1,
                        '#include <iostream>\nusing namespace std;\nint main() { return 0; }\n', NULL)
                """, VERSION_ID);
        jdbc.update("""
                UPDATE problem
                SET visibility = 'PUBLIC', current_published_version_id = UUID_TO_BIN(?), updated_at = UTC_TIMESTAMP(6)
                WHERE id = UUID_TO_BIN(?)
                  AND (current_published_version_id IS NULL OR current_published_version_id = UUID_TO_BIN(?))
                """, VERSION_ID, PROBLEM_ID, VERSION_ID);
    }
}
