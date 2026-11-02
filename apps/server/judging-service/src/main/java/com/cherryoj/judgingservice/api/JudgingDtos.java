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
    public static final String KIND_PATTERN = "^(BASE|OVERRIDE)$";

    /**
     * testDataLocation 与 testDataDigest 是 problem-service 此刻给出的；判题结果里的指纹必须与之一致。
     * CORE：referenceSource 是驱动文件，referenceSolutionSource 是前缀 + 参考核心代码，两个 CORE 字段同时出现或同时缺席。
     * kind 区分基础标定与覆盖标定（docs/protocols/limits.md）。契约见 contracts/calibration.schema.json。
     */
    public record CalibrationRequest(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String problemId,
            @NotBlank @Pattern(regexp = LANGUAGE_PATTERN) String languageId,
            @NotBlank @Pattern(regexp = KIND_PATTERN) String kind,
            @NotBlank @Size(max = 2048) String testDataLocation,
            @NotBlank @Pattern(regexp = SHA_PATTERN) String testDataDigest,
            @Min(1) long cpuNs,
            @Min(1) long memoryBytes,
            @Min(1) Long clockNs,
            @NotBlank @Size(max = 1048576) String referenceSource,
            @Size(min = 1, max = 327680) String referenceSolutionSource,
            @Min(0) Integer solutionPrefixLineCount) {}

    public record BenchmarkSummary(
            String sourceSha256, String verdict, Long maxCpuNs, Long maxMemoryBytes, Long maxClockNs) {}

    public record Calibration(
            String id,
            String problemId,
            String languageId,
            String kind,
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

    /**
     * 该语言解析出的限制（docs/protocols/limits.md）：limitSource 为 DERIVED 时 limitDerivation 必填，否则为 null；
     * calibrationId 与 calibratedAt 是所依据的那条标定（DERIVED 时是基础标定），problem-service 据此提示重新标定。
     */
    public record ExecutionProfile(
            String calibrationId,
            long cpuNs,
            long memoryBytes,
            Long clockNs,
            String limitSource,
            LimitDerivation limitDerivation,
            java.time.LocalDateTime calibratedAt) {}

    /** 推导时所用的语言限制参数原值，契约见 contracts/execution-profile.schema.json 的 LimitModel。 */
    public record LimitModel(
            long cpuFactorPermille,
            Long cpuFactorCapPermille,
            long cpuOverheadNs,
            long ioPicosPerByte,
            long memoryFactorPermille,
            long memoryOverheadBytes) {}

    /** DERIVED 的推导依据，随提交冻结；契约见 contracts/execution-profile.schema.json 的 LimitDerivation。 */
    public record LimitDerivation(
            String baseLanguageId,
            String baseCalibrationId,
            long baseCpuNs,
            long baseMemoryBytes,
            long maxTestcaseIoBytes,
            long refCpuNs,
            long refMemoryBytes,
            long platformMaxCpuNs,
            long platformMaxMemoryBytes,
            boolean cpuCapped,
            boolean memoryCapped,
            LimitModel baseLimitModel,
            LimitModel languageLimitModel) {}

    public record Readiness(boolean ready, List<ReadinessCheck> checks, ExecutionProfile executionProfile) {}
}
