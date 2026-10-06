package com.cherryoj.problemservice.integration.judging;

import java.time.LocalDateTime;
import java.util.List;

public final class JudgingDtos {
    private JudgingDtos() {}

    /**
     * 标定请求带着题目当前的测试数据地址与指纹：judging-service 用这个地址跑参考解，
     * 并要求判题结果里的数据指纹与 testDataDigest 一致，否则说明标定中途数据被替换，标定作废。
     */
    public record CalibrationRequest(
            String problemId, String languageId, String testDataLocation, String testDataDigest,
            long cpuNs, long memoryBytes, Long clockNs, String referenceSource) {}
    public record BenchmarkSummary(
            String sourceSha256, String verdict, Long maxCpuNs, Long maxMemoryBytes, Long maxClockNs) {}
    public record Calibration(
            String id, String problemId, String languageId, String status, Long cpuNs, Long memoryBytes, Long clockNs,
            String testDataDigest, BenchmarkSummary benchmarkSummary, String errorMessage,
            LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}
    public record ReadinessCheck(String code, boolean passed, String message) {}
    public record ExecutionProfile(String calibrationId, long cpuNs, long memoryBytes, Long clockNs) {}
    public record Readiness(boolean ready, List<ReadinessCheck> checks, ExecutionProfile executionProfile) {}
}
