package com.cherryoj.problemservice.integration.judging;

import java.time.LocalDateTime;
import java.util.List;

public final class JudgingDtos {
    private JudgingDtos() {}

    public record ManifestFile(String name, long sizeBytes, String sha256) {}
    public record Manifest(int caseCount, long totalBytes, List<ManifestFile> files) {}
    public record DeploymentMetadata(String testDataVersionId, String expectedSha256, Manifest manifest) {}
    public record Deployment(
            String testDataVersionId, String environmentId, String environmentName,
            String expectedSha256, String status, String deployedSha256,
            LocalDateTime deployedAt, String errorMessage, LocalDateTime updatedAt, long rowVersion) {}
    public record CalibrationRequest(
            String problemId, String problemVersionId, String testDataVersionId, String expectedSha256,
            String languageId, long cpuNs, long memoryBytes, Long clockNs, String referenceSource) {}
    public record BenchmarkSummary(
            String sourceSha256, String verdict, Long maxCpuNs, Long maxMemoryBytes, Long maxClockNs) {}
    public record Calibration(
            String id, String problemVersionId, String languageId, String environmentId,
            String status, Long cpuNs, Long memoryBytes, Long clockNs,
            BenchmarkSummary benchmarkSummary, String errorMessage,
            LocalDateTime createdAt, LocalDateTime updatedAt, long rowVersion) {}
    public record ReadinessCheck(String code, boolean passed, String message) {}
    public record ExecutionProfile(
            String environmentId, String environmentFingerprint, String endpointRef,
            String calibrationId, long cpuNs, long memoryBytes, Long clockNs) {}
    public record Readiness(
            boolean ready, String environmentId, List<ReadinessCheck> checks, ExecutionProfile executionProfile) {}
}
