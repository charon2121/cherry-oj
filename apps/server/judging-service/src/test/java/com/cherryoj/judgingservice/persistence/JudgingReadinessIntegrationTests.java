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
import com.cherryoj.judgingservice.limits.LanguageLimitModels;
import com.cherryoj.judgingservice.limits.LimitModel;
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

    @Autowired
    JudgingReadinessService service;

    @Autowired
    JudgeNodeRegistry registry;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    JudgeGateway judge;

    /** cpp 用真实参数；python 默认没有参数，个别用例给它一组 Java 那样的参数来走推导。 */
    @MockitoBean
    LanguageLimitModels models;

    private static final LimitModel CPP = new LimitModel(1000, null, 0, 0, 1000, 0, true);
    private static final LimitModel JAVA_LIKE = new LimitModel(3200, null, 170_000_000L, 0, 1250, 40L << 20, false);

    @BeforeEach
    void onlineNode() {
        when(models.of("cpp")).thenReturn(CPP);
        jdbc.update("DELETE FROM judging_audit_event");
        jdbc.update("UPDATE language_calibration SET supersedes_id = NULL");
        jdbc.update("DELETE FROM language_calibration");
        registry.register(new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp")));
    }

    @Test
    void acCalibrationMakesReadinessTrueAndAuditHoldsNoSource() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        assertThat(service.readiness(problem, "cpp", DIGEST, 0).ready()).isFalse();

        CalibrationRequest request = calibration(problem, DIGEST, "int main(){return 0;}");
        var calibrated = service.calibrate(request, ACTOR, "trace-1");
        assertThat(calibrated.status()).isEqualTo("VALID");
        assertThat(calibrated.testDataDigest()).isEqualTo(DIGEST);
        assertThat(calibrated.benchmarkSummary().verdict()).isEqualTo("AC");

        var readiness = service.readiness(problem, "cpp", DIGEST, 0);
        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.checks()).allMatch(check -> check.passed());
        assertThat(readiness.executionProfile().cpuNs()).isEqualTo(1_000_000_000L);
        // 批准时间随执行配置返回：problem-service 据此判断驱动代码或前缀是否在标定之后改过
        assertThat(readiness.executionProfile().calibratedAt()).isEqualTo(calibrated.updatedAt());
        String details = String.join(
                "", jdbc.queryForList("SELECT CAST(detail_json AS CHAR) FROM judging_audit_event", String.class));
        assertThat(details).doesNotContain(request.referenceSource());
    }

    @Test
    void calibrationRunsAgainstTheAddressItWasGivenAndAnyOnlineNode() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(calibration(problem, DIGEST, "// ref"), ACTOR, null);
        var captured = org.mockito.ArgumentCaptor.forClass(JudgeGateway.JudgeRequest.class);
        org.mockito.Mockito.verify(judge)
                .judge(org.mockito.ArgumentMatchers.eq("http://127.0.0.1:15051"), captured.capture(), any());
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

        var stale = service.readiness(problem, "cpp", newDigest, 0);
        assertThat(stale.ready()).isFalse();
        assertThat(stale.executionProfile()).isNull();
        assertThat(stale.checks())
                .filteredOn(check -> !check.passed())
                .extracting("code")
                .containsExactly("CALIBRATION");

        when(judge.judge(anyString(), any(), any())).thenReturn(ac(newDigest));
        var fresh = service.calibrate(calibration(problem, newDigest, "// ref"), ACTOR, null);
        assertThat(service.readiness(problem, "cpp", newDigest, 0).ready()).isTrue();
        assertThat(service.readiness(problem, "cpp", DIGEST, 0).ready()).isFalse();
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
        assertThat(service.readiness(problem, "cpp", DIGEST, 0).ready()).isFalse();
        // 判题结果没带指纹同样不能当作成功
        when(judge.judge(anyString(), any(), any())).thenReturn(new JudgeGateway.JudgeResult("AC", 1L, 2L, 100));
        assertThat(service.calibrate(calibration(problem, DIGEST, "// ref2"), ACTOR, null)
                        .status())
                .isEqualTo("FAILED");
    }

    @Test
    void failedJudgeNeverSupersedesExistingValidCalibration() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        var valid = service.calibrate(calibration(problem, DIGEST, "// valid"), ACTOR, null);

        when(judge.judge(anyString(), any(), any()))
                .thenReturn(new JudgeGateway.JudgeResult("WA", 3L, 4L, 0, null, null, DIGEST));
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
        assertThatThrownBy(() -> service.calibrate(
                        new CalibrationRequest(
                                problem,
                                "python",
                                "OVERRIDE",
                                LOCATION,
                                DIGEST,
                                1_000_000_000L,
                                268_435_456L,
                                null,
                                "print(1)",
                                null,
                                null),
                        ACTOR,
                        null))
                .isInstanceOf(JudgingApiException.class)
                .hasMessageContaining("没有在线判题节点");
    }

    @Test
    void databaseAllowsOnlyOneValidCalibrationPerProblemAndLanguage() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(calibration(problem, DIGEST, "// valid"), ACTOR, null);
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO language_calibration
                  (id,problem_id,language_id,kind,test_data_digest,status,source_type,cpu_ns,
                   memory_bytes,approved_by,approved_at,created_at,updated_at,row_version)
                SELECT UUID_TO_BIN(UUID()),problem_id,language_id,'OVERRIDE',test_data_digest,'VALID','MANUAL',1,1,
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

    @Test
    void coreCalibrationJudgesTheDriverTogetherWithTheReferenceSolutionFile() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        String solution = "#include <bits/stdc++.h>\nusing namespace std;\nclass Solution {};\n";

        var calibrated = service.calibrate(
                new CalibrationRequest(
                        problem,
                        "cpp",
                        "BASE",
                        LOCATION,
                        DIGEST,
                        1_000_000_000L,
                        268_435_456L,
                        null,
                        "#include \"solution.cpp\"\nint main(){}\n",
                        solution,
                        2),
                ACTOR,
                null);

        assertThat(calibrated.status()).isEqualTo("VALID");
        var sent = org.mockito.ArgumentCaptor.forClass(JudgeGateway.JudgeRequest.class);
        org.mockito.Mockito.verify(judge).judge(anyString(), sent.capture(), any());
        assertThat(sent.getValue().source()).startsWith("#include \"solution.cpp\"");
        assertThat(sent.getValue().solutionSource()).isEqualTo(solution);
    }

    @Test
    void aCoreCalibrationNeedsBothTheSolutionFileAndThePrefixLineCount() {
        String problem = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.calibrate(
                        new CalibrationRequest(
                                problem,
                                "cpp",
                                "BASE",
                                LOCATION,
                                DIGEST,
                                1_000_000_000L,
                                268_435_456L,
                                null,
                                "driver",
                                "class Solution {};",
                                null),
                        ACTOR,
                        null))
                .isInstanceOf(JudgingApiException.class)
                .hasMessageContaining("同时给出");
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM language_calibration WHERE problem_id = UUID_TO_BIN(?)",
                        Integer.class,
                        problem))
                .isZero();
    }

    @Test
    void onlyBaseEligibleLanguagesCanBeTheBase() {
        // python 没有限制参数，只能靠覆盖标定开放，不能做基础语言
        registry.register(
                new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp", "python")));
        String problem = UUID.randomUUID().toString();
        assertThatThrownBy(() -> service.calibrate(
                        request(problem, "python", "BASE", DIGEST, 1_000_000_000L, 268_435_456L), ACTOR, null))
                .isInstanceOfSatisfying(JudgingApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("LANGUAGE_NOT_BASE_ELIGIBLE");
                    assertThat(error.status().value()).isEqualTo(400);
                });
    }

    @Test
    void handWrittenLimitsAreBoundByThePlatformLimits() {
        // application.yaml 里平台上限是 10s / 1GiB：手填的限制和推导值受同一组上限约束
        String problem = UUID.randomUUID().toString();
        for (var limits : new long[][] {{10_000_000_001L, 268_435_456L}, {1_000_000_000L, (1L << 30) + 1}}) {
            assertThatThrownBy(() -> service.calibrate(
                            request(problem, "cpp", "BASE", DIGEST, limits[0], limits[1]), ACTOR, null))
                    .isInstanceOfSatisfying(JudgingApiException.class, error -> {
                        assertThat(error.code()).isEqualTo("LIMIT_ABOVE_PLATFORM");
                        assertThat(error.status().value()).isEqualTo(400);
                    });
        }
        assertThat(jdbc.queryForObject(
                        "SELECT COUNT(*) FROM language_calibration WHERE problem_id = UUID_TO_BIN(?)",
                        Integer.class,
                        problem))
                .isZero();
    }

    @Test
    void theCurrentBaseLanguageCannotBeOverridden() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(calibration(problem, DIGEST, "// base"), ACTOR, null);
        assertThatThrownBy(() -> service.calibrate(
                        request(problem, "cpp", "OVERRIDE", DIGEST, 1_000_000_000L, 268_435_456L), ACTOR, null))
                .isInstanceOfSatisfying(JudgingApiException.class, error -> {
                    assertThat(error.code()).isEqualTo("OVERRIDE_ON_BASE_LANGUAGE");
                    assertThat(error.status().value()).isEqualTo(409);
                });
    }

    @Test
    void aNewBaseReplacesTheOverrideOfItsOwnLanguageButKeepsOtherOverrides() throws Exception {
        registry.register(
                new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp", "python")));
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        var python = service.calibrate(
                request(problem, "python", "OVERRIDE", DIGEST, 3_000_000_000L, 268_435_456L), ACTOR, null);
        var cppOverride = service.calibrate(
                request(problem, "cpp", "OVERRIDE", DIGEST, 1_000_000_000L, 268_435_456L), ACTOR, null);
        assertThat(cppOverride.kind()).isEqualTo("OVERRIDE");

        var base = service.calibrate(calibration(problem, DIGEST, "// base"), ACTOR, null);

        assertThat(base.kind()).isEqualTo("BASE");
        assertThat(status(base.id())).isEqualTo("VALID");
        assertThat(status(cppOverride.id())).isEqualTo("SUPERSEDED");
        assertThat(status(python.id())).isEqualTo("VALID");
    }

    @Test
    void databaseAllowsOnlyOneValidBasePerProblem() throws Exception {
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(calibration(problem, DIGEST, "// base"), ACTOR, null);
        LocalDateTime now = LocalDateTime.now();
        assertThatThrownBy(() -> jdbc.update("""
                INSERT INTO language_calibration
                  (id,problem_id,language_id,kind,test_data_digest,status,source_type,cpu_ns,
                   memory_bytes,approved_by,approved_at,created_at,updated_at,row_version)
                SELECT UUID_TO_BIN(UUID()),problem_id,'other','BASE',test_data_digest,'VALID','MANUAL',1,1,
                       UUID_TO_BIN(?),?,?,?,0
                FROM language_calibration WHERE problem_id=UUID_TO_BIN(?) AND status='VALID'
                """, ACTOR, now, now, now, problem))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void otherLanguagesAreDerivedFromTheBaseAndCarryTheirInputs() throws Exception {
        registry.register(
                new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp", "python")));
        when(models.of("python")).thenReturn(JAVA_LIKE);
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        var base = service.calibrate(calibration(problem, DIGEST, "// base"), ACTOR, null);

        var readiness = service.readiness(problem, "python", DIGEST, 2_000_000);

        assertThat(readiness.ready()).isTrue();
        var profile = readiness.executionProfile();
        assertThat(profile.limitSource()).isEqualTo("DERIVED");
        assertThat(profile.calibrationId()).isEqualTo(base.id());
        // 协议黄金用例：C++ 1s / 256MiB → 3.37s / 360MiB；推导值不带墙钟
        assertThat(profile.cpuNs()).isEqualTo(3_370_000_000L);
        assertThat(profile.memoryBytes()).isEqualTo(360L << 20);
        assertThat(profile.clockNs()).isNull();
        var derivation = profile.limitDerivation();
        assertThat(derivation.baseLanguageId()).isEqualTo("cpp");
        assertThat(derivation.baseCalibrationId()).isEqualTo(base.id());
        assertThat(derivation.maxTestcaseIoBytes()).isEqualTo(2_000_000);
        assertThat(derivation.platformMaxCpuNs()).isEqualTo(10_000_000_000L);
        assertThat(derivation.cpuCapped()).isFalse();
        assertThat(derivation.languageLimitModel().cpuFactorPermille()).isEqualTo(3200);

        // 基础语言自己就是基础标定，不推导
        var own = service.readiness(problem, "cpp", DIGEST, 2_000_000).executionProfile();
        assertThat(own.limitSource()).isEqualTo("BASE");
        assertThat(own.limitDerivation()).isNull();
    }

    @Test
    void derivedValuesAbovePlatformLimitsAreCappedAndTheLanguageStaysReady() throws Exception {
        registry.register(
                new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp", "python")));
        when(models.of("python")).thenReturn(JAVA_LIKE);
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(request(problem, "cpp", "BASE", DIGEST, 8_000_000_000L, 1L << 30), ACTOR, null);

        var readiness = service.readiness(problem, "python", DIGEST, 0);

        assertThat(readiness.ready()).isTrue();
        assertThat(readiness.executionProfile().cpuNs()).isEqualTo(10_000_000_000L);
        assertThat(readiness.executionProfile().memoryBytes()).isEqualTo(1L << 30);
        assertThat(readiness.executionProfile().limitDerivation().cpuCapped()).isTrue();
        assertThat(readiness.executionProfile().limitDerivation().memoryCapped())
                .isTrue();
    }

    @Test
    void aLanguageWithoutParametersNeedsAnOverrideWhichThenWins() throws Exception {
        registry.register(
                new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp", "python")));
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(calibration(problem, DIGEST, "// base"), ACTOR, null);

        var missing = service.readiness(problem, "python", DIGEST, 0);
        assertThat(missing.ready()).isFalse();
        assertThat(missing.checks()).anySatisfy(check -> {
            assertThat(check.code()).isEqualTo("LIMIT_MODEL");
            assertThat(check.passed()).isFalse();
        });

        var override = service.calibrate(
                request(problem, "python", "OVERRIDE", DIGEST, 2_000_000_000L, 268_435_456L), ACTOR, null);
        var profile = service.readiness(problem, "python", DIGEST, 0).executionProfile();
        assertThat(profile.limitSource()).isEqualTo("OVERRIDE");
        assertThat(profile.calibrationId()).isEqualTo(override.id());
        assertThat(profile.cpuNs()).isEqualTo(2_000_000_000L);
        assertThat(profile.limitDerivation()).isNull();
    }

    @Test
    void anOverrideForOldDataFallsBackToDerivation() throws Exception {
        registry.register(
                new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp", "python")));
        when(models.of("python")).thenReturn(JAVA_LIKE);
        String problem = UUID.randomUUID().toString();
        String newDigest = "b".repeat(64);
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        service.calibrate(request(problem, "python", "OVERRIDE", DIGEST, 2_000_000_000L, 268_435_456L), ACTOR, null);
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(newDigest));
        service.calibrate(calibration(problem, newDigest, "// base"), ACTOR, null);

        // 覆盖标定对应旧数据，已经过期：按新数据的基础标定推导
        var profile = service.readiness(problem, "python", newDigest, 0).executionProfile();
        assertThat(profile.limitSource()).isEqualTo("DERIVED");
        assertThat(profile.cpuNs()).isEqualTo(3_370_000_000L);
    }

    @Test
    void limitsListEveryRequestedLanguageWithItsSourceInRequestOrder() throws Exception {
        registry.register(
                new Registration("node-readiness", SESSION, "http://127.0.0.1:15051", List.of("cpp", "python")));
        when(models.of("python")).thenReturn(JAVA_LIKE);
        String problem = UUID.randomUUID().toString();
        when(judge.judge(anyString(), any(), any())).thenReturn(ac(DIGEST));
        var base = service.calibrate(request(problem, "cpp", "BASE", DIGEST, 8_000_000_000L, 256L << 20), ACTOR, null);

        // ruby 既没有参数也没有覆盖标定，也没有在线节点：限制页照样列出它，并说明原因
        var rows = service.limits(problem, List.of("python", "ruby", "cpp"), DIGEST, 0)
                .languages();

        assertThat(rows).extracting(row -> row.languageId()).containsExactly("python", "ruby", "cpp");
        var python = rows.get(0);
        assertThat(python.limitSource()).isEqualTo("DERIVED");
        assertThat(python.calibrationId()).isEqualTo(base.id());
        assertThat(python.cpuNs()).isEqualTo(10_000_000_000L);
        assertThat(python.cpuCapped()).isTrue();
        assertThat(python.memoryCapped()).isFalse();
        assertThat(python.unavailableReason()).isNull();
        var ruby = rows.get(1);
        assertThat(ruby.limitSource()).isNull();
        assertThat(ruby.cpuNs()).isNull();
        assertThat(ruby.unavailableReason()).isEqualTo("NO_LIMIT_MODEL");
        assertThat(rows.get(2).limitSource()).isEqualTo("BASE");
        assertThat(rows.get(2).cpuNs()).isEqualTo(8_000_000_000L);

        // 数据换了，旧标定一律过期
        assertThat(service.limits(problem, List.of("cpp"), "b".repeat(64), 0)
                        .languages()
                        .getFirst()
                        .unavailableReason())
                .isEqualTo("NO_CALIBRATION");
    }

    private static CalibrationRequest request(
            String problem, String language, String kind, String digest, long cpuNs, long memoryBytes) {
        return new CalibrationRequest(
                problem, language, kind, LOCATION, digest, cpuNs, memoryBytes, null, "// source", null, null);
    }

    private static CalibrationRequest calibration(String problem, String digest, String source) {
        return new CalibrationRequest(
                problem, "cpp", "BASE", LOCATION, digest, 1_000_000_000L, 268_435_456L, null, source, null, null);
    }
}
