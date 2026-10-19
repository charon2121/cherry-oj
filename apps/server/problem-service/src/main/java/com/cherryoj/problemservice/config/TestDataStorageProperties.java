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
        if (root == null
                || maxArchiveSize == null
                || maxExpandedSize == null
                || maxEntrySize == null
                || maxArchiveSize.toBytes() <= 0
                || maxExpandedSize.toBytes() <= 0
                || maxEntrySize.toBytes() <= 0
                || maxEntrySize.toBytes() > maxExpandedSize.toBytes()
                || maxFiles < 2
                || maxFiles > 2_000
                || !Double.isFinite(maxCompressionRatio)
                || maxCompressionRatio <= 1
                || staleAge == null
                || staleAge.isZero()
                || staleAge.isNegative()) {
            throw new IllegalArgumentException("Invalid test data storage configuration");
        }
    }

    /** 上限写进报错给人看：整 MiB/KiB 用对应单位，其余写字节数。 */
    public static String readable(DataSize size) {
        long bytes = size.toBytes();
        if (bytes % (1024 * 1024) == 0) return bytes / (1024 * 1024) + " MiB";
        if (bytes % 1024 == 0) return bytes / 1024 + " KiB";
        return bytes + " 字节";
    }
}
