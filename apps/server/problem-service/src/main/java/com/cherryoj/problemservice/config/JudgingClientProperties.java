package com.cherryoj.problemservice.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.problem.judging")
public record JudgingClientProperties(URI baseUrl, Duration connectTimeout, Duration requestTimeout) {

    public JudgingClientProperties {
        if (baseUrl == null || !baseUrl.isAbsolute()) throw new IllegalArgumentException("judging base-url must be absolute");
        if (connectTimeout == null || connectTimeout.isZero() || connectTimeout.isNegative()) {
            throw new IllegalArgumentException("judging connect-timeout must be positive");
        }
        if (requestTimeout == null || requestTimeout.isZero() || requestTimeout.isNegative()) {
            throw new IllegalArgumentException("judging request-timeout must be positive");
        }
    }
}
