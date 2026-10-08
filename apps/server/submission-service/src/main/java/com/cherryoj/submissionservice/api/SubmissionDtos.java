package com.cherryoj.submissionservice.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.UUID;

public final class SubmissionDtos {
    private SubmissionDtos() {}
    public record Create(@NotNull UUID problemId,
                         @NotBlank String languageId, @NotBlank @Size(max=262144) String source) {
        @Override public String toString() { return "Create[problemId=" + problemId + ", source=<redacted>]"; }
    }
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record View(String id, String problemId, String problemTitle, String languageId, String status, Instant createdAt,
                       String verdict, Long cpuNs, Long memoryBytes, Integer passedTestcaseCount, Integer executedTestcaseCount,
                       Integer testcaseCount, String message, Instant finishedAt) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Limits(long cpuNs, long memoryBytes, Long clockNs) {}
    /** 契约 problem-judge-snapshot：题目没有版本，也不含测试数据地址。 */
    public record Snapshot(String problemId, String problemTitle, String languageId, String codeMode) {}
    public record Profile(String problemId, String languageId, String languageCalibrationId,
                          Limits effectiveLimits, long executionBudgetNs) {}
    /** 契约 judge-input：只冻结源码、语言、标定与限制；测试数据地址由 judging-service 判题时取。 */
    public record Input(String submissionId, String contractVersion, String problemId, String languageId,
                        String completeSource, String sourceSha256, String languageCalibrationId,
                        Limits effectiveLimits, Instant createdAt) {
        @Override public String toString() { return "Input[submissionId=" + submissionId + ", source=<redacted>]"; }
    }
    public record HistoryPage(java.util.List<View> items, int page, int size, long totalElements, int totalPages) {}
    public record Source(String submissionId, String problemId, String languageId, String source) {
        @Override public String toString() { return "Source[submissionId=" + submissionId + ", source=<redacted>]"; }
    }
    public record Created(View view, boolean fresh) {}
}
