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
import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.storage.TestDataStore;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
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
    /** docs/protocols/testdata.md 的算法对 {@link #goldenZip()} 的结果；Go、Python 的测试钉着同一个值。 */
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

    @Autowired
    TestDataStore store;

    @Autowired
    TestDataStorageProperties properties;

    @Autowired
    AdminProblemMapper mapper;

    @Autowired
    UuidV7 ids;

    @Autowired
    Clock clock;

    @Autowired
    PlatformTransactionManager transactionManager;

    @Test
    void uploadWritesAProtocolDirectoryRecordsOnlyTheAddressAndServesJudging() throws Exception {
        var problem = createProblem("asset");
        assertThatThrownBy(() -> testData.forJudging(problem.id()))
                .isInstanceOfSatisfying(
                        ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_NOT_FOUND"));

        var uploaded = testData.upload(problem.id(), multipart(goldenZip()), ACTOR);

        assertThat(uploaded.digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(uploaded.testcaseCount()).isEqualTo(2);
        assertThat(uploaded.totalBytes()).isEqualTo(16);
        assertThat(uploaded.manifest().files())
                .extracting(file -> file.name())
                .containsExactly("1.in", "1.out", "2.in", "2.out");
        // 对外的数据里没有服务器路径，也没有测试数据内容
        assertThat(json.writeValueAsString(uploaded)).doesNotContain(STORAGE_ROOT.toString(), "location", "1 2\\n");

        // 数据库里只有地址，没有指纹、清单或数据本身
        String location = jdbc.queryForObject(
                "SELECT test_data_location FROM problem WHERE id = UUID_TO_BIN(?)", String.class, problem.id());
        assertThat(Path.of(location).getParent()).isEqualTo(STORAGE_ROOT.resolve(".store/published"));
        assertThat(Path.of(location).getFileName().toString()).startsWith(problem.id() + "-");
        assertThat(Files.readString(Path.of(location).resolve("testdata.json"))).contains(GOLDEN_DIGEST);
        assertThat(Files.readString(Path.of(location).resolve("2.out"))).isEqualTo("93\n");

        // judging-service 取到的就是这个地址和 testdata.json 里的指纹
        var forJudging = testData.forJudging(problem.id());
        assertThat(forJudging.location()).isEqualTo(location);
        assertThat(forJudging.digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(forJudging.testcaseCount()).isEqualTo(2);
        assertThat(forJudging.totalBytes()).isEqualTo(16);
        // 测试点 1 是 4 + 2 字节，测试点 2 是 7 + 3 字节：取单个测试点输入加输出的最大值
        assertThat(forJudging.maxTestcaseIoBytes()).isEqualTo(10);

        // 管理端读到同样的数据；上传不改乐观锁计数，免得让正在编辑题面的人白白冲突
        var reloaded = admin.getProblem(problem.id());
        assertThat(reloaded.testData().digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(reloaded.rowVersion()).isEqualTo(problem.rowVersion());
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles.filter(path -> path.toString().endsWith(".upload")))
                    .isEmpty();
        }
    }

    @Test
    void replacingTheDataPublishesANewAddressAndKeepsThePreviousAddressReadable() throws Exception {
        var problem = createProblem("replace");
        var first = testData.upload(problem.id(), multipart(goldenZip()), ACTOR);
        String address = testData.forJudging(problem.id()).location();

        var second = testData.upload(
                problem.id(), multipart(zip(Map.of("1.in", bytes("5 6\n"), "1.out", bytes("11\n")))), ACTOR);

        assertThat(second.digest()).isNotEqualTo(first.digest());
        assertThat(second.testcaseCount()).isEqualTo(1);
        var current = testData.forJudging(problem.id());
        assertThat(current.location()).isNotEqualTo(address);
        assertThat(current.digest()).isEqualTo(second.digest());
        assertThat(store.describe(address).digest()).isEqualTo(first.digest());
        assertThat(Files.readString(Path.of(address).resolve("2.out"))).isEqualTo("93\n");
        assertThat(Files.readString(Path.of(current.location()).resolve("1.out")))
                .isEqualTo("11\n");

        var third = testData.upload(
                problem.id(), multipart(zip(Map.of("1.in", bytes("7\n"), "1.out", bytes("8\n")))), ACTOR);
        assertThat(testData.forJudging(problem.id()).digest()).isEqualTo(third.digest());
        assertThat(store.describe(address).digest()).isEqualTo(first.digest());
        assertThat(store.describe(current.location()).digest()).isEqualTo(second.digest());
        assertThat(jdbc.queryForList("""
                SELECT action FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?) ORDER BY created_at
                """, String.class, problem.id()))
                .containsExactly("PROBLEM_CREATED", "TEST_DATA_UPLOADED", "TEST_DATA_UPLOADED", "TEST_DATA_UPLOADED");
    }

    @Test
    void auditFailureRollsBackBothTheFirstUploadAndAReplacement() throws Exception {
        var problem = createProblem("audit-failure");
        byte[] initial = goldenZip();
        byte[] replacement = zip(Map.of("1.in", bytes("7\n"), "1.out", bytes("8\n")));

        // UUID_TO_BIN 在插入上传审计时失败，此时事务内已写入新的地址。
        assertStorageFailure(problem.id(), initial);
        assertThat(admin.getProblem(problem.id()).testData()).isNull();
        assertThat(uploadDigests(problem.id())).isEmpty();

        var uploaded = testData.upload(problem.id(), multipart(initial), ACTOR);
        var original = testData.forJudging(problem.id());
        assertStorageFailure(problem.id(), replacement);

        assertThat(testData.forJudging(problem.id())).isEqualTo(original);
        assertThat(admin.getProblem(problem.id()).testData()).isEqualTo(uploaded);
        assertThat(uploadDigests(problem.id())).containsExactly(uploaded.digest());
    }

    @Test
    void anUncertainCommitIsResolvedByTheCommittedUploadAudit() throws Exception {
        var problem = createProblem("commit-result");
        PlatformTransactionManager ambiguousCommit = new PlatformTransactionManager() {
            @Override
            public TransactionStatus getTransaction(TransactionDefinition definition) {
                return transactionManager.getTransaction(definition);
            }

            @Override
            public void commit(TransactionStatus status) {
                transactionManager.commit(status);
                throw new TransactionSystemException("simulated lost commit acknowledgement");
            }

            @Override
            public void rollback(TransactionStatus status) {
                transactionManager.rollback(status);
            }
        };
        var service = new TestDataService(mapper, store, ids, clock, json, properties, ambiguousCommit);
        var uploaded = service.upload(problem.id(), multipart(goldenZip()), ACTOR);

        assertThat(testData.get(problem.id())).isEqualTo(uploaded);
        assertThat(testData.forJudging(problem.id()).digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(uploadDigests(problem.id())).containsExactly(GOLDEN_DIGEST);
    }

    @Test
    void aLegacyAddressRemainsReadableDuringAndAfterTheFirstReplacement() throws Exception {
        var problem = createProblem("legacy");
        testData.upload(problem.id(), multipart(goldenZip()), ACTOR);
        Path source = Path.of(testData.forJudging(problem.id()).location());
        Path legacyTarget = STORAGE_ROOT.resolve(".store").resolve(problem.id() + "-" + GOLDEN_DIGEST.substring(0, 16));
        Files.move(source, legacyTarget);
        Path legacy =
                Files.createSymbolicLink(STORAGE_ROOT.resolve(problem.id()), STORAGE_ROOT.relativize(legacyTarget));
        jdbc.update(
                "UPDATE problem SET test_data_location = ? WHERE id = UUID_TO_BIN(?)", legacy.toString(), problem.id());
        assertThat(testData.forJudging(problem.id()).location()).isEqualTo(legacy.toString());

        testData.upload(problem.id(), multipart(zip(Map.of("1.in", bytes("4\n"), "1.out", bytes("9\n")))), ACTOR);
        var current = testData.forJudging(problem.id());
        assertThat(current.location()).isNotEqualTo(legacy.toString());
        recovery.recover();
        assertThat(store.describe(legacy.toString()).digest()).isEqualTo(GOLDEN_DIGEST);

        Path retired = STORAGE_ROOT.resolve("tmp").resolve(legacyTarget.getFileName() + ".retired");
        Files.setLastModifiedTime(retired, FileTime.from(Instant.now().minusSeconds(172_800)));
        recovery.recover();
        assertThat(Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)).isFalse();
        assertThat(legacyTarget).doesNotExist();
        assertThat(testData.forJudging(problem.id())).isEqualTo(current);
    }

    @Test
    void anInvalidUploadLeavesTheExistingDataUntouchedAndNoResidue() throws Exception {
        var problem = createProblem("invalid");
        var good = testData.upload(problem.id(), multipart(goldenZip()), ACTOR);
        byte[] orphan = zip(Map.of("1.in", bytes("1")));

        assertThatThrownBy(() -> testData.upload(problem.id(), multipart(orphan), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("INVALID_TEST_DATA_ARCHIVE");
                    assertThat(error.getMessage()).isEqualTo("缺少「1.out」：每个测试点都必须同时有同名的 .in 和 .out 文件。");
                });

        assertThat(testData.forJudging(problem.id()).digest()).isEqualTo(good.digest());
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles.filter(path -> path.toString().endsWith(".upload")))
                    .isEmpty();
        }
    }

    @Test
    void anInterruptedStreamLeavesNoDataAndNoResidue() throws Exception {
        var problem = createProblem("interrupted");

        assertThatThrownBy(() -> testData.upload(problem.id(), interruptedMultipart(goldenZip()), ACTOR))
                .isInstanceOfSatisfying(
                        ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_STORAGE_UNAVAILABLE"));

        assertThat(admin.getProblem(problem.id()).testData()).isNull();
        assertThat(Files.exists(STORAGE_ROOT.resolve(problem.id()), LinkOption.NOFOLLOW_LINKS))
                .isFalse();
        try (var temporaryFiles = Files.list(STORAGE_ROOT.resolve("tmp"))) {
            assertThat(temporaryFiles.filter(path -> path.toString().endsWith(".upload")))
                    .isEmpty();
        }
    }

    @Test
    void archivedProblemsRejectUploads() throws Exception {
        var problem = createProblem("archived");
        admin.archive(problem.id(), problem.rowVersion(), ACTOR);

        assertThatThrownBy(() -> testData.upload(problem.id(), multipart(goldenZip()), ACTOR))
                .isInstanceOfSatisfying(
                        ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT"));
        assertThat(Files.exists(STORAGE_ROOT.resolve(problem.id()), LinkOption.NOFOLLOW_LINKS))
                .isFalse();
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
        assertThat(uploadDigests(problem.id())).containsExactlyInAnyOrderElementsOf(digests);
        assertThat(jdbc.queryForObject("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(a.detail_json, '$.digest'))
                FROM problem_audit_event a JOIN problem p ON p.id = a.problem_id AND p.test_data_updated_at = a.created_at
                WHERE p.id = UUID_TO_BIN(?) AND a.action = 'TEST_DATA_UPLOADED'
                """, String.class, problem.id())).isEqualTo(current.digest());
        // 地址下的 testdata.json、数据文件与数据库读到的指纹是同一份
        assertThat(Files.readString(Path.of(current.location()).resolve("testdata.json")))
                .contains(current.digest());
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
                .isInstanceOfSatisfying(
                        ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_STORAGE_UNAVAILABLE"));
        // 管理端按「没有」展示，公开检查会给出具体原因
        assertThat(admin.getProblem(problem.id()).testData()).isNull();

        Files.delete(metadata);
        assertThatThrownBy(() -> testData.forJudging(problem.id()))
                .isInstanceOfSatisfying(
                        ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_NOT_FOUND"));
    }

    private String uploadAfter(CountDownLatch start, String problemId, byte[] zip) throws Exception {
        start.await();
        return testData.upload(problemId, multipart(zip), ACTOR).digest();
    }

    private void assertStorageFailure(String problemId, byte[] content) {
        assertThatThrownBy(() -> testData.upload(problemId, multipart(content), "invalid-audit-actor"))
                .isInstanceOfSatisfying(
                        ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("TEST_DATA_STORAGE_UNAVAILABLE"));
    }

    private List<String> uploadDigests(String problemId) {
        return jdbc.queryForList("""
                SELECT JSON_UNQUOTE(JSON_EXTRACT(detail_json, '$.digest'))
                FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?) AND action = 'TEST_DATA_UPLOADED'
                """, String.class, problemId);
    }

    private com.cherryoj.problemservice.api.AdminProblemDtos.Problem createProblem(String prefix) {
        return admin.create(
                new CreateProblemRequest(
                        prefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                        "Test data fixture",
                        Difficulty.EASY,
                        CodeMode.ACM,
                        "cpp"),
                ACTOR);
    }

    private static MockMultipartFile multipart(byte[] content) {
        return new MockMultipartFile("file", "cases.zip", "application/zip", content);
    }

    private static MultipartFile interruptedMultipart(byte[] content) {
        return new MultipartFile() {
            @Override
            public String getName() {
                return "file";
            }

            @Override
            public String getOriginalFilename() {
                return "cases.zip";
            }

            @Override
            public String getContentType() {
                return "application/zip";
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public long getSize() {
                return content.length;
            }

            @Override
            public byte[] getBytes() {
                return content.clone();
            }

            @Override
            public InputStream getInputStream() {
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

            @Override
            public void transferTo(java.io.File destination) {
                throw new UnsupportedOperationException();
            }
        };
    }

    /** 2026-10-18 出题时真实遇到的两种拒绝：报错要指出是哪个文件、什么原因、上限多少，而不是一句笼统的规则。 */
    @Test
    void rejectionsNameTheFileTheReasonAndTheLimit() throws Exception {
        var problem = createProblem("explained");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(output)) {
            for (String name : new String[] {"1.in", "1.out"}) {
                ZipArchiveEntry entry = new ZipArchiveEntry(name);
                entry.setUnixMode(0600);
                archive.putArchiveEntry(entry);
                archive.write(bytes("1\n"));
                archive.closeArchiveEntry();
            }
        }
        assertThatThrownBy(() -> testData.upload(problem.id(), multipart(output.toByteArray()), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("INVALID_TEST_DATA_ARCHIVE");
                    assertThat(error.getMessage())
                            .startsWith("「1.in」不是可读取的普通文件")
                            .contains("重新打包");
                });

        byte[] repetitive = zip(Map.of("1.in", bytes("10000 ".repeat(100_000)), "1.out", bytes("1\n")));
        assertThatThrownBy(() -> testData.upload(problem.id(), multipart(repetitive), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("PAYLOAD_TOO_LARGE");
                    assertThat(error.getMessage())
                            .isEqualTo("「1.in」的压缩比超过 100 倍的上限：内容重复度太高，" + "会被当作压缩炸弹拦下。请让这组数据不那么重复后重试。");
                });
    }

    private static byte[] goldenZip() throws Exception {
        return zip(Map.of(
                "1.in", bytes("1 2\n"), "1.out", bytes("3\n"), "2.in", bytes("100 -7\n"), "2.out", bytes("93\n")));
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
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
