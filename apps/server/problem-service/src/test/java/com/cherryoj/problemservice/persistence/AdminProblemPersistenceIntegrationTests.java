package com.cherryoj.problemservice.persistence;

import static com.cherryoj.problemservice.persistence.ProblemFixtures.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.AdminProblemDtos.SampleInput;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.PublicProblemService;
import com.cherryoj.problemservice.application.TestDataService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class AdminProblemPersistenceIntegrationTests {

    private static final Path STORAGE_ROOT = temporaryRoot();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_problem_admin")
            .withUsername("cherry")
            .withPassword("test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("cherry.problem.test-data.root", () -> STORAGE_ROOT.toString());
        registry.add("cherry.problem.test-data.recovery-enabled", () -> "false");
    }

    @Autowired
    AdminProblemService admin;

    @Autowired
    PublicProblemService publicProblems;

    @Autowired
    TestDataService testData;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void createSavePreviewConflictAndRollbackAreAtomic() {
        String slug = "managed-" + UUID.randomUUID().toString().substring(0, 8);
        var created = admin.create(new CreateProblemRequest(slug, "Managed", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
        assertThat(created.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(created.testData()).isNull();

        UpdateProblemRequest update = new UpdateProblemRequest(
                slug, "Managed A+B", "statement", "input", "output", "constraints", null, Difficulty.EASY,
                List.of("入门", "数学"),
                List.of(new SampleInput(1, "1 2\n", "3\n", null), new SampleInput(2, "4 5\n", "9\n", "解释")),
                "int main() {}", 0);
        var saved = admin.update(created.id(), update, ACTOR);
        assertThat(saved.rowVersion()).isEqualTo(1);
        assertThat(saved.samples()).extracting(sample -> sample.ordinal()).containsExactly(1, 2);
        assertThat(saved.allowedLanguages().getFirst().starterCode()).isEqualTo("int main() {}");
        assertThat(admin.preview(created.id()).statementMarkdown()).isEqualTo("statement");

        // 同一个 rowVersion 再提交一次：乐观锁拒绝，内容不变
        assertThatThrownBy(() -> admin.update(created.id(), update, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("ROW_VERSION_CONFLICT"));

        // 样例序号不连续：整次修改回滚，标题、样例都保持原样
        UpdateProblemRequest invalidSamples = new UpdateProblemRequest(
                slug, "Should rollback", "statement", "input", "output", null, null, Difficulty.EASY,
                List.of(), List.of(new SampleInput(2, "x", "y", null)), "", 1);
        assertThatThrownBy(() -> admin.update(created.id(), invalidSamples, ACTOR))
                .isInstanceOf(ProblemApiException.class);
        var unchanged = admin.getProblem(created.id());
        assertThat(unchanged.title()).isEqualTo("Managed A+B");
        assertThat(unchanged.samples()).hasSize(2);
        assertThat(unchanged.rowVersion()).isEqualTo(1);

        List<String> actions = jdbc.queryForList("""
                SELECT action FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?) ORDER BY created_at
                """, String.class, created.id());
        assertThat(actions).containsExactly("PROBLEM_CREATED", "PROBLEM_UPDATED");
        String details = jdbc.queryForObject("""
                SELECT CAST(JSON_ARRAYAGG(detail_json) AS CHAR) FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?)
                """, String.class, created.id());
        assertThat(details).doesNotContain("int main", "1 2", "statement");
    }

    /** 题目没有版本，改了就是改了：公开题目的编辑立即对读者生效，但不能被改成读不懂的样子。 */
    @Test
    void editsToAPublicProblemTakeEffectImmediatelyButCannotBlankTheStatement() {
        String slug = "live-" + UUID.randomUUID().toString().substring(0, 8);
        ProblemFixtures.insertPublic(jdbc, slug, "Live problem", "EASY", "live", LocalDateTime.now(ZoneOffset.UTC));
        var before = admin.getProblem(admin.list(slug, null, 1, 10).items().getFirst().id());
        assertThat(publicProblems.detail(slug).statementMarkdown()).isEqualTo("statement");

        var edited = admin.update(before.id(), new UpdateProblemRequest(
                slug, "Live problem v2", "new statement", "input", "output", null, null, Difficulty.HARD,
                List.of("live"), List.of(new SampleInput(1, "1", "1", null)), "int main() {}", before.rowVersion()), ACTOR);

        assertThat(edited.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(publicProblems.detail(slug).statementMarkdown()).isEqualTo("new statement");
        assertThat(publicProblems.detail(slug).title()).isEqualTo("Live problem v2");

        assertThatThrownBy(() -> admin.update(before.id(), new UpdateProblemRequest(
                slug, "Blank", "", "input", "output", null, null, Difficulty.HARD,
                List.of(), List.of(new SampleInput(1, "1", "1", null)), "", edited.rowVersion()), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
        assertThatThrownBy(() -> admin.update(before.id(), new UpdateProblemRequest(
                slug, "No samples", "statement", "input", "output", null, null, Difficulty.HARD,
                List.of(), List.of(), "", edited.rowVersion()), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
        assertThat(publicProblems.detail(slug).statementMarkdown()).isEqualTo("new statement");
    }

    @Test
    void slugAndUpdateConcurrencyHaveOneWinner() throws Exception {
        String slug = "concurrent-" + UUID.randomUUID().toString().substring(0, 8);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> createAfter(start, slug));
            var second = executor.submit(() -> createAfter(start, slug));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("CREATED", "SLUG_CONFLICT");
        }

        var problem = admin.create(new CreateProblemRequest(
                "race-" + UUID.randomUUID().toString().substring(0, 8), "Race", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
        CountDownLatch updateStart = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> updateAfter(updateStart, problem.id(), problem.slug(), "first", 0));
            var second = executor.submit(() -> updateAfter(updateStart, problem.id(), problem.slug(), "second", 0));
            updateStart.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("UPDATED", "ROW_VERSION_CONFLICT");
        }
        assertThat(admin.getProblem(problem.id()).rowVersion()).isEqualTo(1);
    }

    @Test
    void archiveKeepsTheProblemAndImmediatelyRemovesPublicRead() {
        String slug = "archive-" + UUID.randomUUID().toString().substring(0, 8);
        ProblemFixtures.insertPublic(jdbc, slug, "Published fixture", "MEDIUM", "fixture", LocalDateTime.now(ZoneOffset.UTC));
        var before = admin.getProblem(admin.list(slug, null, 1, 10).items().getFirst().id());
        assertThat(publicProblems.detail(slug).title()).isEqualTo("Published fixture");

        var archived = admin.archive(before.id(), before.rowVersion(), ACTOR);

        assertThat(archived.status().name()).isEqualTo("ARCHIVED");
        assertThat(archived.samples()).isNotEmpty();
        assertThatThrownBy(() -> publicProblems.detail(slug))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("PROBLEM_NOT_FOUND"));
    }

    @Test
    void onlyNeverPublishedProblemsCanBeDeletedAndTheirTestDataGoesWithThem() throws Exception {
        var draft = admin.create(new CreateProblemRequest(
                "draft-" + UUID.randomUUID().toString().substring(0, 8), "Draft", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
        var uploaded = testData.upload(draft.id(), new MockMultipartFile(
                "file", "cases.zip", "application/zip", zip()), ACTOR);
        Path address = STORAGE_ROOT.resolve(draft.id());
        assertThat(Files.exists(address, LinkOption.NOFOLLOW_LINKS)).isTrue();
        assertThat(uploaded.digest()).matches("[a-f0-9]{64}");

        admin.delete(draft.id(), admin.getProblem(draft.id()).rowVersion());

        assertThatThrownBy(() -> admin.getProblem(draft.id()))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("PROBLEM_NOT_FOUND"));
        // 审计事件与样例、语言一起删掉，数据目录和所有代际也删掉；别的题目的数据不受影响
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?)",
                Integer.class, draft.id())).isZero();
        assertThat(Files.exists(address, LinkOption.NOFOLLOW_LINKS)).isFalse();
        try (var generations = Files.list(STORAGE_ROOT.resolve(".store"))) {
            assertThat(generations.map(path -> path.getFileName().toString()))
                    .noneMatch(name -> name.startsWith(draft.id()));
        }

        String slug = "never-delete-" + UUID.randomUUID().toString().substring(0, 8);
        ProblemFixtures.insertPublic(jdbc, slug, "Published", "EASY", "x", LocalDateTime.now(ZoneOffset.UTC));
        var published = admin.getProblem(admin.list(slug, null, 1, 10).items().getFirst().id());
        assertThatThrownBy(() -> admin.delete(published.id(), published.rowVersion()))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT"));
        // 公开过的题目哪怕转回私有也不能删：它可能已经有提交
        assertThat(admin.getProblem(published.id()).slug()).isEqualTo(slug);
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

    private String updateAfter(CountDownLatch start, String problemId, String slug, String title, long rowVersion)
            throws Exception {
        start.await();
        try {
            admin.update(problemId, new UpdateProblemRequest(slug, title, "s", "i", "o", null, null, Difficulty.EASY,
                    List.of(), List.of(), "", rowVersion), ACTOR);
            return "UPDATED";
        }
        catch (ProblemApiException error) {
            return error.code();
        }
    }

    private static byte[] zip() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(output)) {
            for (String[] file : new String[][] {{"1.in", "1 2\n"}, {"1.out", "3\n"}}) {
                archive.putArchiveEntry(new ZipArchiveEntry(file[0]));
                archive.write(file[1].getBytes(StandardCharsets.UTF_8));
                archive.closeArchiveEntry();
            }
        }
        return output.toByteArray();
    }

    private static Path temporaryRoot() {
        try {
            return Files.createTempDirectory("cherry-admin-problem-");
        }
        catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
