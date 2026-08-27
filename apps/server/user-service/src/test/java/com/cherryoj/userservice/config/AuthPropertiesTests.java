package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthPropertiesTests {

    @Test
    void convertsValidatedSecondsAndStrictBoolean() {
        AuthProperties properties = properties(7_200, 86_400, "false");

        assertThat(properties.sessionIdleTimeout()).isEqualTo(Duration.ofSeconds(7_200));
        assertThat(properties.sessionAbsoluteTimeout()).isEqualTo(Duration.ofSeconds(86_400));
        assertThat(properties.refreshIdleOnActivity()).isFalse();
    }

    @Test
    void rejectsNonBooleanAndInvalidDeadlineOrder() {
        assertThatThrownBy(() -> properties(1_800, 43_200, "1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("session-refresh-idle-on-activity");
        assertThatThrownBy(() -> properties(7_200, 3_600, "true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not exceed");
        assertThatThrownBy(() -> properties(7_201, 43_200, "true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[300, 7200]");
        assertThatThrownBy(() -> properties(1_800, 3_599, "true"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[3600, 604800]");
    }

    private static AuthProperties properties(long idle, long absolute, String refresh) {
        return new AuthProperties(
                "server",
                "cherry-oj-user-service",
                "cherry-oj-internal",
                "test-key",
                "unused",
                "unused",
                Map.of(),
                Duration.ofSeconds(120),
                Duration.ofSeconds(30),
                idle,
                absolute,
                refresh);
    }
}
