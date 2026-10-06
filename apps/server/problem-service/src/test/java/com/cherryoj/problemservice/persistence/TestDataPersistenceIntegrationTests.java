package com.cherryoj.problemservice.persistence;

import static com.cherryoj.problemservice.persistence.ProblemFixtures.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.TestDataService;
import com.cherryoj.problemservice.bootstrap.TestDataRecovery;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class TestDataPersistenceIntegrationTests {

    private static final Path STORAGE_ROOT = temporaryRoot();
    /** docs/testdata-protocol.md 的算法对 {@link #goldenZip()} 的结果；Go、Python 的测试钉着同一个值。 */
    private static final String GOLDEN_DIGEST = "6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4";

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
    void uploadWritesAProtocolDirectoryRecordsOnlyTheAddressAndServesJudging() throws Exception {
        var problem = createProblem("asset");
        assertThatThrownBy(() -> testData.forJudging(problem.id()))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_NOT_FOUND"));

        var uploaded = testData.upload(problem.id(), multipart(goldenZip()), ACTOR);

        assertThat(uploaded.digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(uploaded.caseCount()).isEqualTo(2);
        assertThat(uploaded.totalBytes()).isEqualTo(16);
        assertThat(uploaded.manifest().files()).extracting(file -> file.name())
                .containsExactly("1.in", "1.out", "2.in", "2.out");
        // 对外的数据里没有服务器路径，也没有测试数据内容
        assertThat(json.writeValueAsString(uploaded)).doesNotContain(STORAGE_ROOT.toString(), "location", "1 2\\n");

        // 数据库里只有地址，没有指纹、清单或数据本身
        String location = jdbc.queryForObject(
                "SELECT test_data_location FROM problem WHERE id = UUID_TO_BIN(?)", String.class, problem.id());
        assertThat(location).isEqualTo(STORAGE_ROOT.toAbsolutePath().normalize().resolve(problem.id()).toString());
        assertThat(Files.readString(Path.of(location).resolve("testdata.json"))).contains(GOLDEN_DIGEST);
        assertThat(Files.readString(Path.of(location).resolve("2.out"))).isEqualTo("93\n");

        // judging-service 取到的就是这个地址和 testdata.json 里的指纹
        var forJudging = testData.forJudging(problem.id());
        assertThat(forJudging.location()).isEqualTo(location);
        assertThat(forJudging.digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(forJudging.caseCount()).isEqualTo(2);
        assertThat(forJudging.totalBytes()).isEqualTo(16);

        // 管理端读到同样的数据；上传不改乐观锁计数，免得让正在编辑题面的人白白冲突
        var reloaded = admin.getProblem(problem.id());
        assertThat(reloaded.testData().digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(reloaded.rowVersion()).isEqualTo(problem.rowVersion());
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles).isEmpty();
        }
    }

    @Test
    void replacingTheDataKeepsTheAddressAndChangesTheDigestImmediately() throws Exception {
        var problem = createProblem("replace");
        var first = testData.upload(problem.id(), multipart(goldenZip()), ACTOR);
        String address = testData.forJudging(problem.id()).location();

        var second = testData.upload(problem.id(), multipart(zip(Map.of(
                "1.in", bytes("5 6\n"), "1.out", bytes("11\n")))), ACTOR);

        assertThat(second.digest()).isNotEqualTo(first.digest());
        assertThat(second.caseCount()).isEqualTo(1);
        // 地址不变，数据变了：「改了就是改了」
        var current = testData.forJudging(problem.id());
        assertThat(current.location()).isEqualTo(address);
        assertThat(current.digest()).isEqualTo(second.digest());
        assertThat(Files.readString(Path.of(address).resolve("1.out"))).isEqualTo("11\n");

        // 再换一次：只保留当前与上一代，磁盘占用有界
        testData.upload(problem.id(), multipart(zip(Map.of("1.in", bytes("7\n"), "1.out", bytes("8\n")))), ACTOR);
        try (var generations = Files.list(STORAGE_ROOT.resolve(".store"))) {
            assertThat(generations.map(path -> path.getFileName().toString())
                    .filter(name -> name.startsWith(problem.id())).toList()).hasSize(2);
        }
        assertThat(jdbc.queryForList("""
                SELECT action FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?) ORDER BY created_at
                """, String.class, problem.id()))
                .containsExactly("PROBLEM_CREATED", "TEST_DATA_UPLOADED", "TEST_DATA_UPLOADED", "TEST_DATA_UPLOADED");
    }

    @Test
    void anInvalidUploadLeavesTheExistingDataUntouchedAndNoResidue() throws Exception {
        var problem = createProblem("invalid");
        var good = testData.upload(problem.id(), multipart(goldenZip()), ACTOR);
        byte[] orphan = zip(Map.of("1.in", bytes("1")));

        assertThatThrownBy(() -> testData.upload(problem.id(), multipart(orphan), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("INVALID_TEST_DATA_ARCHIVE");
                    assertThat(error.getMessage()).isEqualTo("每个测试点都必须同时包含同名的 .in 和 .out 文件。");
                });

        assertThat(testData.forJudging(problem.id()).digest()).isEqualTo(good.digest());
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles).isEmpty();
        }
    }

    @Test
    void anInterruptedStreamLeavesNoDataAndNoResidue() throws Exception {
        var problem = createProblem("interrupted");

        assertThatThrownBy(() -> testData.upload(problem.id(), interruptedMultipart(goldenZip()), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_STORAGE_UNAVAILABLE"));

        assertThat(admin.getProblem(problem.id()).testData()).isNull();
        assertThat(Files.exists(STORAGE_ROOT.resolve(problem.id()), LinkOption.NOFOLLOW_LINKS)).isFalse();
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles).isEmpty();
        }
    }

    @Test
    void archivedProblemsRejectUploads() throws Exception {
        var problem = createProblem("archived");
        admin.archive(problem.id(), problem.rowVersion(), ACTOR);

        assertThatThrownBy(() -> testData.upload(problem.id(), multipart(goldenZip()), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT"));
        assertThat(Files.exists(STORAGE_ROOT.resolve(problem.id()), LinkOption.NOFOLLOW_LINKS)).isFalse();
    }

    /** 两个管理员同时上传不同的数据：两次都成功，最终只有一份完整、自洽的数据，地址与指纹始终对得上。 */
    @Test
    void concurrentUploadsOfDifferentDataEndInOneConsistentState() throws Exception {
        var problem = createProblem("race");
        byte[] first = goldenZip();
        byte[] second = zip(Map.of("1.in", bytes("5 6\n"), "1.out", bytes("11\n")));
        CountDownLatch start = new CountDownLatch(1);
        List<String> digests;
        try (var executor = Executors.newFixedThreadPool(2)) {
            var one = executor.submit(() -> uploadAfter(start, problem.id(), first));
            var two = executor.submit(() -> uploadAfter(start, problem.id(), second));
            start.countDown();
            digests = List.of(one.get(), two.get());
        }

        var current = testData.forJudging(problem.id());
        assertThat(digests).contains(current.digest());
        // 地址下的 testdata.json、数据文件与数据库读到的指纹是同一份
        assertThat(Files.readString(Path.of(current.location()).resolve("testdata.json"))).contains(current.digest());
        assertThat(admin.getProblem(problem.id()).testData().digest()).isEqualTo(current.digest());
    }

    @Test
    void startupRecoveryDeletesOnlyStaleTemporaryUploads() throws Exception {
        Path stale = STORAGE_ROOT.resolve("tmp/" + UUID.randomUUID() + ".upload");
        Path fresh = STORAGE_ROOT.resolve("tmp/" + UUID.randomUUID() + ".upload");
        Files.write(stale, bytes("partial"));
        Files.write(fresh, bytes("partial"));
        Files.setLastModifiedTime(stale, FileTime.from(Instant.now().minusSeconds(172_800)));

        recovery.run(new DefaultApplicationArguments(new String[0]));

        assertThat(stale).doesNotExist();
        assertThat(fresh).exists();
        Files.deleteIfExists(fresh);
    }

    @Test
    void unreadableDataIsReportedDifferentlyFromMissingData() throws Exception {
        var problem = createProblem("broken");
        testData.upload(problem.id(), multipart(goldenZip()), ACTOR);
        Path metadata = Path.of(testData.forJudging(problem.id()).location()).resolve("testdata.json");

        Files.writeString(metadata, "not json");
        assertThatThrownBy(() -> testData.forJudging(problem.id()))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_STORAGE_UNAVAILABLE"));
        // 管理端按「没有」展示，公开检查会给出具体原因
        assertThat(admin.getProblem(problem.id()).testData()).isNull();

        Files.delete(metadata);
        assertThatThrownBy(() -> testData.forJudging(problem.id()))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_NOT_FOUND"));
    }

    private String uploadAfter(CountDownLatch start, String problemId, byte[] zip) throws Exception {
        start.await();
        return testData.upload(problemId, multipart(zip), ACTOR).digest();
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

    private static byte[] goldenZip() throws Exception {
        return zip(Map.of(
                "1.in", bytes("1 2\n"), "1.out", bytes("3\n"),
                "2.in", bytes("100 -7\n"), "2.out", bytes("93\n")));
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
