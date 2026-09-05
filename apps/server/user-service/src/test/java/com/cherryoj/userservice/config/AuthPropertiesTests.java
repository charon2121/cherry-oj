package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuthPropertiesTests {

    @Test
    void convertsFixedAbsoluteLifetime() {
        AuthProperties properties = properties("fixed-absolute", 2_592_000);
        assertThat(properties.sessionAbsoluteTimeout()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void rejectsUnknownPolicyAndInvalidLifetime() {
        assertThatThrownBy(() -> properties("sliding-idle", 2_592_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("fixed-absolute");
        assertThatThrownBy(() -> properties("fixed-absolute", 86_399))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[86400, 7776000]");
    }

    private static AuthProperties properties(String policy, long absolute) {
        return new AuthProperties(
                "server",
                "cherry-oj-user-service",
                "cherry-oj-internal",
                "test-key",
                "unused",
                "unused",
                Map.of(),
                Duration.ofSeconds(120),
                Duration.ofSeconds(30), policy, absolute);
    }
}
