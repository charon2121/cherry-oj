package com.cherryoj.judgingservice.formal;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class ExecutionBudgetTests {
    private final FormalProperties properties = new FormalProperties(
            false,
            "requests",
            "lifecycle",
            "http://unused",
            2,
            3,
            Duration.ofSeconds(30),
            Duration.ofMinutes(10),
            Duration.ofSeconds(30),
            10,
            Duration.ofSeconds(1));

    @Test
    void compiledAndInterpretedLanguagesShareRunLimitsButOnlyCompilationConsumesCompileBudget() {
        assertEquals(Duration.ofSeconds(51), properties.executionBudget(true, 2_000_000_000L, null, 1));
        assertEquals(Duration.ofSeconds(21), properties.executionBudget(false, 2_000_000_000L, null, 1));
        assertEquals(Duration.ofSeconds(33), properties.executionBudget(false, 2_000_000_000L, 10_000_000_000L, 3));
    }

    @Test
    void invalidLimitsAndOverflowDoNotBecomeAUsableBudget() {
        for (int count : new int[] {0, 1001})
            assertThrows(IllegalArgumentException.class, () -> properties.executionBudget(true, 1, null, count));
        assertThrows(IllegalArgumentException.class, () -> properties.executionBudget(true, 0, null, 1));
        assertThrows(IllegalArgumentException.class, () -> properties.executionBudget(true, 1, 0L, 1));
        assertThrows(ArithmeticException.class, () -> properties.executionBudget(true, Long.MAX_VALUE, null, 1));
        assertThrows(ArithmeticException.class, () -> properties.executionBudget(false, 1, Long.MAX_VALUE, 2));
    }
}
