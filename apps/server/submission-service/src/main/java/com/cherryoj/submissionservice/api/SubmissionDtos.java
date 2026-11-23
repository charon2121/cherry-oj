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

    public record HistoryPage(java.util.List<View> items, int page, int size, long totalElements, int totalPages) {}

    public record SubmissionSource(String submissionId, String problemId, String languageId, String source) {
        @Override
        public String toString() {
            return "SubmissionSource[submissionId=" + submissionId + ", source=<redacted>]";
        }
    }

    public record Created(View view, boolean fresh) {}
}
