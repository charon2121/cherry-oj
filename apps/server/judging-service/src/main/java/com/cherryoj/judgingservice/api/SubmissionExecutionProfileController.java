package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.ExecutionProfileService;
import com.cherryoj.judgingservice.application.ExecutionProfileService.Purpose;
import com.cherryoj.judgingservice.limits.ResolvedLanguageLimits;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.*;

/**
 * 契约见 contracts/execution-profile.schema.json：按题目×语言解析，测试点数与最大 IO 字节数取自题目此刻的测试数据；
 * 限制的来源与推导依据一起返回，由 submission-service 冻结（docs/protocols/limits.md）。
 */
@RestController
public class SubmissionExecutionProfileController {
    private final ExecutionProfileService profiles;

    public SubmissionExecutionProfileController(ExecutionProfileService profiles) {
        this.profiles = profiles;
    }

    @PostMapping("/internal/submission/execution-profile")
    public Profile resolve(
            @Valid @RequestBody Request request, @RequestHeader(value = "traceparent", required = false) String trace) {
        Purpose purpose = "trial".equals(request.purpose()) ? Purpose.TRIAL : Purpose.FORMAL;
        ExecutionProfileService.Profile profile;
        try {
            profile = profiles.resolve(request.problemId(), request.languageId(), purpose, trace);
        } catch (ExecutionProfileService.Rejected rejected) {
            throw JudgingApiException.from(rejected);
        }
        ResolvedLanguageLimits limits = profile.limits();
        return new Profile(
                request.problemId(),
                request.languageId(),
                limits.calibrationId(),
                new Limits(limits.cpuNs(), limits.memoryBytes(), limits.clockNs()),
                limits.limitSource(),
                limits.limitDerivation(),
                profile.executionBudgetNs());
    }

    public record Request(
            @NotBlank @Pattern(regexp = JudgingDtos.UUID_PATTERN)
            String problemId,

            @NotBlank @Pattern(regexp = JudgingDtos.LANGUAGE_PATTERN)
            String languageId,

            @Pattern(regexp = "formal|trial") String purpose) {}

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public record Limits(long cpuNs, long memoryBytes, Long clockNs) {}

    public record Profile(
            String problemId,
            String languageId,
            String languageCalibrationId,
            Limits effectiveLimits,
            String limitSource,
            ResolvedLanguageLimits.Derivation limitDerivation,
            long executionBudgetNs) {}
}
