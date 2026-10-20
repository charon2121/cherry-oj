package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class JavaServiceConfigurationDefaultsTests {

    private static final Pattern CHERRY_PLACEHOLDER = Pattern.compile("\\$\\{(CHERRY_[A-Z0-9_]+)(?::([^}]*))?}");
    private static final Set<String> JAVA_SERVICES =
            Set.of("gateway-service", "user-service", "problem-service", "submission-service", "judging-service");
    private static final String SERVICE_IDENTITY = """
              service-identity:
                name: "${spring.application.name}"
                private-key-location: "${CHERRY_SERVICE_PRIVATE_KEY_LOCATION:file:${CHERRY_SERVICE_IDENTITY_DIR:./service-identity}/${spring.application.name}-private.pem}"
                public-keys-dir: "${CHERRY_SERVICE_PUBLIC_KEYS_DIR:${CHERRY_SERVICE_IDENTITY_DIR:./service-identity}}"
            """;

    @Test
    void sharedConfigurationIsCompleteWithoutEmbeddingPrivateCredentials() throws IOException {
        Path root = serverRoot();
        for (String service : JAVA_SERVICES) {
            Path resources = root.resolve(service + "/src/main/resources");
            String configuration = Files.readString(resources.resolve("application.yaml"));
            assertThat(configuration)
                    .contains("default: local")
                    .doesNotContain("spring.config.additional-location", "optional:file:");
            assertThat(resources.resolve("application-local.example.yaml")).isRegularFile();
            var matcher = CHERRY_PLACEHOLDER.matcher(configuration);
            while (matcher.find()) {
                String key = matcher.group(1), value = matcher.group(2);
                if (key.endsWith("_DB_PASSWORD")) {
                    assertThat(value).as(key + " must be externally supplied").isNull();
                } else if (key.endsWith("_PRIVATE_KEY_LOCATION")) {
                    // 服务私钥只能来自部署环境或本地生成目录，不能默认指向打包进 jar 的资源。
                    assertThat(value == null || !value.startsWith("classpath:"))
                            .as(key + " cannot default to a bundled key")
                            .isTrue();
                } else if ((key.contains("TOKEN") && !key.contains("TTL") && !key.endsWith("_URI"))
                        || key.endsWith("_PASSWORD")) {
                    assertThat(value == null || value.isEmpty())
                            .as(key + " cannot have a shared secret default")
                            .isTrue();
                }
            }
        }
    }

    /** 每个服务都用自己的私钥签发调用令牌，用同一个公钥目录校验（docs/protocols/service-identity.md §2）。 */
    @Test
    void everyServiceSignsWithItsOwnKeyAndVerifiesWithThePublicKeyDirectory() throws IOException {
        Path serverRoot = serverRoot();
        for (String service : JAVA_SERVICES) {
            String configuration =
                    Files.readString(serverRoot.resolve(service + "/src/main/resources/application.yaml"));
            assertThat(configuration)
                    .as(service + " service identity")
                    .contains(SERVICE_IDENTITY)
                    .doesNotContain("jwks", "token-uri", "service-tokens", "CHERRY_IDENTITY_");
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
