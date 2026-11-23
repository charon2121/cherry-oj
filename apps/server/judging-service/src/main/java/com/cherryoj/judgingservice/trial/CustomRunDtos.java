package com.cherryoj.judgingservice.trial;

import com.cherryoj.judgingservice.api.JudgingDtos;
import jakarta.validation.constraints.*;
import java.util.UUID;

public final class CustomRunDtos {
    private CustomRunDtos() {}

    /**
     * 契约见 contracts/custom-run-internal.schema.json。CORE 时 source 是驱动文件，solutionSource 是用户文件（前缀 + 用户代码），
     * 两个 CORE 字段同时出现或同时缺席；用户代码自身的 256 KiB 上限由 submission-service 在入口检查。
     */
    public record Request(
            @NotNull UUID problemId,

            @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN) @NotNull
            String languageId,

            @NotBlank @Size(max = 1048576) String source,
            @Size(min = 1, max = 327680) String solutionSource,
            @Min(0) Integer solutionPrefixLineCount,
            @NotNull @Size(max = 65536) String inputText,
            @Positive long deadlineEpochMs) {
        @Override
        public String toString() {
            return "TrialRequest[private]";
        }
    }

    public record Output(String text, long capturedBytes, boolean truncated) {
        @Override
        public String toString() {
            return "Output[private]";
        }
    }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Result(
            String status,
            Long cpuNs,
            Long memoryBytes,
            Output stdout,
            Output stderr,
            String compileDiagnostic,
            EffectiveLimits effectiveLimits) {
        @Override
        public String toString() {
            return "TrialResult[status=" + status + "]";
        }
    }

    public record EffectiveLimits(long cpuNs, long memoryBytes, long clockNs) {}
}
