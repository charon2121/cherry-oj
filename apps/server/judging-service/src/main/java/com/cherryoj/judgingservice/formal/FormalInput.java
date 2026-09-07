package com.cherryoj.judgingservice.formal;

import com.cherryoj.judgingservice.judge.JudgeGateway.Limits;
import java.time.Instant;

public record FormalInput(String submissionId, String contractVersion, String problemId, String problemVersionId,
                          String testDataVersionId, String languageId, String completeSource, String sourceSha256,
                          String judgeEnvironmentId, String environmentFingerprint, String languageCalibrationId,
                          Limits effectiveLimits, Instant createdAt, String testDataContentSha256, int totalCount, long executionBudgetNs) {
    @Override public String toString() { return "FormalInput[submissionId="+submissionId+", source=<redacted>]"; }
}
