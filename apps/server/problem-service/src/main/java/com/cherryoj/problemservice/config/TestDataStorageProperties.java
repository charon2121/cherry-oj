package com.cherryoj.problemservice.config;

import java.nio.file.Path;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("cherry.problem.test-data")
public record TestDataStorageProperties(
        Path root,
        DataSize maxArchiveSize,
        DataSize maxExpandedSize,
        DataSize maxEntrySize,
        int maxFiles,
        double maxCompressionRatio,
        Duration staleAge) {

    public TestDataStorageProperties {
        if (root == null || maxArchiveSize == null || maxExpandedSize == null || maxEntrySize == null
                || maxArchiveSize.toBytes() <= 0 || maxExpandedSize.toBytes() <= 0
                || maxEntrySize.toBytes() <= 0 || maxEntrySize.toBytes() > maxExpandedSize.toBytes()
                || maxFiles < 2 || maxFiles > 2_000
                || !Double.isFinite(maxCompressionRatio) || maxCompressionRatio <= 1
                || staleAge == null || staleAge.isZero() || staleAge.isNegative()) {
            throw new IllegalArgumentException("Invalid test data storage configuration");
        }
    }
}
