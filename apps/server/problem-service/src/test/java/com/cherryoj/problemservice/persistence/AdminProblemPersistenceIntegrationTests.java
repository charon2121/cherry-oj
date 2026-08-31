package com.cherryoj.problemservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateRevisionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.AdminProblemDtos.SampleInput;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateVersionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.VersionStatus;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.PublicProblemService;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(properties = {
        "cherry.problem.test-data.root=${java.io.tmpdir}/cherry-oj-admin-problem-testdata",
        "cherry.problem.test-data.recovery-enabled=false"
})
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class AdminProblemPersistenceIntegrationTests {

    private static final String ACTOR = "019c8e42-7f70-7000-8000-000000000001";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_problem_admin")
            .withUsername("cherry")
            .withPassword("test-password");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired
    AdminProblemService admin;

    @Autowired
    PublicProblemService publicProblems;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createSavePreviewConflictDeleteAndRecreateDraftAreAtomic() {
        String slug = "managed-" + UUID.randomUUID().toString().substring(0, 8);
        var created = admin.create(new CreateProblemRequest(slug, "Managed", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
        assertThat(created.versions()).hasSize(1);
        String versionId = created.versions().getFirst().id();

        UpdateVersionRequest update = new UpdateVersionRequest(
                "Managed A+B", "statement", "input", "output", "constraints", null, Difficulty.EASY,
                List.of("入门", "数学"),
                List.of(new SampleInput(1, "1 2\n", "3\n", null), new SampleInput(2, "4 5\n", "9\n", "解释")),
                "int main() {}", "initial draft", 0);
        var saved = admin.updateVersion(created.id(), versionId, update, ACTOR);
        assertThat(saved.rowVersion()).isEqualTo(1);
        assertThat(saved.samples()).extracting(sample -> sample.ordinal()).containsExactly(1, 2);
        assertThat(admin.preview(created.id(), versionId).statementMarkdown()).isEqualTo("statement");

        assertThatThrownBy(() -> admin.updateVersion(created.id(), versionId, update, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("ROW_VERSION_CONFLICT"));
        assertThat(admin.getVersion(created.id(), versionId).title()).isEqualTo("Managed A+B");

        UpdateVersionRequest invalidSamples = new UpdateVersionRequest(
                "Should rollback", "statement", "input", "output", null, null, Difficulty.EASY,
                List.of(), List.of(new SampleInput(2, "x", "y", null)), "", null, 1);
        assertThatThrownBy(() -> admin.updateVersion(created.id(), versionId, invalidSamples, ACTOR))
                .isInstanceOf(ProblemApiException.class);
        assertThat(admin.getVersion(created.id(), versionId).title()).isEqualTo("Managed A+B");

        admin.deleteDraft(created.id(), versionId, 1, ACTOR);
        assertThat(admin.getProblem(created.id()).versions()).isEmpty();
        var recreated = admin.createRevision(created.id(), new CreateRevisionRequest(0, false), ACTOR);
        assertThat(recreated.status()).isEqualTo(VersionStatus.DRAFT);

        List<String> actions = jdbc.queryForList("""
                SELECT action FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?) ORDER BY created_at
                """, String.class, created.id());
        assertThat(actions).containsExactly(
                "PROBLEM_CREATED", "DRAFT_UPDATED", "DRAFT_DELETED", "REVISION_CREATED");
        String details = jdbc.queryForObject("""
                SELECT CAST(JSON_ARRAYAGG(detail_json) AS CHAR) FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?)
                """, String.class, created.id());
        assertThat(details).doesNotContain("int main", "1 2", "statement");
    }

    @Test
    void revisionCopiesPublishedContentAndKeepsSourceImmutable() {
        String problemId = insertPublished("revision-" + UUID.randomUUID().toString().substring(0, 8));
        var problem = admin.getProblem(problemId);
        String sourceId = problem.currentPublishedVersionId();

        var revision = admin.createRevision(problemId, new CreateRevisionRequest(problem.rowVersion(), false), ACTOR);
        assertThat(revision.versionNo()).isEqualTo(2);
        assertThat(revision.title()).isEqualTo("Published fixture");
        assertThat(revision.samples()).hasSize(1);
        assertThat(jdbc.queryForObject("""
                SELECT test_data_version_id IS NULL FROM problem_version WHERE id = UUID_TO_BIN(?)
                """, Boolean.class, revision.id())).isTrue();

        UpdateVersionRequest attempt = new UpdateVersionRequest(
                "Mutated", revision.statementMarkdown(), revision.inputDescriptionMarkdown(),
                revision.outputDescriptionMarkdown(), null, null, revision.difficulty(), revision.tags(),
                List.of(), "", null, 0);
        assertThatThrownBy(() -> admin.updateVersion(problemId, sourceId, attempt, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT"));
        assertThat(admin.getVersion(problemId, sourceId).title()).isEqualTo("Published fixture");
    }

    @Test
    void slugAndRevisionConcurrencyHaveOneWinner() throws Exception {
        String slug = "concurrent-" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createAfter(start, slug));
            var second = executor.submit(() -> createAfter(start, slug));
            start.countDown();
            List<String> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "SLUG_CONFLICT");
        }

        String problemId = insertPublished("version-race-" + UUID.randomUUID().toString().substring(0, 8));
        long rowVersion = admin.getProblem(problemId).rowVersion();
        CountDownLatch revisionStart = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> reviseAfter(revisionStart, problemId, rowVersion));
            var second = executor.submit(() -> reviseAfter(revisionStart, problemId, rowVersion));
            revisionStart.countDown();
            List<String> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).containsExactlyInAnyOrder("CREATED", "ROW_VERSION_CONFLICT");
        }
    }

    @Test
    void archiveKeepsHistoryAndImmediatelyRemovesPublicRead() {
        String problemId = insertPublished("archive-" + UUID.randomUUID().toString().substring(0, 8));
        var before = admin.getProblem(problemId);
        assertThat(publicProblems.detail(before.slug()).title()).isEqualTo("Published fixture");

        var archived = admin.archive(problemId, before.rowVersion(), ACTOR);

        assertThat(archived.status().name()).isEqualTo("ARCHIVED");
        assertThat(archived.versions()).isNotEmpty();
        assertThatThrownBy(() -> publicProblems.detail(before.slug()))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("PROBLEM_NOT_FOUND"));
    }

    private String createAfter(CountDownLatch start, String slug) throws Exception {
        start.await();
        try {
            admin.create(new CreateProblemRequest(slug, "Race", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
            return "CREATED";
        }
        catch (ProblemApiException error) {
            return error.code();
        }
    }

    private String reviseAfter(CountDownLatch start, String problemId, long rowVersion) throws Exception {
        start.await();
        try {
            admin.createRevision(problemId, new CreateRevisionRequest(rowVersion, false), ACTOR);
            return "CREATED";
        }
        catch (ProblemApiException error) {
            return error.code();
        }
    }

    private String insertPublished(String slug) {
        String problemId = UUID.randomUUID().toString();
        String versionId = UUID.randomUUID().toString();
        String testDataId = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                INSERT INTO problem (id, slug, visibility, status, current_published_version_id,
                    created_by, created_at, updated_at, row_version)
                VALUES (UUID_TO_BIN(?), ?, 'PRIVATE', 'ACTIVE', NULL, UUID_TO_BIN(?), ?, ?, 0)
                """, problemId, slug, ACTOR, now, now);
        jdbc.update("""
                INSERT INTO test_data_version (id, problem_id, status, source_type, storage_ref, content_sha256,
                    case_count, total_bytes, manifest_json, created_by, created_at, ready_at, error_message)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'READY', 'MANUAL_UPLOAD', ?, UNHEX(REPEAT('2', 64)),
                    1, 1, JSON_OBJECT('caseCount', 1, 'totalBytes', 1, 'files', JSON_ARRAY()),
                    UUID_TO_BIN(?), ?, ?, NULL)
                """, testDataId, problemId, "fixture/" + slug, ACTOR, now, now);
        jdbc.update("""
                INSERT INTO problem_version (id, problem_id, version_no, status, code_mode, title,
                    statement_markdown, input_description_markdown, output_description_markdown,
                    constraints_markdown, hint_markdown, difficulty, tags_json, checker_type,
                    test_data_version_id, change_summary, created_by, published_by,
                    created_at, updated_at, published_at, row_version)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 1, 'PUBLISHED', 'ACM', 'Published fixture',
                    'statement', 'input', 'output', NULL, NULL, 'MEDIUM', JSON_ARRAY('fixture'), 'DEFAULT',
                    UUID_TO_BIN(?), NULL, UUID_TO_BIN(?), UUID_TO_BIN(?), ?, ?, ?, 0)
                """, versionId, problemId, testDataId, ACTOR, ACTOR, now, now, now);
        jdbc.update("""
                INSERT INTO problem_sample (id, problem_version_id, ordinal, input_text, expected_output_text)
                VALUES (UUID_TO_BIN(UUID()), UUID_TO_BIN(?), 1, '1', '1')
                """, versionId);
        jdbc.update("""
                INSERT INTO problem_version_language (problem_version_id, language_id, display_order, starter_code, judge_template)
                VALUES (UUID_TO_BIN(?), 'cpp', 1, 'int main() {}', NULL)
                """, versionId);
        jdbc.update("""
                UPDATE problem SET visibility = 'PUBLIC', current_published_version_id = UUID_TO_BIN(?)
                WHERE id = UUID_TO_BIN(?)
                """, versionId, problemId);
        return problemId;
    }
}
