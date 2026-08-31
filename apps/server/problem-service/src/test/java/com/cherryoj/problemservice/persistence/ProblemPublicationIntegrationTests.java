package com.cherryoj.problemservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.CalibrateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.DeployTestDataRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.AdminProblemDtos.SampleInput;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateVersionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.VersionStatus;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.TestDataDtos.BindTestDataRequest;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.ProblemPublicationService;
import com.cherryoj.problemservice.application.TestDataService;
import com.cherryoj.problemservice.bootstrap.ProblemValidationRecovery;
import com.cherryoj.problemservice.integration.judging.JudgingClient;
import com.cherryoj.problemservice.integration.judging.JudgingDtos;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
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
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Import(ProblemPublicationIntegrationTests.FakeConfig.class)
@Testcontainers(disabledWithoutDocker = true)
class ProblemPublicationIntegrationTests {
    private static final String ACTOR = "019c8e42-7f70-7000-8000-000000000001";
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
    @Autowired ProblemValidationRecovery recovery;
    @Autowired FakeJudgingClient judging;
    @Autowired JdbcTemplate jdbc;

    @BeforeEach
    void resetFake() {
        judging.reset();
    }

    @Test
    void deploymentStreamsTheBoundReadyAssetWithDelegatedIdentityOutsideTransactions() throws Exception {
        Fixture fixture = fixture("deploy");

        var deployed = publication.deploy(fixture.problemId(), fixture.versionId(),
                new DeployTestDataRequest(fixture.testDataId(), fixture.sha256(), fixture.rowVersion()),
                "delegated-admin-token", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01", ACTOR);

        assertThat(deployed.status()).isEqualTo(AdminProblemDtos.DeploymentStatus.READY);
        assertThat(judging.deployedBytes).isEqualTo(fixture.zip());
        assertThat(judging.jwt).isEqualTo("delegated-admin-token");
        assertThat(judging.traceparent).startsWith("00-");
        assertThat(judging.transactionObserved.get()).isFalse();
        assertThat(judging.deploymentMetadata.testDataVersionId()).isEqualTo(fixture.testDataId());
        assertThat(judging.deploymentMetadata.manifest().files()).hasSize(2);
    }

    @Test
    void validationIsVisibleAndFailureRestoresDraftWithoutPersistingSource() throws Exception {
        Fixture fixture = fixture("validation");
        judging.blockCalibration = true;
        judging.calibrationStatus = "FAILED";
        String sourceCanary = "source-canary-" + UUID.randomUUID();
        CalibrateProblemRequest request = new CalibrateProblemRequest(
                "cpp", 1_000_000L, 64_000_000L, null, sourceCanary, fixture.rowVersion());

        try (var executor = Executors.newSingleThreadExecutor()) {
            var future = executor.submit(() -> publication.calibrate(
                    fixture.problemId(), fixture.versionId(), request, "jwt", null, ACTOR));
            assertThat(judging.entered.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(admin.getVersion(fixture.problemId(), fixture.versionId()).status())
                    .isEqualTo(VersionStatus.VALIDATING);
            judging.release.countDown();
            assertThat(future.get().status()).isEqualTo(AdminProblemDtos.CalibrationStatus.FAILED);
        }

        var restored = admin.getVersion(fixture.problemId(), fixture.versionId());
        assertThat(restored.status()).isEqualTo(VersionStatus.DRAFT);
        assertThat(restored.rowVersion()).isEqualTo(fixture.rowVersion() + 2);
        assertThat(judging.transactionObserved.get()).isFalse();
        String audit = jdbc.queryForObject("""
                SELECT CAST(JSON_ARRAYAGG(detail_json) AS CHAR) FROM problem_audit_event
                WHERE problem_id = UUID_TO_BIN(?)
                """, String.class, fixture.problemId());
        assertThat(audit).doesNotContain(sourceCanary);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.columns
                WHERE table_schema = DATABASE() AND column_name = 'reference_source'
                """, Integer.class)).isZero();
    }

    @Test
    void ambiguousRemoteTimeoutRestoresDraftAndRequiresStateReload() throws Exception {
        Fixture fixture = fixture("timeout");
        judging.calibrationFailure = new ProblemApiException(
                HttpStatus.GATEWAY_TIMEOUT, "JUDGING_TIMEOUT", "判题服务请求超时，请读取状态后重试。");

        assertThatThrownBy(() -> publication.calibrate(fixture.problemId(), fixture.versionId(),
                new CalibrateProblemRequest("cpp", 1, 1, null, "temporary-reference", fixture.rowVersion()),
                "jwt", null, ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("JUDGING_TIMEOUT"));

        var reloaded = admin.getVersion(fixture.problemId(), fixture.versionId());
        assertThat(reloaded.status()).isEqualTo(VersionStatus.DRAFT);
        assertThat(reloaded.rowVersion()).isEqualTo(fixture.rowVersion() + 2);
    }

    @Test
    void publishCheckIsReadOnlyAndPublishIsAtomicIdempotentAndImmutable() throws Exception {
        Fixture fixture = fixture("publish");
        judging.calibrationStatus = "VALID";
        var calibration = publication.calibrate(fixture.problemId(), fixture.versionId(),
                new CalibrateProblemRequest("cpp", 1_000_000, 64_000_000, 2_000_000L,
                        "int main() { return 0; }", fixture.rowVersion()), "jwt", null, ACTOR);
        assertThat(calibration.status()).isEqualTo(AdminProblemDtos.CalibrationStatus.VALID);
        var readyVersion = admin.getVersion(fixture.problemId(), fixture.versionId());
        assertThat(readyVersion.status()).isEqualTo(VersionStatus.READY_FOR_REVIEW);

        judging.ready = false;
        var missing = publication.publishCheck(fixture.problemId(), fixture.versionId(), "jwt", null);
        assertThat(missing.checks()).hasSize(6);
        assertThat(missing.checks()).extracting(value -> value.code().name())
                .containsExactly("CONTENT", "SAMPLES", "LANGUAGE", "TEST_DATA", "DEPLOYMENT", "CALIBRATION");
        assertThat(missing.ready()).isFalse();
        assertThat(admin.getVersion(fixture.problemId(), fixture.versionId()).status())
                .isEqualTo(VersionStatus.READY_FOR_REVIEW);

        judging.ready = true;
        var published = publication.publish(fixture.problemId(), fixture.versionId(), readyVersion.rowVersion(),
                "jwt", null, ACTOR);
        assertThat(published.status()).isEqualTo(VersionStatus.PUBLISHED);
        assertThat(admin.getProblem(fixture.problemId()).currentPublishedVersionId()).isEqualTo(fixture.versionId());
        assertThat(publication.publish(fixture.problemId(), fixture.versionId(), readyVersion.rowVersion(),
                "jwt", null, ACTOR).publishedAt()).isEqualTo(published.publishedAt());
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM problem_audit_event
                WHERE problem_id = UUID_TO_BIN(?) AND action = 'PROBLEM_VERSION_PUBLISHED'
                """, Integer.class, fixture.problemId())).isEqualTo(1);

        assertThatThrownBy(() -> admin.deleteDraft(
                fixture.problemId(), fixture.versionId(), published.rowVersion(), ACTOR))
                .isInstanceOf(ProblemApiException.class);
        assertThatThrownBy(() -> admin.updateVersion(fixture.problemId(), fixture.versionId(),
                updateRequest(published.rowVersion()), ACTOR)).isInstanceOf(ProblemApiException.class);
        var revision = admin.createRevision(fixture.problemId(),
                new AdminProblemDtos.CreateRevisionRequest(admin.getProblem(fixture.problemId()).rowVersion(), true), ACTOR);
        assertThat(revision.status()).isEqualTo(VersionStatus.DRAFT);
        assertThat(((com.cherryoj.problemservice.api.TestDataDtos.TestDataVersion) revision.testDataVersion()).id())
                .isEqualTo(fixture.testDataId());
        assertThat(admin.getVersion(fixture.problemId(), fixture.versionId()).title()).isEqualTo("Complete problem");
    }

