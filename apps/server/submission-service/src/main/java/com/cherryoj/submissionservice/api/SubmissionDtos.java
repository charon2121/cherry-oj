package com.cherryoj.submissionservice.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class SubmissionDtos {
    private SubmissionDtos() {}
    public record Create(@NotNull UUID problemId, @NotNull UUID expectedProblemVersionId,
                         @NotBlank String languageId, @NotBlank @Size(max=262144) String source) {
        @Override public String toString() { return "Create[problemId=" + problemId + ", source=<redacted>]"; }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record View(String id, String problemId, String problemVersionId, int problemVersionNo,
                       String problemTitle, String languageId, String status, Instant createdAt,
                       String verdict, Long cpuNs, Long memoryBytes, Integer passedCount, Integer executedCount,
                       Integer totalCount, String message, Instant finishedAt) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Limits(long cpuNs, long memoryBytes, Long clockNs) {}
    public record Snapshot(String problemId, String problemVersionId, int problemVersionNo, String problemTitle,
                           String testDataVersionId, String testDataContentSha256, String languageId, String codeMode,
                           int totalCount) {}
    public record Profile(String problemVersionId, String testDataVersionId, String languageId,
                          String judgeEnvironmentId, String environmentFingerprint, String languageCalibrationId,
                          Limits effectiveLimits, long executionBudgetNs) {}
    public record Input(String submissionId, String contractVersion, String problemId, String problemVersionId,
                        String testDataVersionId, String languageId, String completeSource, String sourceSha256,
                        String judgeEnvironmentId, String environmentFingerprint, String languageCalibrationId,
                        Limits effectiveLimits, Instant createdAt, String testDataContentSha256, int totalCount, long executionBudgetNs) {
        @Override public String toString() { return "Input[submissionId=" + submissionId + ", source=<redacted>]"; }
    }
    public record HistoryPage(java.util.List<View> items, int page, int size, long totalElements, int totalPages) {}
    public record Source(String submissionId, String problemId, String problemVersionId,
                         String languageId, String source) {
        @Override public String toString() { return "Source[submissionId=" + submissionId + ", source=<redacted>]"; }
    }
    public record Created(View view, boolean fresh) {}
}
