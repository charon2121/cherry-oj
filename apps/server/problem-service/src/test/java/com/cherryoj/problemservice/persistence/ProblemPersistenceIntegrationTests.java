package com.cherryoj.problemservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.PublicProblemDtos.ProblemDetail;
import com.cherryoj.problemservice.application.PublicProblemService;
import com.cherryoj.problemservice.bootstrap.DevProblemSeed;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "cherry.problem.test-data.root=${java.io.tmpdir}/cherry-oj-public-problem-testdata",
        "cherry.problem.test-data.recovery-enabled=false"
})
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class ProblemPersistenceIntegrationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_problem")
            .withUsername("cherry")
            .withPassword("test-password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PublicProblemService problems;

    @Autowired
    DevProblemSeed seed;

    @Autowired
    ObjectMapper json;

    @Test
    void migrationSeedQueriesAndPublicWhitelistHoldOnMysql84() throws Exception {
        Integer tableCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE()
                  AND table_name IN ('problem', 'test_data_version', 'problem_version',
                                     'problem_sample', 'problem_version_language', 'problem_audit_event')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(6);

        seed.run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem WHERE slug = 'a-plus-b'", Integer.class))
                .isEqualTo(1);

        var page = problems.list("A+B", PublicProblemService.Difficulty.EASY, List.of("入门"),
                PublicProblemService.CodeMode.ACM, "cpp", PublicProblemService.Sort.UPDATED_DESC, null, 20);
        assertThat(page.items()).hasSize(1);
        ProblemDetail detail = problems.detail("a-plus-b");
        assertThat(detail.samples()).hasSize(1);
        assertThat(detail.allowedLanguages()).extracting(language -> language.id()).containsExactly("cpp");

        jdbc.update("""
                UPDATE problem_version_language SET judge_template = 'HIDDEN_CANARY'
                WHERE problem_version_id = UUID_TO_BIN(?) AND language_id = 'cpp'
                """, detail.problemVersionId());
        assertThat(json.writeValueAsString(problems.detail("a-plus-b")))
                .doesNotContain("HIDDEN_CANARY", "judgeTemplate", "testDataVersionId", "storageRef");

        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO problem
                    (id, slug, visibility, status, current_published_version_id,
                     created_by, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(UUID()), 'invalid-public', 'PUBLIC', 'ACTIVE', NULL,
                        UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """))
                .isInstanceOf(DataAccessException.class);

        List<Map<String, Object>> plan = jdbc.queryForList("""
                EXPLAIN SELECT p.id
                FROM problem p FORCE INDEX (idx_problem_listing)
                JOIN problem_version v ON v.id = p.current_published_version_id
                WHERE p.visibility = 'PUBLIC' AND p.status = 'ACTIVE' AND v.status = 'PUBLISHED'
                ORDER BY p.updated_at DESC, p.id DESC LIMIT 21
                """);
        assertThat(plan.stream().map(row -> String.valueOf(row.get("key"))))
                .contains("idx_problem_listing");
    }

    @Test
    void keysetPaginationHandlesEqualTimestampsAndConcurrentNewerRows() {
        LocalDateTime sharedTime = LocalDateTime.of(2026, 8, 29, 1, 0);
        for (int index = 0; index < 25; index++) {
            insertPublished("page-" + String.format("%02d", index), sharedTime, "pagination");
        }

        var first = problems.list(null, null, null, null, null,
                PublicProblemService.Sort.UPDATED_DESC, null, 10);
        assertThat(first.items()).hasSize(10);
        assertThat(first.hasMore()).isTrue();

        insertPublished("newer-during-pagination", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1), "pagination");

        List<String> seen = new ArrayList<>(first.items().stream().map(item -> item.problemId()).toList());
        String cursor = first.nextCursor();
        while (cursor != null) {
            var page = problems.list(null, null, null, null, null,
                    PublicProblemService.Sort.UPDATED_DESC, cursor, 10);
            seen.addAll(page.items().stream().map(item -> item.problemId()).toList());
            cursor = page.nextCursor();
        }
        assertThat(seen).hasSize(26);
        assertThat(new HashSet<>(seen)).hasSize(26);

        var filtered = problems.list(null, PublicProblemService.Difficulty.MEDIUM,
                List.of("pagination"), PublicProblemService.CodeMode.ACM, "cpp",
                PublicProblemService.Sort.TITLE_ASC, null, 100);
        assertThat(filtered.items()).hasSize(26);
        assertThat(filtered.items()).extracting(item -> item.title()).isSorted();
    }

    private void insertPublished(String slug, LocalDateTime updatedAt, String tag) {
        String problemId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        String testDataId = UUID.randomUUID().toString();
        String userId = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO problem
                    (id, slug, visibility, status, current_published_version_id,
                     created_by, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(?), ?, 'PRIVATE', 'ACTIVE', NULL,
                        UUID_TO_BIN(?), ?, ?, 0)
                """, problemId, slug, userId, updatedAt, updatedAt);
        jdbc.update("""
                INSERT INTO test_data_version
                    (id, problem_id, status, source_type, storage_ref, content_sha256,
                     case_count, total_bytes, manifest_json, created_by, created_at, ready_at, error_message)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'READY', 'MANUAL_UPLOAD', ?,
                        UNHEX(REPEAT('1', 64)), 1, 1,
                        JSON_OBJECT('caseCount', 1, 'totalBytes', 1, 'files', JSON_ARRAY()),
                        UUID_TO_BIN(?), ?, ?, NULL)
                """, testDataId, problemId, "test/" + slug + ".zip", userId, updatedAt, updatedAt);
        jdbc.update("""
                INSERT INTO problem_version
                    (id, problem_id, version_no, status, code_mode, title, statement_markdown,
                     input_description_markdown, output_description_markdown, constraints_markdown,
                     hint_markdown, difficulty, tags_json, checker_type, test_data_version_id,
                     change_summary, created_by, published_by, created_at, updated_at, published_at, row_version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 1, 'PUBLISHED', 'ACM', ?,
                        'statement', 'input', 'output', NULL, NULL, 'MEDIUM', JSON_ARRAY(?), 'DEFAULT',
                        UUID_TO_BIN(?), NULL, UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, 0)
                """, versionId, problemId, slug, tag, testDataId, userId, userId, updatedAt, updatedAt, updatedAt);
        jdbc.update("""
                INSERT INTO problem_version_language
                    (problem_version_id, language_id, display_order, starter_code, judge_template)
                VALUES (UUID_TO_BIN(?), 'cpp', 1, 'int main() {}', NULL)
                """, versionId);
        jdbc.update("""
                UPDATE problem SET visibility = 'PUBLIC', current_published_version_id = UUID_TO_BIN(?)
                WHERE id = UUID_TO_BIN(?)
                """, versionId, problemId);
    }
}
