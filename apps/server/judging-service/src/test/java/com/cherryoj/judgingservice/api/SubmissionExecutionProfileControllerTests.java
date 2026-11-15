package com.cherryoj.judgingservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.problem.ProblemTestData;
import com.cherryoj.judgingservice.problem.ProblemTestDataClient;
import com.cherryoj.judgingservice.problem.ProblemTestDataException;
import com.cherryoj.judgingservice.trial.TrialProperties;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class SubmissionExecutionProfileControllerTests {
    private static final String PROBLEM = "019c8e42-7f70-7000-8000-000000000010";
    private static final String DIGEST = "a".repeat(64);

    static SubmissionExecutionProfileController controller(
            JudgingReadinessService service, ProblemTestDataClient testData, Duration cap) {
        return new SubmissionExecutionProfileController(
                service,
                budgets(),
                testData,
                new LanguageCatalog(new tools.jackson.databind.ObjectMapper()),
                new TrialProperties(cap));
    }

    static com.cherryoj.judgingservice.formal.FormalProperties budgets() {
        return new com.cherryoj.judgingservice.formal.FormalProperties(
                false,
                "requests",
                "lifecycle",
                "http://localhost",
                2,
                3,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                10,
                Duration.ofSeconds(1));
    }

    @Test
    void neverFallsBackToDefaultLimitsOrExposesNodeAddress() {
        var service = mock(JudgingReadinessService.class);
        var testData = mock(ProblemTestDataClient.class);
        var controller = controller(service, testData, Duration.ofSeconds(180));
        var request = new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", null);
        when(testData.current(eq(PROBLEM), any())).thenReturn(new ProblemTestData("/data/p", DIGEST, 3, 100, 0L));
        when(service.readiness(PROBLEM, "cpp", DIGEST, 0L))
                .thenReturn(new JudgingDtos.Readiness(false, List.of(), null));
        assertThrows(JudgingApiException.class, () -> controller.resolve(request, null));
        when(service.readiness(PROBLEM, "cpp", DIGEST, 0L))
                .thenReturn(new JudgingDtos.Readiness(
                        true,
                        List.of(),
                        new JudgingDtos.ExecutionProfile("cal", 1000, 2000, 3000L, "BASE", null, null)));
        var profile = controller.resolve(request, null);
        assertEquals("cal", profile.languageCalibrationId());
        assertEquals(1000, profile.effectiveLimits().cpuNs());
        // 预算 = 编译预算 + 每个测试点 (墙钟 + 开销) × 测试点数，测试点数取自题目当前的测试数据
        assertEquals(
                Duration.ofSeconds(30).plusNanos(3 * (3000L + 1_000_000_000L)).toNanos(), profile.executionBudgetNs());
        assertFalse(profile.toString().contains("/data/p"));
    }

    @Test
    void budgetFollowsTheCurrentTestcaseCountAndTrialUsesOneCase() {
        var service = mock(JudgingReadinessService.class);
        var testData = mock(ProblemTestDataClient.class);
        var controller = controller(service, testData, Duration.ofSeconds(180));
        when(service.readiness(PROBLEM, "cpp", DIGEST, 0L))
                .thenReturn(new JudgingDtos.Readiness(
                        true,
                        List.of(),
                        new JudgingDtos.ExecutionProfile("cal", 1000, 2000, 3000L, "BASE", null, null)));
        when(testData.current(eq(PROBLEM), any())).thenReturn(new ProblemTestData("/data/p", DIGEST, 10, 100, 0L));
        long ten = controller
                .resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", "formal"), null)
                .executionBudgetNs();
        when(testData.current(eq(PROBLEM), any())).thenReturn(new ProblemTestData("/data/p", DIGEST, 20, 100, 0L));
        long twenty = controller
                .resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", "formal"), null)
                .executionBudgetNs();
        long trial = controller
                .resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", "trial"), null)
                .executionBudgetNs();
        assertEquals(10 * (3000L + 1_000_000_000L), twenty - ten);
        assertEquals(Duration.ofSeconds(30).plusNanos(3000L + 1_000_000_000L).toNanos(), trial);
    }

    @Test
    void problemWithoutReadableTestDataIsNotReady() {
        var testData = mock(ProblemTestDataClient.class);
        var controller = controller(mock(JudgingReadinessService.class), testData, Duration.ofSeconds(180));
        when(testData.current(any(), any())).thenThrow(new ProblemTestDataException("TEST_DATA_MISSING"));
        var error = assertThrows(
                JudgingApiException.class,
                () -> controller.resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", null), null));
        assertEquals("JUDGING_NOT_READY", error.code());
    }

    @Test
    void twoSecondCompiledTrialIsAdmittedWithoutChangingLimitsAndCapIsInclusive() {
        var service = mock(JudgingReadinessService.class);
        var testData = mock(ProblemTestDataClient.class);
        when(testData.current(eq(PROBLEM), any())).thenReturn(new ProblemTestData("/data/p", DIGEST, 4, 100, 0L));
        when(service.readiness(PROBLEM, "c", DIGEST, 0L))
                .thenReturn(new JudgingDtos.Readiness(
                        true,
                        List.of(),
                        new JudgingDtos.ExecutionProfile(
                                "cal", 2_000_000_000L, 128L << 20, null, "OVERRIDE", null, null)));
        var request = new SubmissionExecutionProfileController.Request(PROBLEM, "c", "trial");
        var accepted = controller(service, testData, Duration.ofSeconds(51)).resolve(request, null);
        assertEquals(51_000_000_000L, accepted.executionBudgetNs());
        assertEquals(2_000_000_000L, accepted.effectiveLimits().cpuNs());
        assertEquals(128L << 20, accepted.effectiveLimits().memoryBytes());
        var limited = controller(service, testData, Duration.ofSeconds(51).minusNanos(1));
        var error = assertThrows(JudgingApiException.class, () -> limited.resolve(request, null));
        assertEquals(422, error.status().value());
        assertEquals("RUN_LIMIT_UNSUPPORTED", error.code());
        assertEquals(
                114_000_000_000L,
                limited.resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "c", "formal"), null)
                        .executionBudgetNs());
    }
}
