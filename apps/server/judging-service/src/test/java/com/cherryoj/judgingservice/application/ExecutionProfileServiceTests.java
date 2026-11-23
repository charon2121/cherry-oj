package com.cherryoj.judgingservice.application;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cherryoj.judgingservice.application.ExecutionProfileService.Purpose;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Reason;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Rejected;
import com.cherryoj.judgingservice.formal.FormalProperties;
import com.cherryoj.judgingservice.judge.LanguageCatalog;
import com.cherryoj.judgingservice.limits.ResolvedLanguageLimits;
import com.cherryoj.judgingservice.problem.ProblemTestData;
import com.cherryoj.judgingservice.problem.ProblemTestDataClient;
import com.cherryoj.judgingservice.problem.ProblemTestDataException;
import com.cherryoj.judgingservice.trial.CustomRunProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class ExecutionProfileServiceTests {
    private static final String PROBLEM = "019c8e42-7f70-7000-8000-000000000010";
    private static final String DIGEST = "a".repeat(64);
    private static final Duration TRIAL_CAP = Duration.ofSeconds(180);
    private final JudgingReadinessService readiness = mock(JudgingReadinessService.class);
    private final ProblemTestDataClient testData = mock(ProblemTestDataClient.class);

    @Test
    void neverFallsBackToDefaultLimits() {
        var service = service(TRIAL_CAP);
        when(testData.current(PROBLEM, "trace")).thenReturn(data(3));

        var error = assertThrows(Rejected.class, () -> service.resolve(PROBLEM, "cpp", Purpose.FORMAL, "trace"));
        assertEquals(Reason.NOT_READY, error.reason());

        var limits = new ResolvedLanguageLimits("cal", 1000, 2000, 3000L, "BASE", null, null);
        when(readiness.readyLimits(PROBLEM, "cpp", DIGEST, 0L)).thenReturn(limits);
        var profile = service.resolve(PROBLEM, "cpp", Purpose.FORMAL, "trace");

        assertEquals(limits, profile.limits());
        // 预算 = 编译预算 + 每个测试点 (墙钟 + 开销) × 当前测试点数。
        assertEquals(
                Duration.ofSeconds(30).plusNanos(3 * (3000L + 1_000_000_000L)).toNanos(), profile.executionBudgetNs());
        assertFalse(profile.toString().contains("/data/p"));
    }

    @Test
    void budgetFollowsTheCurrentTestcaseCountAndTrialUsesOneCase() {
        var service = service(TRIAL_CAP);
        when(readiness.readyLimits(PROBLEM, "cpp", DIGEST, 0L))
                .thenReturn(new ResolvedLanguageLimits("cal", 1000, 2000, 3000L, "BASE", null, null));
        when(testData.current(PROBLEM, null)).thenReturn(data(10), data(20), data(20));

        long ten = service.resolve(PROBLEM, "cpp", Purpose.FORMAL, null).executionBudgetNs();
        long twenty = service.resolve(PROBLEM, "cpp", Purpose.FORMAL, null).executionBudgetNs();
        long trial = service.resolve(PROBLEM, "cpp", Purpose.TRIAL, null).executionBudgetNs();

        assertEquals(10 * (3000L + 1_000_000_000L), twenty - ten);
        assertEquals(Duration.ofSeconds(30).plusNanos(3000L + 1_000_000_000L).toNanos(), trial);
    }

    @Test
    void problemWithoutReadableTestDataIsNotReady() {
        when(testData.current(PROBLEM, null)).thenThrow(new ProblemTestDataException("TEST_DATA_MISSING"));

        var error =
                assertThrows(Rejected.class, () -> service(TRIAL_CAP).resolve(PROBLEM, "cpp", Purpose.FORMAL, null));

        assertEquals(Reason.NOT_READY, error.reason());
        verifyNoInteractions(readiness);
    }

    @Test
    void twoSecondCompiledTrialIsAdmittedWithoutChangingLimitsAndCapIsInclusive() {
        when(testData.current(PROBLEM, null)).thenReturn(data(4));
        var limits = new ResolvedLanguageLimits("cal", 2_000_000_000L, 128L << 20, null, "OVERRIDE", null, null);
        when(readiness.readyLimits(PROBLEM, "c", DIGEST, 0L)).thenReturn(limits);

        var accepted = service(Duration.ofSeconds(51)).resolve(PROBLEM, "c", Purpose.TRIAL, null);
        assertEquals(51_000_000_000L, accepted.executionBudgetNs());
        assertEquals(limits, accepted.limits());

        var limited = service(Duration.ofSeconds(51).minusNanos(1));
        var error = assertThrows(Rejected.class, () -> limited.resolve(PROBLEM, "c", Purpose.TRIAL, null));
        assertEquals(Reason.TRIAL_BUDGET_EXCEEDED, error.reason());
        assertEquals(
                114_000_000_000L,
                limited.resolve(PROBLEM, "c", Purpose.FORMAL, null).executionBudgetNs());
    }

    private ExecutionProfileService service(Duration cap) {
        var budgets = new FormalProperties(
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
        return new ExecutionProfileService(
                readiness, budgets, testData, new LanguageCatalog(new ObjectMapper()), new CustomRunProperties(cap));
    }

    private static ProblemTestData data(int count) {
        return new ProblemTestData("/data/p", DIGEST, count, 100, 0L);
    }
}
