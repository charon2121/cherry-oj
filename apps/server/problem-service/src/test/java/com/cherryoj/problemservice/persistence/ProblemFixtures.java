package com.cherryoj.problemservice.persistence;

import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** 集成测试直接用 SQL 造「已经公开」的题目，不经过服务层，所以能指定任意的更新时间与内容。 */
final class ProblemFixtures {

    static final String ACTOR = "019c8e42-7f70-7000-8000-000000000001";

    private ProblemFixtures() {
    }

    /** 公开题目要求有测试数据地址与首次公开时间（数据库约束），这里的地址只是占位，不指向真实文件。 */
    static String insertPublic(
            JdbcTemplate jdbc, String slug, String title, String difficulty, String tag, LocalDateTime updatedAt) {
        String problemId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO problem
                    (id, slug, visibility, status, code_mode, title, statement_markdown,
                     input_description_markdown, output_description_markdown, constraints_markdown,
                     hint_markdown, difficulty, tags_json, checker_type, test_data_location,
                     test_data_updated_at, created_by, created_at, updated_at, published_at, row_version)
                VALUES (UUID_TO_BIN(?), ?, 'PUBLIC', 'ACTIVE', 'ACM', ?, 'statement', 'input', 'output',
                        NULL, NULL, ?, JSON_ARRAY(?), 'DEFAULT', ?, ?, UUID_TO_BIN(?), ?, ?, ?, 0)
                """, problemId, slug, title, difficulty, tag, "/tmp/fixture/" + problemId, updatedAt, ACTOR,
                updatedAt, updatedAt, updatedAt);
        jdbc.update("""
                INSERT INTO problem_sample (id, problem_id, ordinal, input_text, expected_output_text)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), 1, '1', '1')
                """, problemId);
        jdbc.update("""
                INSERT INTO problem_language (problem_id, language_id, display_order, starter_code, judge_template)
                VALUES (UUID_TO_BIN(?), 'cpp', 1, 'int main() {}', NULL)
                """, problemId);
        return problemId;
    }
}
