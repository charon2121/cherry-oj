package com.cherryoj.problemservice.integration.judging;

import java.io.InputStream;

public interface JudgingClient {
    JudgingDtos.Deployment deploy(
            JudgingDtos.DeploymentMetadata metadata,
            InputStream archive,
            String delegatedJwt,
            String traceparent);

    JudgingDtos.Calibration calibrate(
            JudgingDtos.CalibrationRequest request,
            String delegatedJwt,
            String traceparent);

    JudgingDtos.Readiness readiness(
            String problemVersionId,
            String testDataVersionId,
            String expectedSha256,
            String languageId,
            String delegatedJwt,
            String traceparent);
}
