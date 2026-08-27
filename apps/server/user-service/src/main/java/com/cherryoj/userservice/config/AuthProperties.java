package com.cherryoj.userservice.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.auth")
public record AuthProperties(
        String mode,
        String issuer,
        String audience,
        String keyId,
        String privateKeyLocation,
        String publicKeyLocation,
        Map<String, String> previousPublicKeys,
        Duration accessTokenTtl,
        Duration clockSkew,
        long sessionIdleTimeoutSeconds,
        long sessionAbsoluteTimeoutSeconds,
        String sessionRefreshIdleOnActivity) {

    public AuthProperties {
        mode = mode == null ? "server" : mode;
        previousPublicKeys = previousPublicKeys == null ? Map.of() : Map.copyOf(previousPublicKeys);
        if (sessionIdleTimeoutSeconds < 300 || sessionIdleTimeoutSeconds > 7_200) {
            throw new IllegalArgumentException(
                    "cherry.auth.session-idle-timeout-seconds must be within [300, 7200]");
        }
        if (sessionAbsoluteTimeoutSeconds < 3_600 || sessionAbsoluteTimeoutSeconds > 604_800) {
            throw new IllegalArgumentException(
                    "cherry.auth.session-absolute-timeout-seconds must be within [3600, 604800]");
        }
        if (sessionIdleTimeoutSeconds > sessionAbsoluteTimeoutSeconds) {
            throw new IllegalArgumentException(
                    "cherry.auth.session-idle-timeout-seconds must not exceed session-absolute-timeout-seconds");
        }
        if (!"true".equals(sessionRefreshIdleOnActivity)
                && !"false".equals(sessionRefreshIdleOnActivity)) {
            throw new IllegalArgumentException(
                    "cherry.auth.session-refresh-idle-on-activity must be true or false");
        }
    }

    public Duration sessionIdleTimeout() {
        return Duration.ofSeconds(sessionIdleTimeoutSeconds);
    }

    public Duration sessionAbsoluteTimeout() {
        return Duration.ofSeconds(sessionAbsoluteTimeoutSeconds);
    }

    public boolean refreshIdleOnActivity() {
        return Boolean.parseBoolean(sessionRefreshIdleOnActivity);
    }
}
