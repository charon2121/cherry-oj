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
        Duration sessionIdleTimeout,
        Duration sessionAbsoluteTimeout) {

    public AuthProperties {
        mode = mode == null ? "server" : mode;
        previousPublicKeys = previousPublicKeys == null ? Map.of() : Map.copyOf(previousPublicKeys);
    }
}
