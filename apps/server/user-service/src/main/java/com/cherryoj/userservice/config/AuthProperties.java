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
        String sessionLifetimePolicy,
        long sessionAbsoluteTimeoutSeconds) {

    public AuthProperties {
        mode = mode == null ? "server" : mode;
        previousPublicKeys = previousPublicKeys == null ? Map.of() : Map.copyOf(previousPublicKeys);
        if (!"fixed-absolute".equals(sessionLifetimePolicy)) {
            throw new IllegalArgumentException(
                    "cherry.auth.session-lifetime-policy must be fixed-absolute");
        }
        if (sessionAbsoluteTimeoutSeconds < 86_400 || sessionAbsoluteTimeoutSeconds > 7_776_000) {
            throw new IllegalArgumentException(
                    "cherry.auth.session-absolute-timeout-seconds must be within [86400, 7776000]");
        }
    }

    public Duration sessionAbsoluteTimeout() {
        return Duration.ofSeconds(sessionAbsoluteTimeoutSeconds);
    }
}
