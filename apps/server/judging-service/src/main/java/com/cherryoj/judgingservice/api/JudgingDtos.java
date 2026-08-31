package com.cherryoj.judgingservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class JudgingDtos {
    private JudgingDtos() {}

    public static final String UUID_PATTERN = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    public static final String SHA_PATTERN = "^[a-f0-9]{64}$";
    public static final String LANGUAGE_PATTERN = "^[a-z][a-z0-9-]{0,31}$";

    public record ManifestFile(
            @NotBlank @Size(max = 132) String name,
            @Min(0) long sizeBytes,
            @NotBlank @Pattern(regexp = SHA_PATTERN) String sha256) {}

    public record Manifest(
            @Min(1) int caseCount,
            @Min(0) long totalBytes,
            @NotNull @Size(min = 2, max = 2000) List<@Valid ManifestFile> files) {}

    public record DeploymentMetadata(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String testDataVersionId,
            @NotBlank @Pattern(regexp = SHA_PATTERN) String expectedSha256,
            @NotNull @Valid Manifest manifest) {}

    public record Deployment(
            String testDataVersionId, String environmentId, String environmentName,
            String expectedSha256, String status, String deployedSha256,
            LocalDateTime deployedAt, String errorMessage, LocalDateTime updatedAt, long rowVersion) {}

    public record CalibrationRequest(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String problemId,
            @NotBlank @Pattern(regexp = UUID_PATTERN) String problemVersionId,
            @NotBlank @Pattern(regexp = UUID_PATTERN) String testDataVersionId,
            @NotBlank @Pattern(regexp = SHA_PATTERN) String expectedSha256,
            @NotBlank @Pattern(regexp = LANGUAGE_PATTERN) String languageId,
            @Min(1) long cpuNs,
            @Min(1) long memoryBytes,
            @Min(1) Long clockNs,
            @NotBlank @Size(max = 1048576) String referenceSource) {}

    public record BenchmarkSummary(String sourceSha256, String verdict, Long maxCpuNs,
                                   Long maxMemoryBytes, Long maxClockNs) {}

    public record Calibration(
            String id, String problemVersionId, String languageId, String environmentId,
            String status, Long cpuNs, Long memoryBytes, Long clockNs,
            BenchmarkSummary benchmarkSummary, String errorMessage,
            LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}

    public record ReadinessCheck(String code, boolean passed, String message) {}

    public record ExecutionProfile(
            String environmentId, String environmentFingerprint, String endpointRef,
            String calibrationId, long cpuNs, long memoryBytes, Long clockNs) {}

    public record Readiness(boolean ready, String environmentId, List<ReadinessCheck> checks,
                            ExecutionProfile executionProfile) {}
}
