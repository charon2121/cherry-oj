package com.cherryoj.gatewayservice.auth;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.gateway")
public record GatewayAuthProperties(
        URI userServiceBaseUrl,
        Duration userServiceTimeout,
        long sessionAbsoluteTimeoutSeconds,
        List<String> trustedOrigins,
        int loginRateLimitPerMinute) {

    public GatewayAuthProperties {
        validateIdentityServiceUri(userServiceBaseUrl);
        if (userServiceTimeout == null
                || userServiceTimeout.isNegative()
                || userServiceTimeout.isZero()
                || userServiceTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
            throw new IllegalArgumentException("cherry.gateway.user-service-timeout must be within (0, 30s]");
        }
        if (sessionAbsoluteTimeoutSeconds < 86_400 || sessionAbsoluteTimeoutSeconds > 7_776_000) {
            throw new IllegalArgumentException(
                    "cherry.gateway.session-absolute-timeout-seconds must be within [86400, 7776000]");
        }
        trustedOrigins = trustedOrigins == null ? List.of() : List.copyOf(trustedOrigins);
        if (trustedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank() || origin.contains("*"))) {
            throw new IllegalArgumentException("cherry.gateway.trusted-origins must contain exact origins");
        }
        if (loginRateLimitPerMinute < 1 || loginRateLimitPerMinute > 1_000) {
            throw new IllegalArgumentException("cherry.gateway.login-rate-limit-per-minute must be within [1, 1000]");
        }
    }

    private static void validateIdentityServiceUri(URI value) {
        if (value == null || !value.isAbsolute() || value.getHost() == null) {
            throw new IllegalArgumentException("cherry.gateway.user-service-base-url must be an absolute URI");
        }
        if (!"https".equalsIgnoreCase(value.getScheme()) && !"http".equalsIgnoreCase(value.getScheme())) {
            throw new IllegalArgumentException("cherry.gateway.user-service-base-url must use HTTP or HTTPS");
        }
    }

    Duration sessionAbsoluteTimeout() {
        return Duration.ofSeconds(sessionAbsoluteTimeoutSeconds);
    }
}
