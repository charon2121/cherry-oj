package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthPropertiesTests {
    @Test
    void convertsTheAbsoluteLifetime() {
        assertThat(new AuthProperties("server", 2_592_000).sessionAbsoluteTimeout())
                .isEqualTo(Duration.ofDays(30));
    }

    @Test
    void rejectsAnInvalidLifetime() {
        assertThatThrownBy(() -> new AuthProperties("server", 86_399))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[86400, 7776000]");
    }
}
