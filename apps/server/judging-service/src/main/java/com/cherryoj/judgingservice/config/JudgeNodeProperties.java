package com.cherryoj.judgingservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("cherry.judging.node")
public record JudgeNodeProperties(@DefaultValue("") String controlToken,
                                  @DefaultValue("35s") Duration leaseDuration,
                                  @DefaultValue("node-remote") String deploymentMode) {
    public JudgeNodeProperties {
        if (leaseDuration == null || leaseDuration.compareTo(Duration.ofSeconds(1)) < 0
                || leaseDuration.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("node lease duration must be between 1s and 5m");
        }
        if (!java.util.Set.of("legacy-local", "node-remote").contains(deploymentMode)) {
            throw new IllegalArgumentException("unknown node deployment mode");
        }
        if ("node-remote".equals(deploymentMode) && (controlToken == null || controlToken.isBlank())) {
            throw new IllegalArgumentException("node-remote requires a control token");
        }
    }
    public boolean remote() { return "node-remote".equals(deploymentMode); }
}
