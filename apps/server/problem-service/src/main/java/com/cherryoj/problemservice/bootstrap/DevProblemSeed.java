package com.cherryoj.problemservice.bootstrap;

import com.cherryoj.problemservice.storage.TestDataStore;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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
    private static final String SAMPLE_ID = "019c8e42-7f70-7000-8000-000000000104";
    private static final String USER_ID = "019c8e42-7f70-7000-8000-000000000001";

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final TestDataStore testData;

    public DevProblemSeed(JdbcTemplate jdbc, TransactionTemplate transactions, TestDataStore testData) {
        this.jdbc = jdbc;
        this.transactions = transactions;
        this.testData = testData;
    }

    @Override
    public void run(ApplicationArguments args) {
        // 测试数据按协议写成真实目录，不放进事务：失败时不会留下指向不存在数据的公开题目。
        String location = publishTestData();
        transactions.executeWithoutResult(status -> seed(location));
    }

    private String publishTestData() {
        try {
            var prepared = testData.prepare(PROBLEM_ID, new ByteArrayInputStream(zip()));
            return testData.activate(PROBLEM_ID, prepared);
        }
        catch (TestDataStore.AssetException | IOException error) {
            throw new IllegalStateException("Could not write the development test data", error);
        }
    }

    private static byte[] zip() throws IOException {
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes)) {
            for (var file : new String[][] {{"1.in", "1 2\n"}, {"1.out", "3\n"}}) {
                zip.putNextEntry(new ZipEntry(file[0]));
                zip.write(file[1].getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        return bytes.toByteArray();
    }

    private void seed(String location) {
        jdbc.update("""
                INSERT IGNORE INTO problem
                    (id, slug, visibility, status, code_mode, title, statement_markdown,
                     input_description_markdown, output_description_markdown, constraints_markdown,
                     hint_markdown, difficulty, tags_json, checker_type,
                     created_by, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(?), 'a-plus-b', 'PRIVATE', 'ACTIVE', 'ACM', 'A+B Problem',
                        '计算两个整数之和。', '输入两个整数。', '输出它们的和。',
                        '整数在 32 位有符号范围内。', NULL, 'EASY', JSON_ARRAY('入门'), 'DEFAULT',
                        UUID_TO_BIN(?), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """, PROBLEM_ID, USER_ID);
        jdbc.update("""
                INSERT IGNORE INTO problem_sample
                    (id, problem_id, ordinal, input_text, expected_output_text, explanation_markdown)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 1, '1 2\n', '3\n', NULL)
                """, SAMPLE_ID, PROBLEM_ID);
        jdbc.update("""
                INSERT IGNORE INTO problem_language
                    (problem_id, language_id, display_order, starter_code, judge_template)
                VALUES (UUID_TO_BIN(?), 'cpp', 1,
                        '#include <iostream>\nusing namespace std;\nint main() { return 0; }\n', NULL)
                """, PROBLEM_ID);
        jdbc.update("""
                UPDATE problem
                SET test_data_location = ?, test_data_updated_at = UTC_TIMESTAMP(6),
                    visibility = 'PUBLIC', published_at = COALESCE(published_at, UTC_TIMESTAMP(6)),
                    updated_at = UTC_TIMESTAMP(6)
                WHERE id = UUID_TO_BIN(?)
                """, location, PROBLEM_ID);
    }
}
