package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.limits.ResolvedLanguageLimits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class JudgingDtos {
    public record ProblemReadinessRequest(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String problemId,
            @NotBlank @Pattern(regexp = SHA_PATTERN) String testDataDigest,
            @jakarta.validation.constraints.NotNull @Min(0) Long maxTestcaseIoBytes,

            @jakarta.validation.constraints.NotNull @Size(min = 1, max = 32)
            List<@NotBlank @Pattern(regexp = LANGUAGE_PATTERN) String> languageIds) {}

    public record ProblemLanguageReadiness(String languageId, boolean ready, List<ReadinessCheck> checks) {}

    public record ProblemReadiness(List<ProblemLanguageReadiness> languages) {}

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

    public record Readiness(boolean ready, List<ReadinessCheck> checks, ResolvedLanguageLimits executionProfile) {}

    /**
     * 一门语言在这道题上的限制及其来源，管理端标定页用（docs/protocols/limits.md）。解析不出限制时来源与各限制为 null，
     * unavailableReason 给出原因：NO_CALIBRATION、NO_LIMIT_MODEL 或 BASE_LIMIT_MODEL_REMOVED。
     */
    public record LanguageLimits(
            String languageId,
            String limitSource,
            Long cpuNs,
            Long memoryBytes,
            Long clockNs,
            String calibrationId,
            boolean cpuCapped,
            boolean memoryCapped,
            String unavailableReason) {}

    public record LanguageLimitsList(List<LanguageLimits> languages) {}
}
