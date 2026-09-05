package com.cherryoj.userservice.security;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("identitySigningKey")
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public final class SigningKeyHealthIndicator implements HealthIndicator {

    private final SigningKeys keys;

    public SigningKeyHealthIndicator(SigningKeys keys) {
        this.keys = keys;
    }

    @Override
    public Health health() {
        List<String> publishedKids = keys.publicJwkSet().getKeys().stream()
                .map(key -> key.getKeyID())
                .sorted()
                .toList();
        return Health.up()
                .withDetail("activeKid", keys.activeKid())
                .withDetail("publishedKeyCount", publishedKids.size())
                .withDetail("publishedKids", publishedKids)
                .build();
    }
}
