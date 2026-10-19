package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeGateway.Limits;
import java.time.Instant;

/**
 * 契约见 contracts/judge-input.schema.json：只冻结源码、语言、标定与限制，不含测试数据地址。
 * CORE 时 completeSource 是驱动文件，另冻结用户文件、它的摘要与前缀行数；ACM 时三者都为 null。
 */
public record FormalInput(
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
        return "FormalInput[submissionId=" + submissionId + ", source=<redacted>]";
    }
}
