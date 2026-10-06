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
                  AND table_name IN ('problem', 'problem_sample', 'problem_language', 'problem_audit_event')
                """, Integer.class);
        assertThat(tableCount).isEqualTo(4);
        // 题目没有版本：版本表不存在，数据库里也不存测试数据的指纹、清单或存储位置
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN ('problem_version', 'test_data_version')
                """, Integer.class)).isZero();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND table_name = 'problem'
                  AND (column_name LIKE '%version_id' OR column_name IN ('test_data_digest', 'manifest_json', 'storage_ref'))
                """, Integer.class)).isZero();

        seed.run(null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem WHERE slug = 'a-plus-b'", Integer.class))
                .isEqualTo(1);
        // 种子题目的测试数据是真实的协议目录：地址指向的目录里有 testdata.json
        String location = jdbc.queryForObject(
                "SELECT test_data_location FROM problem WHERE slug = 'a-plus-b'", String.class);
        assertThat(java.nio.file.Path.of(location).resolve("testdata.json")).isRegularFile();

        var page = problems.list("A+B", PublicProblemService.Difficulty.EASY, List.of("入门"),
                PublicProblemService.CodeMode.ACM, "cpp", PublicProblemService.Sort.UPDATED_DESC, null, 20);
        assertThat(page.items()).hasSize(1);
        ProblemDetail detail = problems.detail("a-plus-b");
        assertThat(detail.samples()).hasSize(1);
        assertThat(detail.allowedLanguages()).extracting(language -> language.id()).containsExactly("cpp");

        jdbc.update("""
                UPDATE problem_language SET judge_template = 'HIDDEN_CANARY'
                WHERE problem_id = UUID_TO_BIN(?) AND language_id = 'cpp'
                """, detail.problemId());
        assertThat(json.writeValueAsString(problems.detail("a-plus-b")))
                .doesNotContain("HIDDEN_CANARY", "judgeTemplate", "testData", "testDataLocation", "storageRef");

        // 数据库约束：公开的题目必须有测试数据地址和首次公开时间
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO problem
                    (id, slug, visibility, status, code_mode, title, statement_markdown,
                     input_description_markdown, output_description_markdown, difficulty, tags_json,
                     checker_type, created_by, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(UUID()), 'invalid-public', 'PUBLIC', 'ACTIVE', 'ACM', 't', 's', 'i', 'o',
                        'EASY', JSON_ARRAY(), 'DEFAULT', UUID_TO_BIN(UUID()), UTC_TIMESTAMP(6), UTC_TIMESTAMP(6), 0)
                """))
                .isInstanceOf(DataAccessException.class);

        List<Map<String, Object>> plan = jdbc.queryForList("""
                EXPLAIN SELECT p.id
                FROM problem p FORCE INDEX (idx_problem_listing)
                WHERE p.visibility = 'PUBLIC' AND p.status = 'ACTIVE'
                ORDER BY p.updated_at DESC, p.id DESC LIMIT 21
                """);
        assertThat(plan.stream().map(row -> String.valueOf(row.get("key"))))
                .contains("idx_problem_listing");
    }

    @Test
    void keysetPaginationHandlesEqualTimestampsAndConcurrentNewerRows() {
        LocalDateTime sharedTime = LocalDateTime.of(2026, 8, 29, 1, 0);
        for (int index = 0; index < 25; index++) {
            ProblemFixtures.insertPublic(jdbc, "page-" + String.format("%02d", index),
                    "page-" + String.format("%02d", index), "MEDIUM", "pagination", sharedTime);
        }

        var first = problems.list(null, null, null, null, null,
                PublicProblemService.Sort.UPDATED_DESC, null, 10);
        assertThat(first.items()).hasSize(10);
        assertThat(first.hasMore()).isTrue();

        ProblemFixtures.insertPublic(jdbc, "newer-during-pagination", "newer-during-pagination", "MEDIUM",
                "pagination", LocalDateTime.now(ZoneOffset.UTC).plusMinutes(1));

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
}
