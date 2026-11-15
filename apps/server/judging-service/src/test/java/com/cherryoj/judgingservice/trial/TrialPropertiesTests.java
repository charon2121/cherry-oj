package com.cherryoj.judgingservice.trial;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class TrialPropertiesTests {
    @Test
    void rejectsAbsentZeroNegativeAndUnboundedCaps() {
        for (Duration value : new Duration[] {
            null, Duration.ZERO, Duration.ofNanos(-1), Duration.ofSeconds(180).plusNanos(1)
        }) assertThrows(IllegalArgumentException.class, () -> new TrialProperties(value));
        assertEquals(Duration.ofSeconds(180), new TrialProperties(Duration.ofSeconds(180)).maxExecutionBudget());
    }
}
