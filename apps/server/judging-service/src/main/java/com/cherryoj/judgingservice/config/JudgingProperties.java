package com.cherryoj.judgingservice.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.judging")
public record JudgingProperties(
        Path testdataRoot,
        long maxArchiveBytes,
        long maxExpandedBytes,
        long maxEntryBytes,
        int maxFiles,
        double maxCompressionRatio,
        Duration judgeTimeout,
        Duration staleAge,
        boolean recoveryEnabled,
        Provision provision) {

    public record Provision(
            boolean enabled,
            boolean allowExistingIdentical,
            String id,
            String name,
            String fingerprint,
            String architecture,
            String cpuModel,
            String osVersion,
            String kernelVersion,
            String judgeVersion,
            String sandboxVersion,
            String configDigest,
            String endpointRef,
            String languageId,
            String toolchainVersion,
            String languageConfigDigest) {
    }
}
