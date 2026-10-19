package com.cherryoj.submissionservice.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SubmissionDtos {
    private SubmissionDtos() {}

    public record Create(
            @NotNull UUID problemId,
            @NotBlank String languageId,
            @NotBlank @Size(max = 262144) String source) {
        @Override
        public String toString() {
            return "Create[problemId=" + problemId + ", source=<redacted>]";
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record View(
            String id,
            String problemId,
            String problemTitle,
            String languageId,
            String status,
            Instant createdAt,
            String verdict,
            Long cpuNs,
            Long memoryBytes,
            Integer passedTestcaseCount,
            Integer executedTestcaseCount,
            Integer testcaseCount,
            String message,
            Instant finishedAt) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Limits(long cpuNs, long memoryBytes, Long clockNs) {}
    /**
     * 契约 problem-judge-snapshot：题目没有版本，也不含测试数据地址。CORE 时另有驱动代码 judgeTemplate 与该语言的前缀
     * solutionPrefix，ACM 时两者为 null；驱动代码不得写进日志。
     */
    public record Snapshot(
            String problemId,
            String problemTitle,
            String languageId,
            String codeMode,
            String judgeTemplate,
            String solutionPrefix) {
        @Override
        public String toString() {
            return "Snapshot[problemId=" + problemId + ", codeMode=" + codeMode + "]";
        }
    }

    public record Profile(
            String problemId,
            String languageId,
            String languageCalibrationId,
            Limits effectiveLimits,
            long executionBudgetNs) {}
    /**
     * 契约 judge-input：只冻结源码、语言、标定与限制；测试数据地址由 judging-service 判题时取。
     * CORE 时 completeSource 是驱动文件，另冻结用户文件、它的摘要与前缀行数；ACM 时三者为 null，且不能出现在 JSON 里。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Input(
            String submissionId,
            String contractVersion,
            String problemId,
            String languageId,
            String completeSource,
            String sourceSha256,
            String solutionSource,
            String solutionSha256,
            Integer solutionPrefixLineCount,
            String languageCalibrationId,
            Limits effectiveLimits,
            Instant createdAt) {
        @Override
        public String toString() {
            return "Input[submissionId=" + submissionId + ", source=<redacted>]";
        }
    }

    public record HistoryPage(java.util.List<View> items, int page, int size, long totalElements, int totalPages) {}

    public record Source(String submissionId, String problemId, String languageId, String source) {
        @Override
        public String toString() {
            return "Source[submissionId=" + submissionId + ", source=<redacted>]";
        }
    }

    public record Created(View view, boolean fresh) {}
}
