package com.cherryoj.problemservice.integration.judging;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.List;

public final class JudgingDtos {
    private JudgingDtos() {}

    /**
     * 标定请求带着题目当前的测试数据地址与指纹：judging-service 用这个地址跑参考解，
     * 并要求判题结果里的数据指纹与 testDataDigest 一致，否则说明标定中途数据被替换，标定作废。
     * CORE：referenceSource 是驱动文件，referenceSolutionSource 是前缀 + 参考核心代码；ACM 两者都不带。
     * 契约见 contracts/calibration.schema.json。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CalibrationRequest(
            String problemId,
            String languageId,
            String testDataLocation,
            String testDataDigest,
            long cpuNs,
            long memoryBytes,
            Long clockNs,
            String referenceSource,
            String referenceSolutionSource,
            Integer solutionPrefixLineCount) {}

    public record BenchmarkSummary(
            String sourceSha256, String verdict, Long maxCpuNs, Long maxMemoryBytes, Long maxClockNs) {}

    public record Calibration(
            String id,
            String problemId,
            String languageId,
            String status,
            Long cpuNs,
            Long memoryBytes,
            Long clockNs,
            String testDataDigest,
            BenchmarkSummary benchmarkSummary,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion) {}

    public record ReadinessCheck(String code, boolean passed, String message) {}
    /** calibratedAt 是这条标定的批准时间（UTC）；驱动代码或前缀晚于它修改时提示重新标定。 */
    public record ExecutionProfile(
            String calibrationId, long cpuNs, long memoryBytes, Long clockNs, LocalDateTime calibratedAt) {}

    public record Readiness(boolean ready, List<ReadinessCheck> checks, ExecutionProfile executionProfile) {}
}
