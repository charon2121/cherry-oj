package com.cherryoj.judgingservice.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class JudgingDtos {
    private JudgingDtos() {}

    public static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    public static final String SHA_PATTERN = "^[a-f0-9]{64}$";
    public static final String LANGUAGE_PATTERN = "^[a-z][a-z0-9-]{0,31}$";

    /** testDataLocation 与 testDataDigest 是 problem-service 此刻给出的；判题结果里的指纹必须与之一致。 */
    public record CalibrationRequest(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String problemId,
            @NotBlank @Pattern(regexp = LANGUAGE_PATTERN) String languageId,
            @NotBlank @Size(max = 2048) String testDataLocation,
            @NotBlank @Pattern(regexp = SHA_PATTERN) String testDataDigest,
            @Min(1) long cpuNs,
            @Min(1) long memoryBytes,
            @Min(1) Long clockNs,
            @NotBlank @Size(max = 1048576) String referenceSource) {}

    public record BenchmarkSummary(String sourceSha256, String verdict, Long maxCpuNs,
                                   Long maxMemoryBytes, Long maxClockNs) {}

    public record Calibration(
            String id, String problemId, String languageId, String status, Long cpuNs, Long memoryBytes, Long clockNs,
            String testDataDigest, BenchmarkSummary benchmarkSummary, String errorMessage,
            LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}

    public record ReadinessCheck(String code, boolean passed, String message) {}

    /** 判题不再绑定某个节点：任何在线、声明了该语言的节点都能读到同一份测试数据。 */
    public record ExecutionProfile(String calibrationId, long cpuNs, long memoryBytes, Long clockNs) {}

    public record Readiness(boolean ready, List<ReadinessCheck> checks, ExecutionProfile executionProfile) {}
}
