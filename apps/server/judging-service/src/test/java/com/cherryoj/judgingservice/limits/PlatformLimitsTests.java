package com.cherryoj.judgingservice.limits;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.judgingservice.formal.FormalProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class PlatformLimitsTests {
    private static final long S = 1_000_000_000L;

    @Test
    void missingOrNonPositiveLimitsRefuseToStart() {
        assertThatThrownBy(() -> new LimitsProperties(null, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitsProperties(1L, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitsProperties(0L, 1L)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LimitsProperties(1L, 0L)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void theCpuLimitMustLeaveTheDerivedClockWithinTheJudgeBound() {
        var limits = new PlatformLimits(new LimitsProperties(12 * S, 1L << 30), formal(10));
        assertThat(limits.maxCpuNs()).isEqualTo(12 * S);

        assertThatThrownBy(() -> new PlatformLimits(new LimitsProperties(13 * S, 1L << 30), formal(10)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PlatformLimits(new LimitsProperties(Long.MAX_VALUE, 1L << 30), formal(10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static FormalProperties formal(long wallRatio) {
        return new FormalProperties(
                false,
                "requests",
                "lifecycle",
                "http://127.0.0.1",
                1,
                1,
                Duration.ofSeconds(30),
                Duration.ofMinutes(10),
                Duration.ofSeconds(30),
                wallRatio,
                Duration.ofSeconds(1));
    }
}
