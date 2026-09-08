package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class JavaServiceConfigurationDefaultsTests {

    private static final Pattern CHERRY_PLACEHOLDER =
            Pattern.compile("\\$\\{(CHERRY_[A-Z0-9_]+)(?::([^}]*))?}");
    private static final Set<String> JAVA_SERVICES = Set.of(
            "gateway-service",
            "user-service",
            "problem-service",
            "submission-service",
            "judging-service");
    private static final Set<String> RESOURCE_SERVICES = Set.of(
            "problem-service",
            "submission-service",
            "judging-service");
    private static final String LOCAL_JWKS_DEFAULT =
            "${CHERRY_IDENTITY_JWKS_URI:http://127.0.0.1:8081/.well-known/jwks.json}";

    @Test
    void sharedConfigurationIsCompleteWithoutEmbeddingPrivateCredentials() throws IOException {
        Path root = serverRoot();
        for (String service : JAVA_SERVICES) {
            Path resources = root.resolve(service + "/src/main/resources");
            String configuration = Files.readString(resources.resolve("application.yaml"));
            assertThat(configuration).contains("default: local").doesNotContain("spring.config.additional-location", "optional:file:");
            assertThat(resources.resolve("application-local.example.yaml")).isRegularFile();
            var matcher = CHERRY_PLACEHOLDER.matcher(configuration);
            while (matcher.find()) {
                String key = matcher.group(1), value = matcher.group(2);
                if (key.endsWith("_DB_PASSWORD")) {
                    assertThat(value).as(key + " must be externally supplied").isNull();
                } else if ((key.contains("TOKEN") && !key.contains("TTL")) || key.endsWith("_PASSWORD")) {
                    assertThat(value == null || value.isEmpty()).as(key + " cannot have a shared secret default").isTrue();
                }
            }
        }
    }

    @Test
    void everyResourceServiceUsesThePublishedLocalJwksEndpoint() throws IOException {
        Path serverRoot = serverRoot();

        for (String service : RESOURCE_SERVICES) {
            String configuration = Files.readString(
                    serverRoot.resolve(service + "/src/main/resources/application.yaml"));
            assertThat(configuration)
                    .as(service + " local JWKS configuration")
                    .contains(LOCAL_JWKS_DEFAULT)
                    .doesNotContain("/internal/.well-known/jwks.json");
        }
    }

    private static Path serverRoot() {
        Path candidate = Path.of("").toAbsolutePath().normalize();
        while (candidate != null) {
            if (Files.isDirectory(candidate.resolve("gateway-service/src/main/resources"))) {
                return candidate;
            }
            Path nested = candidate.resolve("apps/server");
            if (Files.isDirectory(nested.resolve("gateway-service/src/main/resources"))) {
                return nested;
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Cannot locate the apps/server reactor root");
    }
}
