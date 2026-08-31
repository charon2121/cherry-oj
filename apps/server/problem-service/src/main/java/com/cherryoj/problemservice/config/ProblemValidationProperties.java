package com.cherryoj.problemservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.problem.validation")
public record ProblemValidationProperties(Duration staleAge, boolean recoveryEnabled) {
    public ProblemValidationProperties {
        if (staleAge == null || staleAge.isZero() || staleAge.isNegative()) {
            throw new IllegalArgumentException("validation stale-age must be positive");
        }
    }
}
