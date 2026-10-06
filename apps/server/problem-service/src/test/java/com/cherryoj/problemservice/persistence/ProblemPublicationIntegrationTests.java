package com.cherryoj.problemservice.persistence;

import static com.cherryoj.problemservice.persistence.ProblemFixtures.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.AdminProblemDtos.SampleInput;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.ProblemPublicationService;
import com.cherryoj.problemservice.application.TestDataService;
import com.cherryoj.problemservice.integration.judging.JudgingClient;
import com.cherryoj.problemservice.integration.judging.JudgingDtos;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
@Import(ProblemPublicationIntegrationTests.FakeConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ProblemPublicationIntegrationTests {
    private static final Path STORAGE_ROOT = temporaryRoot();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_problem_publication")
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

    @Autowired AdminProblemService admin;
    @Autowired TestDataService testData;
    @Autowired ProblemPublicationService publication;
    @Autowired FakeJudgingClient judging;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetFake() {
        judging.reset();
    }

    @Test
    void calibrationSendsTheCurrentAddressAndDigestOutsideTransactionsAndLeavesTheProblemAlone() throws Exception {
        Fixture fixture = fixture("calibrate");
        String sourceCanary = "source-canary-" + UUID.randomUUID();

        var result = publication.calibrate(fixture.problemId(), new CalibrateProblemRequest(
                "cpp", 1_000_000L, 64_000_000L, null, sourceCanary),
                "delegated-admin-token", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", ACTOR);

        assertThat(result.status()).isEqualTo(AdminProblemDtos.CalibrationStatus.VALID);
        assertThat(result.problemId()).isEqualTo(fixture.problemId());
        assertThat(result.testDataDigest()).isEqualTo(fixture.digest());
        // 标定用的是题目当前的地址与指纹，judging-service 据此跑参考解并核对判题结果里的指纹
        assertThat(judging.calibration.testDataLocation()).isEqualTo(fixture.location());
        assertThat(judging.calibration.testDataDigest()).isEqualTo(fixture.digest());
        assertThat(judging.calibration.referenceSource()).isEqualTo(sourceCanary);
        assertThat(judging.jwt).isEqualTo("delegated-admin-token");
        assertThat(judging.traceparent).startsWith("00-");
        assertThat(judging.transactionObserved.get()).isFalse();
        // 标定不改题目：没有草稿、验证中这些状态
        var reloaded = admin.getProblem(fixture.problemId());
        assertThat(reloaded.rowVersion()).isEqualTo(fixture.rowVersion());
        assertThat(reloaded.visibility()).isEqualTo(Visibility.PRIVATE);
        // 参考源码不进数据库，也不进审计
        String audit = jdbc.queryForObject("""
                SELECT CAST(JSON_ARRAYAGG(detail_json) AS CHAR) FROM problem_audit_event
                WHERE problem_id = UUID_TO_BIN(?)
                """, String.class, fixture.problemId());
        assertThat(audit).doesNotContain(sourceCanary).contains(fixture.digest());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND column_name = 'reference_source'
                """, Integer.class)).isZero();
    }

    @Test
    void aRejectedReferenceIsReportedAndAudited() throws Exception {
        Fixture fixture = fixture("failed");
        judging.calibrationStatus = "FAILED";

        var result = publication.calibrate(fixture.problemId(),
                new CalibrateProblemRequest("cpp", 1, 1, null, "reference"), "jwt", null, ACTOR);

        assertThat(result.status()).isEqualTo(AdminProblemDtos.CalibrationStatus.FAILED);
        assertThat(jdbc.queryForList("""
                SELECT action FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?) AND action LIKE 'PROBLEM_CALIBRAT%'
                """, String.class, fixture.problemId())).containsExactly("PROBLEM_CALIBRATION_FAILED");
    }

    @Test
    void anAmbiguousRemoteTimeoutLeavesTheProblemUntouched() throws Exception {
        Fixture fixture = fixture("timeout");
        judging.calibrationFailure = new ProblemApiException(
                HttpStatus.GATEWAY_TIMEOUT, "JUDGING_TIMEOUT", "判题服务请求超时，请读取状态后重试。");

        assertThatThrownBy(() -> publication.calibrate(fixture.problemId(),
                new CalibrateProblemRequest("cpp", 1, 1, null, "temporary-reference"), "jwt", null, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("JUDGING_TIMEOUT"));

        assertThat(admin.getProblem(fixture.problemId()).rowVersion()).isEqualTo(fixture.rowVersion());
    }

    @Test
    void calibrationNeedsTheProblemToBeLocallyReadyAndNeverCallsJudgingOtherwise() throws Exception {
        var empty = admin.create(new CreateProblemRequest(
                "empty-" + UUID.randomUUID().toString().substring(0, 8), "Empty", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);

        assertThatThrownBy(() -> publication.calibrate(empty.id(),
                new CalibrateProblemRequest("cpp", 1, 1, null, "reference"), "jwt", null, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT"));
        assertThat(judging.calibrateCalls.get()).isZero();
    }

    @Test
    void publishCheckIsReadOnlyAndPublishAndUnpublishAreAtomicIdempotentAndReversible() throws Exception {
        Fixture fixture = fixture("publish");

        judging.ready = false;
        var missing = publication.publishCheck(fixture.problemId(), "jwt", null);
        assertThat(missing.checks()).extracting(value -> value.code().name())
                .containsExactly("CONTENT", "SAMPLES", "LANGUAGE", "TEST_DATA", "ONLINE_JUDGE_NODE", "CALIBRATION");
        assertThat(missing.ready()).isFalse();
        // 判题服务收到的是题目当前测试数据的指纹，用它判断标定是否过期
        assertThat(judging.readinessDigest).isEqualTo(fixture.digest());
        assertThat(admin.getProblem(fixture.problemId()).visibility()).isEqualTo(Visibility.PRIVATE);
        assertThatThrownBy(() -> publication.publish(fixture.problemId(), fixture.rowVersion(), "jwt", null, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT"));

        judging.ready = true;
        var published = publication.publish(fixture.problemId(), fixture.rowVersion(), "jwt", null, ACTOR);
        assertThat(published.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(published.publishedAt()).isNotNull();
        // 重复发布是幂等的：不改首次公开时间
        assertThat(publication.publish(fixture.problemId(), fixture.rowVersion(), "jwt", null, ACTOR).publishedAt())
                .isEqualTo(published.publishedAt());
        assertThat(count(fixture.problemId(), "PROBLEM_PUBLISHED")).isEqualTo(1);

        var hidden = publication.unpublish(fixture.problemId(), published.rowVersion(), ACTOR);
        assertThat(hidden.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(hidden.publishedAt()).isEqualTo(published.publishedAt());
        var again = publication.publish(fixture.problemId(), hidden.rowVersion(), "jwt", null, ACTOR);
        assertThat(again.visibility()).isEqualTo(Visibility.PUBLIC);
        assertThat(again.publishedAt()).isEqualTo(published.publishedAt());
        assertThat(count(fixture.problemId(), "PROBLEM_UNPUBLISHED")).isEqualTo(1);
    }

    @Test
    void withoutTestDataThereIsNothingToAskJudgingAbout() throws Exception {
        var problem = admin.create(new CreateProblemRequest(
                "nodata-" + UUID.randomUUID().toString().substring(0, 8), "No data", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);

        var check = publication.publishCheck(problem.id(), "jwt", null);

        assertThat(check.ready()).isFalse();
        assertThat(check.checks()).filteredOn(item -> item.code().name().equals("TEST_DATA"))
                .singleElement().satisfies(item -> assertThat(item.message()).contains("没有上传"));
        assertThat(judging.readinessCalls.get()).isZero();
    }

    @Test
    void concurrentPublishHasOneWriterAndOneConflict() throws Exception {
        Fixture fixture = fixture("publish-race");
        judging.readinessBarrier = new CyclicBarrier(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> publishAfter(start, fixture));
            var second = executor.submit(() -> publishAfter(start, fixture));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("PUBLIC", "ROW_VERSION_CONFLICT");
        }
        assertThat(count(fixture.problemId(), "PROBLEM_PUBLISHED")).isEqualTo(1);
    }

    /** 公开检查通过之后、真正公开之前数据被换掉：检查的是旧数据，不能据此公开。 */
    @Test
    void publishRefusesWhenTheDataChangedAfterTheCheck() throws Exception {
        Fixture fixture = fixture("data-race");
        judging.afterReadiness = () -> {
            try {
                testData.upload(fixture.problemId(), new MockMultipartFile("file", "cases.zip", "application/zip",
                        zip(Map.of("1.in", bytes("9 9\n"), "1.out", bytes("18\n")))), ACTOR);
            }
            catch (Exception error) {
                throw new IllegalStateException(error);
            }
        };

        assertThatThrownBy(() -> publication.publish(fixture.problemId(), fixture.rowVersion(), "jwt", null, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("RESOURCE_STATE_CONFLICT");
                    assertThat(error.getMessage()).contains("测试数据已改变");
                });
        assertThat(admin.getProblem(fixture.problemId()).visibility()).isEqualTo(Visibility.PRIVATE);
    }

    private String publishAfter(CountDownLatch start, Fixture fixture) throws Exception {
        start.await();
        try {
            return publication.publish(fixture.problemId(), fixture.rowVersion(), "jwt", null, ACTOR)
                    .visibility().name();
        }
        catch (ProblemApiException error) {
            return error.code();
        }
    }

    private int count(String problemId, String action) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) FROM problem_audit_event WHERE problem_id = UUID_TO_BIN(?) AND action = ?
                """, Integer.class, problemId, action);
    }

    private Fixture fixture(String prefix) throws Exception {
        var problem = admin.create(new CreateProblemRequest(
                prefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                "Complete problem", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
        var saved = admin.update(problem.id(), new UpdateProblemRequest(
                problem.slug(), "Complete problem", "statement", "input", "output", "constraints", null,
                Difficulty.EASY, List.of("math"), List.of(new SampleInput(1, "1 2\n", "3\n", null)),
                "int main() {}", 0), ACTOR);
        var uploaded = testData.upload(problem.id(), new MockMultipartFile("file", "cases.zip", "application/zip",
                zip(Map.of("1.in", bytes("1 2\n"), "1.out", bytes("3\n")))), ACTOR);
        String location = testData.forJudging(problem.id()).location();
        return new Fixture(problem.id(), uploaded.digest(), location, saved.rowVersion());
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
            return Files.createTempDirectory("cherry-publication-");
        }
        catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }

    record Fixture(String problemId, String digest, String location, long rowVersion) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeConfig {
        @Bean
        @Primary
        FakeJudgingClient fakeJudgingClient() {
            return new FakeJudgingClient();
        }
    }

    static final class FakeJudgingClient implements JudgingClient {
        volatile String jwt;
        volatile String traceparent;
        volatile JudgingDtos.CalibrationRequest calibration;
        volatile String readinessDigest;
        volatile String calibrationStatus = "VALID";
        volatile RuntimeException calibrationFailure;
        volatile boolean ready = true;
        volatile CyclicBarrier readinessBarrier;
        volatile Runnable afterReadiness;
        final AtomicBoolean transactionObserved = new AtomicBoolean();
        final AtomicInteger calibrateCalls = new AtomicInteger();
        final AtomicInteger readinessCalls = new AtomicInteger();

        void reset() {
            jwt = null;
            traceparent = null;
            calibration = null;
            readinessDigest = null;
            calibrationStatus = "VALID";
            calibrationFailure = null;
            ready = true;
            readinessBarrier = null;
            afterReadiness = null;
            transactionObserved.set(false);
            calibrateCalls.set(0);
            readinessCalls.set(0);
        }

        @Override
        public JudgingDtos.Calibration calibrate(
                JudgingDtos.CalibrationRequest request, String token, String trace) {
            calibrateCalls.incrementAndGet();
            if (TransactionSynchronizationManager.isActualTransactionActive()) transactionObserved.set(true);
            if (calibrationFailure != null) throw calibrationFailure;
            calibration = request;
            jwt = token;
            traceparent = trace;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            String verdict = "VALID".equals(calibrationStatus) ? "AC" : "WA";
            return new JudgingDtos.Calibration(
                    UUID.randomUUID().toString(), request.problemId(), request.languageId(), calibrationStatus,
                    request.cpuNs(), request.memoryBytes(), request.clockNs(), request.testDataDigest(),
                    new JudgingDtos.BenchmarkSummary("a".repeat(64), verdict, 1L, 1L, 1L),
                    "VALID".equals(calibrationStatus) ? null : "REFERENCE_NOT_ACCEPTED", now, now, 1);
        }

        @Override
        public JudgingDtos.Readiness readiness(
                String problemId, String languageId, String testDataDigest, String token, String trace) {
            readinessCalls.incrementAndGet();
            if (TransactionSynchronizationManager.isActualTransactionActive()) transactionObserved.set(true);
            readinessDigest = testDataDigest;
            if (readinessBarrier != null) {
                try {
                    readinessBarrier.await(10, TimeUnit.SECONDS);
                }
                catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }
            var result = new JudgingDtos.Readiness(ready, List.of(
                    new JudgingDtos.ReadinessCheck("ONLINE_JUDGE_NODE", true, "Node online."),
                    new JudgingDtos.ReadinessCheck("LANGUAGE", true, "Language ready."),
                    new JudgingDtos.ReadinessCheck("CALIBRATION", ready, ready ? "Calibration ready." : "Calibration missing.")),
                    ready ? new JudgingDtos.ExecutionProfile("endpoint",
                            UUID.randomUUID().toString(), 1, 1, null) : null);
            if (afterReadiness != null) afterReadiness.run();
            return result;
        }
    }
}
