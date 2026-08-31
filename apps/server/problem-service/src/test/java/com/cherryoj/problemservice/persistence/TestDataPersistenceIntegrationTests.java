package com.cherryoj.problemservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.TestDataDtos.BindTestDataRequest;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.TestDataService;
import com.cherryoj.problemservice.bootstrap.TestDataRecovery;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class TestDataPersistenceIntegrationTests {

    private static final String ACTOR = "019c8e42-7f70-7000-8000-000000000001";
    private static final Path STORAGE_ROOT = temporaryRoot();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_problem_test_data")
            .withUsername("cherry")
            .withPassword("test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("cherry.problem.test-data.root", () -> STORAGE_ROOT.toString());
    }

    @Autowired
    AdminProblemService admin;

    @Autowired
    TestDataService testData;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    @Autowired
    TestDataRecovery recovery;

    @Test
    void uploadIsImmutableDownloadableDeduplicatedAndBindableWithoutLeaks() throws Exception {
        var problem = createProblem("asset");
        String versionId = problem.versions().getFirst().id();
        byte[] zip = validZip();

        var uploaded = testData.upload(problem.id(), multipart(zip), ACTOR);

        assertThat(uploaded.status().name()).isEqualTo("READY");
        assertThat(uploaded.caseCount()).isEqualTo(2);
        assertThat(uploaded.totalBytes()).isEqualTo(12);
        assertThat(uploaded.manifest().files()).hasSize(4);
        assertThat(json.writeValueAsString(uploaded))
                .doesNotContain("storageRef", "1 2", "expectedOutput", "MANUAL_UPLOAD/");
        try (var asset = testData.openReady(problem.id(), uploaded.id())) {
            assertThat(asset.stream().readAllBytes()).isEqualTo(zip);
            assertThat(asset.contentSha256()).isEqualTo(uploaded.contentSha256());
        }

        var duplicate = testData.upload(problem.id(), multipart(zip), ACTOR);
        assertThat(duplicate.id()).isEqualTo(uploaded.id());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_data_version
                WHERE problem_id = UUID_TO_BIN(?) AND status = 'READY'
                """, Integer.class, problem.id())).isEqualTo(1);

        var bound = testData.bind(problem.id(), versionId, new BindTestDataRequest(uploaded.id(), 0), ACTOR);
        assertThat(bound.rowVersion()).isEqualTo(1);
        assertThat(bound.testDataVersion()).isEqualTo(uploaded);
        assertThatThrownBy(() -> testData.bind(
                problem.id(), versionId, new BindTestDataRequest(uploaded.id(), 0), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("ROW_VERSION_CONFLICT"));

        assertThat(Files.readAllBytes(STORAGE_ROOT.resolve("assets/" + uploaded.id() + ".zip"))).isEqualTo(zip);
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles).isEmpty();
        }
    }

    @Test
    void invalidUploadBecomesFailedAndCrossProblemBindingIsRejected() throws Exception {
        var first = createProblem("invalid");
        var second = createProblem("other");
        byte[] orphan = zip(Map.of("1.in", bytes("1")));

        assertThatThrownBy(() -> testData.upload(first.id(), multipart(orphan), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("INVALID_TEST_DATA_ARCHIVE"));
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_data_version
                WHERE problem_id = UUID_TO_BIN(?) AND status = 'FAILED' AND error_message = 'TEST_DATA_CASE_PAIR_REQUIRED'
                """, Integer.class, first.id())).isEqualTo(1);
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles).isEmpty();
        }

        var uploaded = testData.upload(first.id(), multipart(validZip()), ACTOR);
        assertThatThrownBy(() -> testData.bind(
                second.id(), second.versions().getFirst().id(), new BindTestDataRequest(uploaded.id(), 0), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT"));
    }

    @Test
    void concurrentIdenticalUploadsConvergeOnOneReadyAsset() throws Exception {
        var problem = createProblem("dedupe-race");
        byte[] zip = validZip();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> uploadAfter(start, problem.id(), zip));
            var second = executor.submit(() -> uploadAfter(start, problem.id(), zip));
            start.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_data_version
                WHERE problem_id = UUID_TO_BIN(?) AND status = 'READY'
                """, Integer.class, problem.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM problem_audit_event
                WHERE problem_id = UUID_TO_BIN(?) AND action = 'TEST_DATA_REUSED'
                """, Integer.class, problem.id())).isEqualTo(1);
    }

    @Test
    void startupRecoveryFailsStaleUploadAndDeletesOnlyItsBoundedFiles() throws Exception {
        var problem = createProblem("recovery");
        String id = UUID.randomUUID().toString();
        LocalDateTime old = LocalDateTime.now(ZoneOffset.UTC).minusDays(2);
        jdbc.update("""
                INSERT INTO test_data_version (id, problem_id, status, source_type, storage_ref,
                    created_by, created_at)
                VALUES (UUID_TO_BIN(?), UUID_TO_BIN(?), 'UPLOADING', 'MANUAL_UPLOAD', ?, UUID_TO_BIN(?), ?)
                """, id, problem.id(), "assets/" + id + ".zip", ACTOR, old);
        Path partial = STORAGE_ROOT.resolve("tmp/" + id + ".upload");
        Path orphan = STORAGE_ROOT.resolve("assets/" + id + ".zip");
        Files.write(partial, bytes("partial"));
        Files.write(orphan, bytes("orphan"));
        FileTime oldTime = FileTime.from(Instant.now().minusSeconds(172_800));
        Files.setLastModifiedTime(partial, oldTime);
        Files.setLastModifiedTime(orphan, oldTime);

        recovery.run(new DefaultApplicationArguments(new String[0]));

        assertThat(jdbc.queryForObject("""
                SELECT status FROM test_data_version WHERE id = UUID_TO_BIN(?)
                """, String.class, id)).isEqualTo("FAILED");
        assertThat(jdbc.queryForObject("""
                SELECT error_message FROM test_data_version WHERE id = UUID_TO_BIN(?)
                """, String.class, id)).isEqualTo("UPLOAD_INTERRUPTED");
        assertThat(partial).doesNotExist();
        assertThat(orphan).doesNotExist();
    }

    @Test
    void interruptedStreamAndDatabaseFinalizeFailureLeaveFailedMetadataWithoutAssets() throws Exception {
        var interruptedProblem = createProblem("interrupted");
        MultipartFile interrupted = interruptedMultipart(validZip());

        assertThatThrownBy(() -> testData.upload(interruptedProblem.id(), interrupted, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_STORAGE_UNAVAILABLE"));
        assertThat(jdbc.queryForObject("""
                SELECT status FROM test_data_version WHERE problem_id = UUID_TO_BIN(?) ORDER BY created_at DESC LIMIT 1
                """, String.class, interruptedProblem.id())).isEqualTo("FAILED");

        var databaseProblem = createProblem("db-failure");
        BlockingUpload blocked = blockingMultipart(validZip());
        String failedId;
        try (var executor = Executors.newSingleThreadExecutor()) {
            var upload = executor.submit(() -> testData.upload(databaseProblem.id(), blocked.file(), ACTOR));
            blocked.fullyRead().await();
            failedId = jdbc.queryForObject("""
                    SELECT BIN_TO_UUID(id) FROM test_data_version
                    WHERE problem_id = UUID_TO_BIN(?) AND status = 'UPLOADING'
                    ORDER BY created_at DESC LIMIT 1
                    """, String.class, databaseProblem.id());
            jdbc.update("""
                    UPDATE test_data_version SET status = 'FAILED', error_message = 'INJECTED_STATE_CHANGE'
                    WHERE id = UUID_TO_BIN(?) AND status = 'UPLOADING'
                    """, failedId);
            blocked.release().countDown();
            assertThatThrownBy(upload::get)
                    .hasCauseInstanceOf(ProblemApiException.class);
        }
        assertThat(STORAGE_ROOT.resolve("assets/" + failedId + ".zip")).doesNotExist();
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles).isEmpty();
        }
    }

    private String uploadAfter(CountDownLatch start, String problemId, byte[] zip) throws Exception {
        start.await();
        return testData.upload(problemId, multipart(zip), ACTOR).id();
    }

    private com.cherryoj.problemservice.api.AdminProblemDtos.Problem createProblem(String prefix) {
        return admin.create(new CreateProblemRequest(
                prefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                "Test data fixture", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
    }

    private static MockMultipartFile multipart(byte[] content) {
        return new MockMultipartFile("file", "cases.zip", "application/zip", content);
    }

    private static MultipartFile interruptedMultipart(byte[] content) {
        return new MultipartFile() {
            @Override public String getName() { return "file"; }
            @Override public String getOriginalFilename() { return "cases.zip"; }
            @Override public String getContentType() { return "application/zip"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return content.length; }
            @Override public byte[] getBytes() { return content.clone(); }
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    private int position;

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        if (position > 16) throw new IOException("simulated disconnect");
                        if (position == content.length) return -1;
                        int read = Math.min(Math.min(length, 8), content.length - position);
                        System.arraycopy(content, position, buffer, offset, read);
                        position += read;
                        return read;
                    }

                    @Override
                    public int read() throws IOException {
                        byte[] one = new byte[1];
                        return read(one, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(one[0]);
                    }
                };
            }
            @Override public void transferTo(java.io.File destination) { throw new UnsupportedOperationException(); }
        };
    }

    private static BlockingUpload blockingMultipart(byte[] content) {
        CountDownLatch fullyRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        MultipartFile file = new MultipartFile() {
            @Override public String getName() { return "file"; }
            @Override public String getOriginalFilename() { return "cases.zip"; }
            @Override public String getContentType() { return "application/zip"; }
            @Override public boolean isEmpty() { return false; }
            @Override public long getSize() { return content.length; }
            @Override public byte[] getBytes() { return content.clone(); }
            @Override public InputStream getInputStream() {
                return new InputStream() {
                    private int position;

                    @Override
                    public int read(byte[] buffer, int offset, int length) throws IOException {
                        if (position == content.length) {
                            fullyRead.countDown();
                            try {
                                release.await();
                            }
                            catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new IOException(error);
                            }
                            return -1;
                        }
                        int read = Math.min(length, content.length - position);
                        System.arraycopy(content, position, buffer, offset, read);
                        position += read;
                        return read;
                    }

                    @Override
                    public int read() throws IOException {
                        byte[] one = new byte[1];
                        return read(one, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(one[0]);
                    }
                };
            }
            @Override public void transferTo(java.io.File destination) { throw new UnsupportedOperationException(); }
        };
        return new BlockingUpload(file, fullyRead, release);
    }

    private record BlockingUpload(MultipartFile file, CountDownLatch fullyRead, CountDownLatch release) {
    }

    private static byte[] validZip() throws Exception {
        return zip(Map.of(
                "1.in", bytes("1 2\n"), "1.out", bytes("3\n"),
                "2.in", bytes("4 5\n"), "2.out", bytes("9\n")));
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(output)) {
            for (var entry : entries.entrySet()) {
                archive.putArchiveEntry(new ZipArchiveEntry(entry.getKey()));
                archive.write(entry.getValue());
                archive.closeArchiveEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static Path temporaryRoot() {
        try {
            return Files.createTempDirectory("cherry-problem-test-data-");
        }
        catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
