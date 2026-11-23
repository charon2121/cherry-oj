package com.cherryoj.judgingservice.api;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cherryoj.judgingservice.application.ExecutionProfileService;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Purpose;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Reason;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Rejected;
import com.cherryoj.judgingservice.limits.ResolvedLanguageLimits;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class SubmissionExecutionProfileControllerTests {
    private static final String PROBLEM = "019c8e42-7f70-7000-8000-000000000010";
    private final ExecutionProfileService profiles = mock(ExecutionProfileService.class);
    private final SubmissionExecutionProfileController controller = new SubmissionExecutionProfileController(profiles);

    @Test
    void defaultPurposeReturnsContractFieldsAndOmitsUnsetWallLimit() {
        var limits = new ResolvedLanguageLimits("cal", 1000, 2000, null, "BASE", null, null);
        when(profiles.resolve(PROBLEM, "cpp", Purpose.FORMAL, "trace"))
                .thenReturn(new ExecutionProfileService.ExecutionProfile(limits, 3000));
        var result =
                controller.resolve(new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", null), "trace");
        var json = new ObjectMapper().valueToTree(result);
        assertEquals(PROBLEM, json.path("problemId").asText());
        assertEquals("cpp", json.path("languageId").asText());
        assertEquals("cal", json.path("languageCalibrationId").asText());
        assertEquals(1000, json.path("effectiveLimits").path("cpuNs").asLong());
        assertEquals(2000, json.path("effectiveLimits").path("memoryBytes").asLong());
        assertFalse(json.path("effectiveLimits").has("clockNs"));
        assertEquals("BASE", json.path("limitSource").asText());
        assertTrue(json.path("limitDerivation").isNull());
        assertEquals(3000, json.path("executionBudgetNs").asLong());
    }

    @Test
    void rejectionReasonsKeepTheHttpStatusAndCode() {
        var rejected = mock(Rejected.class);
        when(profiles.resolve(PROBLEM, "cpp", Purpose.TRIAL, null)).thenThrow(rejected);
        var request = new SubmissionExecutionProfileController.Request(PROBLEM, "cpp", "trial");

        when(rejected.reason()).thenReturn(Reason.TRIAL_BUDGET_EXCEEDED);
        var budget = assertThrows(JudgingApiException.class, () -> controller.resolve(request, null));
        assertEquals(422, budget.status().value());
        assertEquals("RUN_LIMIT_UNSUPPORTED", budget.code());

        when(rejected.reason()).thenReturn(Reason.NOT_READY);
        var unavailable = assertThrows(JudgingApiException.class, () -> controller.resolve(request, null));
        assertEquals(503, unavailable.status().value());
        assertEquals("JUDGING_NOT_READY", unavailable.code());
    }
}
