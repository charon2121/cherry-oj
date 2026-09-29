package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgingReadinessService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionExecutionProfileControllerTests {
    static com.cherryoj.judgingservice.formal.FormalProperties budgets() {
        return new com.cherryoj.judgingservice.formal.FormalProperties(false,"requests","lifecycle","http://localhost","",2,3,
                Duration.ofSeconds(30),Duration.ofMinutes(10),Duration.ofSeconds(30),10,Duration.ofSeconds(1));
    }
    @Test void neverFallsBackToDefaultLimitsOrExposesNodeAddress() {
        var service = mock(JudgingReadinessService.class);
        var controller = new SubmissionExecutionProfileController(service, budgets());
        var request = new SubmissionExecutionProfileController.Request("p", "d", "a".repeat(64), "cpp",3);
        when(service.readiness("p", "d", "a".repeat(64), "cpp"))
                .thenReturn(new JudgingDtos.Readiness(false, List.of(), null));
        assertThrows(JudgingApiException.class, () -> controller.resolve(request));
        when(service.readiness("p", "d", "a".repeat(64), "cpp"))
                .thenReturn(new JudgingDtos.Readiness(true, List.of(),
                        new JudgingDtos.ExecutionProfile("http://private-node", "cal", 1000, 2000, 3000L)));
        var profile = controller.resolve(request);
        assertEquals("cal", profile.languageCalibrationId());
        assertEquals(1000, profile.effectiveLimits().cpuNs());
        assertFalse(profile.toString().contains("private-node"));
    }
}
