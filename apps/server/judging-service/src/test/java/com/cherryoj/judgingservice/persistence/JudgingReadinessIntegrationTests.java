package com.cherryoj.judgingservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos.CalibrationRequest;
import com.cherryoj.judgingservice.api.JudgingDtos.DeploymentMetadata;
import com.cherryoj.judgingservice.api.JudgingDtos.Manifest;
import com.cherryoj.judgingservice.api.JudgingDtos.ManifestFile;
import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class JudgingReadinessIntegrationTests {
    private static final String ACTOR = "019c8e42-7f70-7000-8000-000000000001";
    private static final String FINGERPRINT = "dev-linux-amd64-go-judge-v2";
    private static final Path TESTDATA_ROOT = temporaryRoot();

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_judging_test")
            .withUsername("cherry")
            .withPassword("test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("cherry.judging.testdata-root", () -> TESTDATA_ROOT.toString());
    }

    @Autowired JudgingReadinessService service;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JudgeGateway judge;

    @Test
    void deploymentIsImmutableIdempotentAndAuditedThenAcCalibrationMakesReadinessTrue() throws Exception {
        Fixture fixture = fixture();
        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", FINGERPRINT, 12L, 4096L, 100));

        var deployed = service.deploy(fixture.metadata(), new ByteArrayInputStream(fixture.zip()), ACTOR, "trace-1");
        var retried = service.deploy(fixture.metadata(), new ByteArrayInputStream(fixture.zip()), ACTOR, "trace-2");
        assertThat(deployed.status()).isEqualTo("READY");
        assertThat(retried).isEqualTo(deployed);
        assertThat(Files.readString(TESTDATA_ROOT.resolve(fixture.testDataId() + "/1.in"))).isEqualTo("1 2\n");

        byte[] otherZip = zip(Map.of("1.in", bytes("2\n"), "1.out", bytes("2\n")));
        var other = new DeploymentMetadata(fixture.testDataId(), sha(otherZip),
                manifest(Map.of("1.in", bytes("2\n"), "1.out", bytes("2\n"))));
        assertThatThrownBy(() -> service.deploy(other, new ByteArrayInputStream(otherZip), ACTOR, null))
                .isInstanceOfSatisfying(JudgingApiException.class,
                        error -> assertThat(error.code()).isEqualTo("DEPLOYMENT_HASH_CONFLICT"));

        CalibrationRequest request = calibration(fixture, "int main(){return 0;}");
        var calibrated = service.calibrate(request, ACTOR, "trace-3");
        assertThat(calibrated.status()).isEqualTo("VALID");
        assertThat(calibrated.benchmarkSummary().sourceSha256()).isEqualTo(sha(bytes(request.referenceSource())));
        assertThat(calibrated.benchmarkSummary().verdict()).isEqualTo("AC");

        var readiness = service.readiness(fixture.problemVersionId(), fixture.testDataId(),
                fixture.metadata().expectedSha256(), "cpp");
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.checks()).allMatch(check -> check.passed());
        assertThat(readiness.executionProfile().cpuNs()).isEqualTo(1_000_000_000L);
        assertThat(readiness.executionProfile().environmentFingerprint()).isEqualTo(FINGERPRINT);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM judging_audit_event
                WHERE actor_user_id = UUID_TO_BIN(?) AND action IN
                  ('TEST_DATA_DEPLOYMENT_STARTED','TEST_DATA_DEPLOYMENT_READY','CALIBRATION_STARTED','CALIBRATION_VALIDATED')
                """, Integer.class, ACTOR)).isGreaterThanOrEqualTo(4);
        String details = String.join("", jdbc.queryForList(
                "SELECT CAST(detail_json AS CHAR) FROM judging_audit_event", String.class));
        assertThat(details).doesNotContain(request.referenceSource(), "1 2", "3\\n");
    }

    @Test
    void failedJudgeNeverSupersedesExistingValidCalibrationAndFingerprintMismatchIsSe() throws Exception {
        Fixture fixture = fixture();
        service.deploy(fixture.metadata(), new ByteArrayInputStream(fixture.zip()), ACTOR, null);
        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", FINGERPRINT, 1L, 2L, 100));
        var valid = service.calibrate(calibration(fixture, "// valid"), ACTOR, null);

        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("WA", FINGERPRINT, 3L, 4L, 0));
        var failed = service.calibrate(calibration(fixture, "// wrong"), ACTOR, null);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.benchmarkSummary().verdict()).isEqualTo("WA");
        assertThat(jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM language_calibration
                WHERE problem_version_id = UUID_TO_BIN(?) AND status = 'VALID'
                """, String.class, fixture.problemVersionId())).isEqualTo(valid.id());

        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", "other-environment", 3L, 4L, 100));
        var mismatch = service.calibrate(calibration(fixture, "// mismatch"), ACTOR, null);
        assertThat(mismatch.status()).isEqualTo("FAILED");
        assertThat(mismatch.errorMessage()).isEqualTo("JUDGE_ENVIRONMENT_FINGERPRINT_MISMATCH");
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM language_calibration
                WHERE problem_version_id = UUID_TO_BIN(?) AND status = 'VALID'
                """, Integer.class, fixture.problemVersionId())).isEqualTo(1);

        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", FINGERPRINT, 5L, 6L, 100));
        var replacement = service.calibrate(calibration(fixture, "// replacement"), ACTOR, null);
        assertThat(replacement.status()).isEqualTo("VALID");
        assertThat(replacement.id()).isNotEqualTo(valid.id());
        assertThat(jdbc.queryForObject("SELECT status FROM language_calibration WHERE id=UUID_TO_BIN(?)",
                String.class, valid.id())).isEqualTo("SUPERSEDED");
    }

    @Test
    void databaseStateChangeDuringStreamRollsBackInstalledDirectoryAndLeavesNoReadyFact() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch fullyRead = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        InputStream blocked = blocking(fixture.zip(), fullyRead, release);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var deployment = executor.submit(() -> service.deploy(fixture.metadata(), blocked, ACTOR, null));
            fullyRead.await();
            jdbc.update("""
                    UPDATE test_data_deployment SET status = 'FAILED', error_message = 'INJECTED', row_version = row_version + 1
                    WHERE test_data_version_id = UUID_TO_BIN(?)
                    """, fixture.testDataId());
            release.countDown();
            assertThatThrownBy(deployment::get).hasCauseInstanceOf(JudgingApiException.class);
        }
        assertThat(TESTDATA_ROOT.resolve(fixture.testDataId())).doesNotExist();
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_data_deployment
                WHERE test_data_version_id = UUID_TO_BIN(?) AND status = 'READY'
                """, Integer.class, fixture.testDataId())).isZero();
    }

    @Test
    void concurrentIdenticalDeploymentsConvergeOnOneReadyFact() throws Exception {
        Fixture fixture = fixture();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> {
                start.await();
                return service.deploy(fixture.metadata(), new ByteArrayInputStream(fixture.zip()), ACTOR, null);
            });
            var second = executor.submit(() -> {
                start.await();
                return service.deploy(fixture.metadata(), new ByteArrayInputStream(fixture.zip()), ACTOR, null);
            });
            start.countDown();
            assertThat(first.get()).isEqualTo(second.get());
        }
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM test_data_deployment
                WHERE test_data_version_id = UUID_TO_BIN(?) AND status = 'READY'
                """, Integer.class, fixture.testDataId())).isEqualTo(1);
    }

    @Test
    void mysqlV1EnforcesSingleActiveValidAndReadyHashConstraints() throws Exception {
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = DATABASE() AND table_name IN
                  ('judge_environment','judge_environment_language','test_data_deployment','language_calibration','judging_audit_event')
                """, Integer.class)).isEqualTo(5);
        String second = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO judge_environment
                  (id,name,fingerprint,status,architecture,cpu_model,os_version,kernel_version,
                   judge_version,sandbox_version,config_digest,endpoint_ref,created_at,activated_at,row_version)
                VALUES (UUID_TO_BIN(?),'second',?,'ACTIVE','amd64','cpu','linux','kernel','v2','v2','digest',
                        'http://127.0.0.1:5051',?,?,0)
                """, second, "fingerprint-" + second, now, now)).isInstanceOf(DataIntegrityViolationException.class);

        String testData = UUID.randomUUID().toString();
        String environment = jdbc.queryForObject(
                "SELECT BIN_TO_UUID(id) FROM judge_environment WHERE status='ACTIVE'", String.class);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO test_data_deployment
                  (test_data_version_id,judge_environment_id,expected_sha256,status,deployed_sha256,
                   deployed_at,error_message,created_at,updated_at,row_version)
                VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),UNHEX(?),'READY',UNHEX(?),?,NULL,?,?,0)
                """, testData, environment, "1".repeat(64), "2".repeat(64), now, now, now))
                .isInstanceOf(RuntimeException.class);

        String problemVersion = UUID.randomUUID().toString();
        String calibration = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO language_calibration
                  (id,problem_version_id,language_id,judge_environment_id,status,source_type,cpu_ns,
                   memory_bytes,approved_by,approved_at,created_at,updated_at,row_version)
                VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),'cpp',UUID_TO_BIN(?),'VALID','MANUAL',1,1,
                        UUID_TO_BIN(?),?,?,?,0)
                """, calibration, problemVersion, environment, ACTOR, now, now, now);
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO language_calibration
                  (id,problem_version_id,language_id,judge_environment_id,status,source_type,cpu_ns,
                   memory_bytes,approved_by,approved_at,created_at,updated_at,row_version)
                VALUES (UUID_TO_BIN(?),UUID_TO_BIN(?),'cpp',UUID_TO_BIN(?),'VALID','MANUAL',1,1,
                        UUID_TO_BIN(?),?,?,?,0)
                """, UUID.randomUUID().toString(), problemVersion, environment, ACTOR, now, now, now))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private static CalibrationRequest calibration(Fixture fixture, String source) {
        return new CalibrationRequest(fixture.problemId(), fixture.problemVersionId(), fixture.testDataId(),
                fixture.metadata().expectedSha256(), "cpp", 1_000_000_000L, 268_435_456L, null, source);
    }

    private static Fixture fixture() throws Exception {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("1.in", bytes("1 2\n")); files.put("1.out", bytes("3\n"));
        files.put("2.in", bytes("4 5\n")); files.put("2.out", bytes("9\n"));
        byte[] zip = zip(files);
        String testData = UUID.randomUUID().toString();
        return new Fixture(UUID.randomUUID().toString(), UUID.randomUUID().toString(), testData,
                zip, new DeploymentMetadata(testData, sha(zip), manifest(files)));
    }

    private static Manifest manifest(Map<String, byte[]> files) {
        List<ManifestFile> entries = files.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(value -> new ManifestFile(value.getKey(), value.getValue().length, sha(value.getValue())))
                .toList();
        return new Manifest(entries.size() / 2, files.values().stream().mapToLong(v -> v.length).sum(), entries);
    }

    private static InputStream blocking(byte[] content, CountDownLatch fullyRead, CountDownLatch release) {
        return new InputStream() {
            int position;
            @Override public int read(byte[] buffer, int offset, int length) throws IOException {
                if (position == content.length) {
                    fullyRead.countDown();
                    try { release.await(); }
                    catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IOException(error); }
                    return -1;
                }
                int read = Math.min(length, content.length - position);
                System.arraycopy(content, position, buffer, offset, read);
                position += read;
                return read;
            }
            @Override public int read() throws IOException {
                byte[] one = new byte[1];
                return read(one, 0, 1) == -1 ? -1 : Byte.toUnsignedInt(one[0]);
            }
        };
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
    private static String sha(byte[] bytes) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static Path temporaryRoot() {
        try { return Files.createTempDirectory("cherry-judging-testdata-"); }
        catch (IOException error) { throw new ExceptionInInitializerError(error); }
    }

    private record Fixture(String problemId, String problemVersionId, String testDataId,
                           byte[] zip, DeploymentMetadata metadata) {}
}
