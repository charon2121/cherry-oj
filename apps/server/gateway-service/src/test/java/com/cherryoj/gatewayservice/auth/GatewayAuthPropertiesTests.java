package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class GatewayAuthPropertiesTests {

    @Test
    void convertsTheSessionLifetimeAndRejectsOutOfRangeValues() {
        assertThat(properties(2_592_000).sessionAbsoluteTimeout()).isEqualTo(Duration.ofDays(30));
        assertThatThrownBy(() -> properties(86_399))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("[86400, 7776000]");
        assertThatCode(() -> new GatewayAuthProperties(
                        URI.create("http://user-service.internal:8081"),
                        Duration.ofSeconds(5),
                        2_592_000,
                        List.of("https://oj.example"),
                        10))
                .doesNotThrowAnyException();
    }

    @Test
    void productionProfileAlwaysMarksTheSessionCookieSecure() throws Exception {
        var sources = new YamlPropertySourceLoader().load("gateway", new ClassPathResource("application.yaml"));

        assertThat(sources).anySatisfy(source -> {
            assertThat(source.getProperty("spring.config.activate.on-profile")).isEqualTo("prod | production");
            assertThat(source.getProperty("server.reactive.session.cookie.secure"))
                    .isEqualTo(true);
        });
    }

    private static GatewayAuthProperties properties(long absolute) {
        return new GatewayAuthProperties(
                URI.create("http://localhost:8081"),
                Duration.ofSeconds(5),
                absolute,
                List.of("http://localhost:5173"),
                10);
    }
}
