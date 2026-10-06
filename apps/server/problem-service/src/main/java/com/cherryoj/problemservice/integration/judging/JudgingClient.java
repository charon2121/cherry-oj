package com.cherryoj.problemservice.integration.judging;

public interface JudgingClient {

    JudgingDtos.Calibration calibrate(
            JudgingDtos.CalibrationRequest request,
            String delegatedJwt,
            String traceparent);

    /** testDataDigest 是题目当前测试数据的指纹：标定记录的指纹对不上就视为过期。 */
    JudgingDtos.Readiness readiness(
            String problemId,
            String languageId,
            String testDataDigest,
            String delegatedJwt,
            String traceparent);
}
