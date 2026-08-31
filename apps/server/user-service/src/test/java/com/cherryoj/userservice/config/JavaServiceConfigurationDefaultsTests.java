package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class JavaServiceConfigurationDefaultsTests {

    private static final Pattern CHERRY_PLACEHOLDER =
            Pattern.compile("\\$\\{(CHERRY_[A-Z0-9_]+)(?::([^}]*))?}");
    private static final Pattern PRODUCTION_PROFILE =
            Pattern.compile("(?m)^\\s*on-profile:\\s*[\"']?(?:prod\\s*\\|\\s*production|production\\s*\\|\\s*prod)[\"']?\\s*$");
    private static final Set<String> OPTIONAL_EMPTY_DEFAULTS = Set.of("CHERRY_REDIS_PASSWORD");
    private static final Set<String> PRODUCTION_REQUIRED = Set.of(
            "user-service/application.yaml:CHERRY_USER_DB_PASSWORD",
            "user-service/application.yaml:CHERRY_AUTH_KEY_ID",
            "user-service/application.yaml:CHERRY_AUTH_PRIVATE_KEY_LOCATION",
            "user-service/application.yaml:CHERRY_AUTH_PUBLIC_KEY_LOCATION",
            "problem-service/application.yaml:CHERRY_PROBLEM_DB_PASSWORD",
            "judging-service/application.yaml:CHERRY_JUDGING_DB_PASSWORD");
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
    void everyJavaServiceEnvironmentPlaceholderHasAnIntentionalDefaultOrProductionRequirement() throws IOException {
        Path serverRoot = serverRoot();
        List<Path> configurations;
        try (Stream<Path> paths = Files.walk(serverRoot, 5)) {
            configurations = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().contains("src/main/resources"))
                    .filter(path -> path.getFileName().toString().matches("application.*\\.ya?ml"))
                    .sorted()
                    .toList();
        }

        Set<String> services = new HashSet<>();
        Set<String> productionRequired = new HashSet<>();
        Set<String> localDefaults = new HashSet<>();
        Set<String> invalid = new HashSet<>();

        for (Path configuration : configurations) {
            String service = serverRoot.relativize(configuration).getName(0).toString();
            services.add(service);
            String file = service + "/" + configuration.getFileName();
            for (String document : Files.readString(configuration).split("(?m)^---\\s*$")) {
                boolean production = PRODUCTION_PROFILE.matcher(document).find();
                var matcher = CHERRY_PLACEHOLDER.matcher(document);
                while (matcher.find()) {
                    String variable = matcher.group(1);
                    String defaultValue = matcher.group(2);
                    String key = file + ":" + variable;
                    if (defaultValue == null) {
                        if (production && PRODUCTION_REQUIRED.contains(key)) {
                            productionRequired.add(key);
                        } else {
                            invalid.add(key + " has no default outside a production-only document");
                        }
                    } else if (defaultValue.isEmpty()) {
                        if (!OPTIONAL_EMPTY_DEFAULTS.contains(variable)) {
                            invalid.add(key + " has an unclassified empty default");
                        }
                    } else if (!production) {
                        localDefaults.add(key);
                    }
                }
            }
        }

        assertThat(services).containsExactlyInAnyOrderElementsOf(JAVA_SERVICES);
        assertThat(invalid).isEmpty();
        assertThat(productionRequired).containsExactlyInAnyOrderElementsOf(PRODUCTION_REQUIRED);
        assertThat(localDefaults).containsAll(PRODUCTION_REQUIRED);
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
