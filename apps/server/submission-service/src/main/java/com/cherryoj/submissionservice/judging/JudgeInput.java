package com.cherryoj.submissionservice.judging;

import com.cherryoj.submissionservice.judging.ExecutionProfile.JudgeLimits;
import com.cherryoj.submissionservice.judging.ExecutionProfile.LimitDerivation;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * 契约 judge-input：只冻结源码、语言、标定与限制；测试数据地址由 judging-service 判题时取。
 * CORE 时 completeSource 是驱动文件，另冻结用户文件、它的摘要与前缀行数；ACM 时三者为 null，且不能出现在 JSON 里。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record JudgeInput(
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
        JudgeLimits effectiveLimits,
        String limitSource,
        // 契约要求 limitDerivation 始终出现：非 DERIVED 时写 null，不能像 CORE 字段那样省略
        @JsonInclude(JsonInclude.Include.ALWAYS) LimitDerivation limitDerivation,
        Instant createdAt) {
    @Override
    public String toString() {
        return "JudgeInput[submissionId=" + submissionId + ", source=<redacted>]";
    }
}
