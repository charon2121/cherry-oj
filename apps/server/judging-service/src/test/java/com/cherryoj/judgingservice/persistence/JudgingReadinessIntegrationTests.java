package com.cherryoj.judgingservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cherryoj.judgingservice.api.JudgeNodeDtos.Receipt;
import com.cherryoj.judgingservice.api.JudgeNodeDtos.Registration;
import com.cherryoj.judgingservice.api.JudgingDtos.CalibrationRequest;
import com.cherryoj.judgingservice.api.JudgingDtos.DeploymentMetadata;
import com.cherryoj.judgingservice.api.JudgingDtos.Manifest;
import com.cherryoj.judgingservice.api.JudgingDtos.ManifestFile;
import com.cherryoj.judgingservice.application.JudgeNodeRegistry;
import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.judge.JudgeGateway;
import com.cherryoj.judgingservice.judge.JudgeNodeClient;
import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** 标定与就绪判定：测试数据由（模拟的）在线节点持有，判题结果由模拟网关给出。 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JudgingReadinessIntegrationTests {
    private static final String ACTOR = "019c8e42-7f70-7000-8000-000000000001";
    private static final String SESSION = UUID.randomUUID().toString();
    private static final String SHA = "a".repeat(64);

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
    }

    @Autowired JudgingReadinessService service;
    @Autowired JudgeNodeRegistry registry;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean JudgeGateway judge;
    @MockitoBean JudgeNodeClient client;

    @BeforeEach
    void onlineNode() {
        registry.register(new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp")));
    }

    @Test
    void acCalibrationMakesReadinessTrueAndAuditHoldsNoSource() throws Exception {
        Fixture fixture = deployed();
        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", 12L, 4096L, 100));
        assertThat(service.readiness(fixture.problemVersionId(), fixture.testDataId(), SHA, "cpp").ready()).isFalse();

        CalibrationRequest request = calibration(fixture, "int main(){return 0;}");
        var calibrated = service.calibrate(request, ACTOR, "trace-1");
        assertThat(calibrated.status()).isEqualTo("VALID");
        assertThat(calibrated.benchmarkSummary().verdict()).isEqualTo("AC");

        var readiness = service.readiness(fixture.problemVersionId(), fixture.testDataId(), SHA, "cpp");
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.checks()).allMatch(check -> check.passed());
        assertThat(readiness.executionProfile().cpuNs()).isEqualTo(1_000_000_000L);
        assertThat(readiness.executionProfile().endpointRef()).isEqualTo("http://127.0.0.1:15051");
        String details = String.join("", jdbc.queryForList(
                "SELECT CAST(detail_json AS CHAR) FROM judging_audit_event", String.class));
        assertThat(details).doesNotContain(request.referenceSource());
    }

    @Test
    void failedJudgeNeverSupersedesExistingValidCalibration() throws Exception {
        Fixture fixture = deployed();
        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", 1L, 2L, 100));
        var valid = service.calibrate(calibration(fixture, "// valid"), ACTOR, null);

        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("WA", 3L, 4L, 0));
        var failed = service.calibrate(calibration(fixture, "// wrong"), ACTOR, null);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.benchmarkSummary().verdict()).isEqualTo("WA");

        assertThat(validCalibration(fixture)).isEqualTo(valid.id());

        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", 5L, 6L, 100));
        var replacement = service.calibrate(calibration(fixture, "// replacement"), ACTOR, null);
        assertThat(replacement.status()).isEqualTo("VALID");
        assertThat(validCalibration(fixture)).isEqualTo(replacement.id());
        assertThat(jdbc.queryForObject("SELECT status FROM language_calibration WHERE id=UUID_TO_BIN(?)",
                String.class, valid.id())).isEqualTo("SUPERSEDED");
    }

    @Test
    void databaseAllowsOnlyOneValidCalibrationPerProblemVersionAndLanguage() throws Exception {
        Fixture fixture = deployed();
        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("AC", 1L, 2L, 100));
        service.calibrate(calibration(fixture, "// valid"), ACTOR, null);
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO language_calibration
                  (id,problem_version_id,language_id,status,source_type,cpu_ns,
                   memory_bytes,approved_by,approved_at,created_at,updated_at,row_version)
                SELECT UUID_TO_BIN(UUID()),problem_version_id,language_id,'VALID','MANUAL',1,1,
                       UUID_TO_BIN(?),?,?,?,0
                FROM language_calibration WHERE problem_version_id=UUID_TO_BIN(?) AND status='VALID'
                """, ACTOR, now, now, now, fixture.problemVersionId()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Fixture deployed() {
        String testData = UUID.randomUUID().toString();
        var metadata = new DeploymentMetadata(testData, SHA, new Manifest(1, 2, List.of(
                new ManifestFile("1.in", 1, "b".repeat(64)), new ManifestFile("1.out", 1, "c".repeat(64)))));
        when(client.install(any(), any(), any(), any())).thenReturn(
                new Receipt("node-readiness", SESSION, testData, SHA, 2));
        service.deploy(metadata, new ByteArrayInputStream(new byte[0]), ACTOR, null);
        return new Fixture(UUID.randomUUID().toString(), UUID.randomUUID().toString(), testData);
    }

    private String validCalibration(Fixture fixture) {
        return jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM language_calibration
                WHERE problem_version_id = UUID_TO_BIN(?) AND status = 'VALID'
                """, String.class, fixture.problemVersionId());
    }

    private static CalibrationRequest calibration(Fixture fixture, String source) {
        return new CalibrationRequest(fixture.problemId(), fixture.problemVersionId(), fixture.testDataId(),
                SHA, "cpp", 1_000_000_000L, 268_435_456L, null, source);
    }

    private record Fixture(String problemId, String problemVersionId, String testDataId) {}
}