    @Test
    void concurrentPublishHasOneWriterAndOneConflict() throws Exception {
        Fixture fixture = fixture("publish-race");
        judging.calibrationStatus = "VALID";
        publication.calibrate(fixture.problemId(), fixture.versionId(),
                new CalibrateProblemRequest("cpp", 1, 1, null, "reference", fixture.rowVersion()),
                "jwt", null, ACTOR);
        long rowVersion = admin.getVersion(fixture.problemId(), fixture.versionId()).rowVersion();
        judging.ready = true;
        judging.readinessBarrier = new CyclicBarrier(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> publishAfter(start, fixture, rowVersion));
            var second = executor.submit(() -> publishAfter(start, fixture, rowVersion));
            start.countDown();
            assertThat(List.of(first.get(), second.get())).containsExactlyInAnyOrder("PUBLISHED", "ROW_VERSION_CONFLICT");
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM problem_version
                WHERE problem_id = UUID_TO_BIN(?) AND status = 'PUBLISHED'
                """, Integer.class, fixture.problemId())).isEqualTo(1);
        assertThat(admin.getProblem(fixture.problemId()).currentPublishedVersionId()).isEqualTo(fixture.versionId());
    }

    @Test
    void recoveryOnlyRestoresExpiredValidations() throws Exception {
        Fixture stale = fixture("stale");
        Fixture active = fixture("active");
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        jdbc.update("""
                UPDATE problem_version SET status = 'VALIDATING', created_at = ?, updated_at = ?, row_version = row_version + 1
                WHERE id = UUID_TO_BIN(?)
                """, now.minusHours(2), now.minusHours(1), stale.versionId());
        jdbc.update("""
                UPDATE problem_version SET status = 'VALIDATING', updated_at = ?, row_version = row_version + 1
                WHERE id = UUID_TO_BIN(?)
                """, now, active.versionId());

        recovery.run(new DefaultApplicationArguments(new String[0]));

        assertThat(admin.getVersion(stale.problemId(), stale.versionId()).status()).isEqualTo(VersionStatus.DRAFT);
        assertThat(admin.getVersion(active.problemId(), active.versionId()).status()).isEqualTo(VersionStatus.VALIDATING);
    }

    private String publishAfter(CountDownLatch start, Fixture fixture, long rowVersion) throws Exception {
        start.await();
        try {
            return publication.publish(fixture.problemId(), fixture.versionId(), rowVersion, "jwt", null, ACTOR)
                    .status().name();
        }
        catch (ProblemApiException error) {
            return error.code();
        }
    }

    private Fixture fixture(String prefix) throws Exception {
        var problem = admin.create(new CreateProblemRequest(
                prefix + "-" + UUID.randomUUID().toString().substring(0, 8),
                "Complete problem", Difficulty.EASY, CodeMode.ACM, "cpp"), ACTOR);
        String versionId = problem.versions().getFirst().id();
        var saved = admin.updateVersion(problem.id(), versionId, updateRequest(0), ACTOR);
        byte[] zip = zip(Map.of("1.in", bytes("1 2\n"), "1.out", bytes("3\n")));
        var uploaded = testData.upload(problem.id(),
                new MockMultipartFile("file", "cases.zip", "application/zip", zip), ACTOR);
        var bound = testData.bind(problem.id(), versionId,
                new BindTestDataRequest(uploaded.id(), saved.rowVersion()), ACTOR);
        return new Fixture(problem.id(), versionId, uploaded.id(), uploaded.contentSha256(), bound.rowVersion(), zip);
    }

    private static UpdateVersionRequest updateRequest(long rowVersion) {
        return new UpdateVersionRequest(
                "Complete problem", "statement", "input", "output", "constraints", null,
                Difficulty.EASY, List.of("math"), List.of(new SampleInput(1, "1 2\n", "3\n", null)),
                "int main() {}", "summary", rowVersion);
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

    record Fixture(String problemId, String versionId, String testDataId, String sha256, long rowVersion, byte[] zip) {}

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeConfig {
        @Bean
        @Primary
        FakeJudgingClient fakeJudgingClient() {
            return new FakeJudgingClient();
        }
    }

    static final class FakeJudgingClient implements JudgingClient {
        volatile byte[] deployedBytes;
        volatile String jwt;
        volatile String traceparent;
        volatile JudgingDtos.DeploymentMetadata deploymentMetadata;
        volatile String calibrationStatus = "VALID";
        volatile RuntimeException calibrationFailure;
        volatile boolean ready = true;
        volatile boolean blockCalibration;
        volatile CountDownLatch entered = new CountDownLatch(1);
        volatile CountDownLatch release = new CountDownLatch(1);
        volatile CyclicBarrier readinessBarrier;
        final AtomicBoolean transactionObserved = new AtomicBoolean();

        void reset() {
            deployedBytes = null;
            jwt = null;
            traceparent = null;
            deploymentMetadata = null;
            calibrationStatus = "VALID";
            calibrationFailure = null;
            ready = true;
            blockCalibration = false;
            entered = new CountDownLatch(1);
            release = new CountDownLatch(1);
            readinessBarrier = null;
            transactionObserved.set(false);
        }

        @Override
        public JudgingDtos.Deployment deploy(
                JudgingDtos.DeploymentMetadata metadata, InputStream archive, String token, String trace) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) transactionObserved.set(true);
            try {
                deployedBytes = archive.readAllBytes();
            }
            catch (Exception error) {
                throw new IllegalStateException(error);
            }
            deploymentMetadata = metadata;
            jwt = token;
            traceparent = trace;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            return new JudgingDtos.Deployment(metadata.testDataVersionId(), UUID.randomUUID().toString(), "test-env",
                    metadata.expectedSha256(), "READY", metadata.expectedSha256(), now, null, now, 1);
        }

        @Override
        public JudgingDtos.Calibration calibrate(
                JudgingDtos.CalibrationRequest request, String token, String trace) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) transactionObserved.set(true);
            entered.countDown();
            if (blockCalibration) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                }
                catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(error);
                }
            }
            if (calibrationFailure != null) throw calibrationFailure;
            LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
            String verdict = "VALID".equals(calibrationStatus) ? "AC" : "WA";
            return new JudgingDtos.Calibration(
                    UUID.randomUUID().toString(), request.problemVersionId(), request.languageId(),
                    UUID.randomUUID().toString(), calibrationStatus,
                    request.cpuNs(), request.memoryBytes(), request.clockNs(),
                    new JudgingDtos.BenchmarkSummary("a".repeat(64), verdict, 1L, 1L, 1L),
                    "VALID".equals(calibrationStatus) ? null : "REFERENCE_NOT_ACCEPTED", now, now, 1);
        }

        @Override
        public JudgingDtos.Readiness readiness(
                String problemVersionId, String testDataVersionId, String expectedSha256,
                String languageId, String token, String trace) {
            if (TransactionSynchronizationManager.isActualTransactionActive()) transactionObserved.set(true);
            if (readinessBarrier != null) {
                try {
                    readinessBarrier.await(10, TimeUnit.SECONDS);
                }
                catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            }
            String environmentId = UUID.randomUUID().toString();
            return new JudgingDtos.Readiness(ready, environmentId, List.of(
                    new JudgingDtos.ReadinessCheck("ACTIVE_ENVIRONMENT", true, "ACTIVE environment ready."),
                    new JudgingDtos.ReadinessCheck("LANGUAGE", true, "Language ready."),
                    new JudgingDtos.ReadinessCheck("DEPLOYMENT", ready, ready ? "Deployment ready." : "Deployment missing."),
                    new JudgingDtos.ReadinessCheck("CALIBRATION", ready, ready ? "Calibration ready." : "Calibration missing.")),
                    ready ? new JudgingDtos.ExecutionProfile(environmentId, "fingerprint", "endpoint",
                            UUID.randomUUID().toString(), 1, 1, null) : null);
        }
    }
}
