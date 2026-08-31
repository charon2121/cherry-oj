package com.cherryoj.problemservice.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class AdminProblemDtos {

    private AdminProblemDtos() {
    }

    public record CreateProblemRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 128) String slug,
            @NotBlank @Size(max = 512) String title,
            @NotNull Difficulty difficulty,
            @NotNull CodeMode codeMode,
            @NotBlank String languageId) {
    }

    public record UpdateProblemRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 128) String slug,
            @NotNull Visibility visibility,
            @Min(0) long rowVersion) {
    }

    public record RowVersionRequest(@Min(0) long rowVersion) {
    }

    public record DeployTestDataRequest(
            @NotBlank @Pattern(regexp = UUID_PATTERN) String testDataVersionId,
            @NotBlank @Pattern(regexp = SHA_PATTERN) String expectedSha256,
            @Min(0) long rowVersion) {
    }

    public record CalibrateProblemRequest(
            @NotBlank @Pattern(regexp = "^cpp$") String languageId,
            @Min(1) long cpuNs,
            @Min(1) long memoryBytes,
            @Min(1) Long clockNs,
            @NotBlank @Size(max = 1_048_576) String referenceSource,
            @Min(0) long rowVersion) {
    }

    public record PublishProblemRequest(@Min(0) long rowVersion) {
    }

    public record CreateRevisionRequest(@Min(0) long rowVersion, boolean reuseTestData) {
    }

    public record SampleInput(
            @Min(1) @Max(100) int ordinal,
            @NotNull @Size(max = 1_048_576) String input,
            @NotNull @Size(max = 1_048_576) String output,
            @Size(max = 1_048_576) String explanationMarkdown) {
    }

    public record UpdateVersionRequest(
            @NotBlank @Size(max = 512) String title,
            @NotNull @Size(max = 16_777_215) String statementMarkdown,
            @NotNull @Size(max = 16_777_215) String inputDescriptionMarkdown,
            @NotNull @Size(max = 16_777_215) String outputDescriptionMarkdown,
            @Size(max = 16_777_215) String constraintsMarkdown,
            @Size(max = 16_777_215) String hintMarkdown,
            @NotNull Difficulty difficulty,
            @NotNull @Size(max = 20) List<@NotBlank @Size(max = 32) String> tags,
            @NotNull @Size(max = 100) List<@Valid SampleInput> samples,
            @NotNull @Size(max = 1_048_576) String starterCode,
            @Size(max = 8192) String changeSummary,
            @Min(0) long rowVersion) {
    }

    public record VersionSummary(
            String id,
            int versionNo,
            VersionStatus status,
            String title,
            LocalDateTime updatedAt,
            LocalDateTime publishedAt,
            long rowVersion) {
    }

    public record Problem(
            String id,
            String slug,
            Visibility visibility,
            ProblemStatus status,
            String currentPublishedVersionId,
            List<VersionSummary> versions,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion) {
    }

    public record ProblemPage(List<Problem> items, int page, int size, long totalElements, int totalPages) {
    }

    public record Language(String id, String displayName, String starterCode) {
    }

    public record TestDataDeployment(
            String testDataVersionId,
            String environmentId,
            String environmentName,
            String expectedSha256,
            DeploymentStatus status,
            String deployedSha256,
            LocalDateTime deployedAt,
            String errorMessage,
            LocalDateTime updatedAt,
            long rowVersion) {
    }

    public record BenchmarkSummary(
            String sourceSha256,
            JudgeVerdict verdict,
            Long maxCpuNs,
            Long maxMemoryBytes,
            Long maxClockNs) {
    }

    public record LanguageCalibration(
            String id,
            String problemVersionId,
            String languageId,
            String environmentId,
            CalibrationStatus status,
            Long cpuNs,
            Long memoryBytes,
            Long clockNs,
            BenchmarkSummary benchmarkSummary,
            String errorMessage,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            long rowVersion) {
    }

    public record PublishCheckItem(PublishCheckCode code, boolean passed, String message) {
    }

    public record PublishCheck(boolean ready, String environmentId, List<PublishCheckItem> checks) {
    }

    public record Version(
            String id,
            String problemId,
            int versionNo,
            VersionStatus status,
            CodeMode codeMode,
            String title,
            String statementMarkdown,
            String inputDescriptionMarkdown,
            String outputDescriptionMarkdown,
            String constraintsMarkdown,
            String hintMarkdown,
            Difficulty difficulty,
            List<String> tags,
            List<PublicProblemDtos.ProblemSample> samples,
            List<Language> allowedLanguages,
            Object testDataVersion,
            String changeSummary,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            LocalDateTime publishedAt,
            long rowVersion) {
    }

    public enum Visibility { PRIVATE, PUBLIC }

    public enum ProblemStatus { ACTIVE, ARCHIVED }

    public enum VersionStatus { DRAFT, VALIDATING, READY_FOR_REVIEW, PUBLISHED, ARCHIVED }

    public enum CodeMode { ACM, CORE }

    public enum Difficulty { UNRATED, EASY, MEDIUM, HARD }

    public enum DeploymentStatus { DEPLOYING, READY, FAILED }

    public enum CalibrationStatus { VALIDATING, VALID, FAILED, SUPERSEDED }

    public enum JudgeVerdict { AC, WA, TLE, MLE, RE, CE, SE }

    public enum PublishCheckCode { CONTENT, SAMPLES, LANGUAGE, TEST_DATA, DEPLOYMENT, CALIBRATION }

    public static final String UUID_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
    public static final String SHA_PATTERN = "^[a-f0-9]{64}$";
}
