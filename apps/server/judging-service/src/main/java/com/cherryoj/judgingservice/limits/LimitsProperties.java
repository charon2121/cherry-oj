package com.cherryoj.judgingservice.limits;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 平台对每个测试点的硬上限（docs/protocols/limits.md 第 4 节）：推导值超过就取上限，手填的标定超过就拒绝。
 * 必填、没有默认值；用包装类型接收，没配置和配成 0 都在入口拒绝，免得悄悄放开或悄悄卡死。
 */
@ConfigurationProperties("cherry.judging.limits")
public record LimitsProperties(Long maxCpuNs, Long maxMemoryBytes) {
    public LimitsProperties {
        if (maxCpuNs == null || maxCpuNs < 1 || maxMemoryBytes == null || maxMemoryBytes < 1) {
            throw new IllegalArgumentException(
                    "cherry.judging.limits.max-cpu-ns and max-memory-bytes are required and must be positive");
        }
    }
}
