package com.cherryoj.judgingservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.judging")
public record JudgingProperties(Duration judgeTimeout) {
}
