package com.cherryoj.judgingservice.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import com.cherryoj.judgingservice.api.JudgeNodeDtos.Registration;
import com.cherryoj.judgingservice.api.JudgingApiException;
import com.cherryoj.judgingservice.api.JudgingDtos.CalibrationRequest;
import com.cherryoj.judgingservice.application.JudgeNodeRegistry;
import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.judge.JudgeGateway;
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

/** 标定与就绪判定：标定按「题目 × 语言」记录并带测试数据指纹，判题结果由模拟网关给出。 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class JudgingReadinessIntegrationTests {
    private static final String ACTOR = "019c8e42-7f70-7000-8000-000000000001";
    private static final String SESSION = UUID.randomUUID().toString();
    private static final String DIGEST = "a".repeat(64);
    private static final String LOCATION = "/data/problems/p";

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

    @BeforeEach
    void onlineNode() {
        jdbc.update("DELETE FROM judging_audit_event");
        jdbc.update("UPDATE language_calibration SET supersedes_id = NULL");
        jdbc.update("DELETE FROM language_calibration");
        registry.register(new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp")));
    }

    @Test
    void acCalibrationMakesReadinessTrueAndAuditHoldsNoSource() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        assertThat(service.readiness(problem, "cpp", DIGEST).ready()).isFalse();

        CalibrationRequest request = calibration(problem, DIGEST, "int main(){return 0;}");
        var calibrated = service.calibrate(request, ACTOR, "trace-1");
        assertThat(calibrated.status()).isEqualTo("VALID");
        assertThat(calibrated.testDataDigest()).isEqualTo(DIGEST);
        assertThat(calibrated.benchmarkSummary().verdict()).isEqualTo("AC");

        var readiness = service.readiness(problem, "cpp", DIGEST);
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.checks()).allMatch(check -> check.passed());
        assertThat(readiness.executionProfile().cpuNs()).isEqualTo(1_000_000_000L);
        String details = String.join("", jdbc.queryForList(
                "SELECT CAST(detail_json AS CHAR) FROM judging_audit_event", String.class));
        assertThat(details).doesNotContain(request.referenceSource());
    }

    @Test
    void calibrationRunsAgainstTheAddressItWasGivenAndAnyOnlineNode() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(calibration(problem, DIGEST, "// ref"), ACTOR, null);
        var captured = org.mockito.ArgumentCaptor.forClass(JudgeGateway.JudgeRequest.class);
        org.mockito.Mockito.verify(judge).judge(org.mockito.ArgumentMatchers.eq("http://127.0.0.1:15051"), captured.capture(), any());
        assertThat(captured.getValue().testDataLocation()).isEqualTo(LOCATION);
        assertThat(captured.getValue().problemId()).isEqualTo(problem);
        assertThat(captured.getValue().mode()).isEqualTo("submit");
    }

    @Test
    void changedTestDataMakesTheOldCalibrationStaleUntilRecalibrated() throws Exception {
        String problem = UUID.randomUUID().toString();
        String newDigest = "b".repeat(64);
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        var old = service.calibrate(calibration(problem, DIGEST, "// ref"), ACTOR, null);

        var stale = service.readiness(problem, "cpp", newDigest);
        assertThat(stale.ready()).isFalse();
        assertThat(stale.executionProfile()).isNull();
        assertThat(stale.checks()).filteredOn(check -> !check.passed()).extracting("code").containsExactly("CALIBRATION");

        when(judge.judge(anyString(), any(), any())).thenReturn(ac(newDigest));
        var fresh = service.calibrate(calibration(problem, newDigest, "// ref"), ACTOR, null);
        assertThat(service.readiness(problem, "cpp", newDigest).ready()).isTrue();
        assertThat(service.readiness(problem, "cpp", DIGEST).ready()).isFalse();
        assertThat(status(old.id())).isEqualTo("SUPERSEDED");
        assertThat(status(fresh.id())).isEqualTo("VALID");
    }

    @Test
    void calibrationIsVoidedWhenJudgeReadsADifferentTestDataThanRequested() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac("c".repeat(64)));
        var result = service.calibrate(calibration(problem, DIGEST, "// ref"), ACTOR, null);
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorMessage()).isEqualTo("TEST_DATA_CHANGED");
        assertThat(service.readiness(problem, "cpp", DIGEST).ready()).isFalse();
        // 判题结果没带指纹同样不能当作成功
        when(judge.judge(anyString(), any(), any())).thenReturn(new JudgeGateway.JudgeResult("AC", 1L, 2L, 100));
        assertThat(service.calibrate(calibration(problem, DIGEST, "// ref2"), ACTOR, null).status()).isEqualTo("FAILED");
    }

    @Test
    void failedJudgeNeverSupersedesExistingValidCalibration() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        var valid = service.calibrate(calibration(problem, DIGEST, "// valid"), ACTOR, null);

        when(judge.judge(anyString(), any(), any())).thenReturn(
                new JudgeGateway.JudgeResult("WA", 3L, 4L, 0, null, null, DIGEST));
        var failed = service.calibrate(calibration(problem, DIGEST, "// wrong"), ACTOR, null);
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.benchmarkSummary().verdict()).isEqualTo("WA");
        assertThat(validCalibration(problem)).isEqualTo(valid.id());

        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        var replacement = service.calibrate(calibration(problem, DIGEST, "// replacement"), ACTOR, null);
        assertThat(replacement.status()).isEqualTo("VALID");
        assertThat(validCalibration(problem)).isEqualTo(replacement.id());
        assertThat(status(valid.id())).isEqualTo("SUPERSEDED");
    }

    @Test
    void calibrationNeedsAnOnlineNodeForTheLanguage() {
        String problem = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.calibrate(new CalibrationRequest(problem, "python", LOCATION, DIGEST,
                1_000_000_000L, 268_435_456L, null, "print(1)"), ACTOR, null))
                .isInstanceOf(JudgingApiException.class).hasMessageContaining("没有在线判题节点");
    }

    @Test
    void databaseAllowsOnlyOneValidCalibrationPerProblemAndLanguage() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(calibration(problem, DIGEST, "// valid"), ACTOR, null);
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO language_calibration
                  (id,problem_id,language_id,test_data_digest,status,source_type,cpu_ns,
                   memory_bytes,approved_by,approved_at,created_at,updated_at,row_version)
                SELECT UUID_TO_BIN(UUID()),problem_id,language_id,test_data_digest,'VALID','MANUAL',1,1,
                       UUID_TO_BIN(?),?,?,?,0
                FROM language_calibration WHERE problem_id=UUID_TO_BIN(?) AND status='VALID'
                """, ACTOR, now, now, now, problem))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private String status(String id) {
        return jdbc.queryForObject("SELECT status FROM language_calibration WHERE id=UUID_TO_BIN(?)", String.class, id);
    }

    private String validCalibration(String problem) {
        return jdbc.queryForObject("""
                SELECT BIN_TO_UUID(id) FROM language_calibration
                WHERE problem_id = UUID_TO_BIN(?) AND status = 'VALID'
                """, String.class, problem);
    }

    private static JudgeGateway.JudgeResult ac(String digest) {
        return new JudgeGateway.JudgeResult("AC", 12L, 4096L, 100, null, null, digest);
    }

    private static CalibrationRequest calibration(String problem, String digest, String source) {
        return new CalibrationRequest(problem, "cpp", LOCATION, digest, 1_000_000_000L, 268_435_456L, null, source);
    }
}
