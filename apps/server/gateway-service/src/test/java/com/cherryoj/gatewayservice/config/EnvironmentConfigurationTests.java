package com.cherryoj.gatewayservice.config;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercises Spring's real Config Data loader, without creating services or contacting databases. */
class EnvironmentConfigurationTests {
    @Test
    void nonLocalEnvironmentsNeverLoadPrivateConfigurationAndEnvironmentOverridesApply() {
        for (String profile : new String[] {"test", "dev"}) {
            var environment = new StandardEnvironment();
            environment.getPropertySources().remove("systemProperties");
            environment.getPropertySources().remove("systemEnvironment");
            environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource("container", Map.of(
                    "SPRING_PROFILES_ACTIVE", profile, "SERVER_PORT", "19090")));
            ConfigDataEnvironmentPostProcessor.applyTo(environment);
            assertThat(environment.getActiveProfiles()).containsExactly(profile);
            assertThat(environment.getProperty("server.port")).isEqualTo("19090");
            assertThat(environment.getPropertySources()).noneMatch(source ->
                    source.getName().contains("application-local."));
        }
    }
}
