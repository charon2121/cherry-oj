package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.cherryoj.judgingservice.problem.ProblemTestData;
import com.cherryoj.judgingservice.problem.ProblemTestDataClient;
import com.cherryoj.judgingservice.problem.ProblemTestDataException;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionExecutionProfileControllerTests {
    private static final String PROBLEM = "019c8e42-7f70-7000-8000-000000000010";
    private static final String DIGEST = "a".repeat(64);

    static com.cherryoj.judgingservice.formal.FormalProperties budgets() {
        return new com.cherryoj.judgingservice.formal.FormalProperties(false,"requests","lifecycle","http://localhost","",2,3,
                Duration.ofSeconds(30),Duration.ofMinutes(10),Duration.ofSeconds(30),10,Duration.ofSeconds(1));
    }

    @Test void neverFallsBackToDefaultLimitsOrExposesNodeAddress() {
        var service = mock(JudgingReadinessService.class);
        var testData = mock(ProblemTestDataClient.class);
        var controller = new SubmissionExecutionProfileController(service, budgets(), testData);
        var request = new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", null);
        when(testData.current(eq(PROBLEM), any())).thenReturn(new ProblemTestData("/data/p", DIGEST, 3, 100));
        when(service.readiness(PROBLEM, "cpp", DIGEST)).thenReturn(new JudgingDtos.Readiness(false, List.of(), null));
        assertThrows(JudgingApiException.class, () -> controller.resolve(request, null));
        when(service.readiness(PROBLEM, "cpp", DIGEST)).thenReturn(new JudgingDtos.Readiness(true, List.of(),
                new JudgingDtos.ExecutionProfile("cal", 1000, 2000, 3000L)));
        var profile = controller.resolve(request, null);
        assertEquals("cal", profile.languageCalibrationId());
        assertEquals(1000, profile.effectiveLimits().cpuNs());
        // 预算 = 编译预算 + 每个测试点 (墙钟 + 开销) × 测试点数，测试点数取自题目当前的测试数据
        assertEquals(Duration.ofSeconds(30).plusNanos(3 * (3000L + 1_000_000_000L)).toNanos(), profile.executionBudgetNs());
        assertFalse(profile.toString().contains("/data/p"));
    }

    @Test void budgetFollowsTheCurrentTestcaseCountAndTrialUsesOneCase() {
        var service = mock(JudgingReadinessService.class);
        var testData = mock(ProblemTestDataClient.class);
        var controller = new SubmissionExecutionProfileController(service, budgets(), testData);
        when(service.readiness(PROBLEM, "cpp", DIGEST)).thenReturn(new JudgingDtos.Readiness(true, List.of(),
                new JudgingDtos.ExecutionProfile("cal", 1000, 2000, 3000L)));
        when(testData.current(eq(PROBLEM), any())).thenReturn(new ProblemTestData("/data/p", DIGEST, 10, 100));
        long ten = controller.resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", "formal"), null).executionBudgetNs();
        when(testData.current(eq(PROBLEM), any())).thenReturn(new ProblemTestData("/data/p", DIGEST, 20, 100));
        long twenty = controller.resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", "formal"), null).executionBudgetNs();
        long trial = controller.resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", "trial"), null).executionBudgetNs();
        assertEquals(10 * (3000L + 1_000_000_000L), twenty - ten);
        assertEquals(Duration.ofSeconds(30).plusNanos(3000L + 1_000_000_000L).toNanos(), trial);
    }

    @Test void problemWithoutReadableTestDataIsNotReady() {
        var testData = mock(ProblemTestDataClient.class);
        var controller = new SubmissionExecutionProfileController(mock(JudgingReadinessService.class), budgets(), testData);
        when(testData.current(any(), any())).thenThrow(new ProblemTestDataException("TEST_DATA_MISSING"));
        var error = assertThrows(JudgingApiException.class, () ->
                controller.resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", null), null));
        assertEquals("JUDGING_NOT_READY", error.code());
    }
}
