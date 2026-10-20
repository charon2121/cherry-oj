package com.cherryoj.problemservice.integration.judging;

import com.cherryoj.identity.token.CallUser;

/** 代表管理员调用 judging-service：admin 原样转发到调用令牌的 user。 */
public interface JudgingClient {

    JudgingDtos.Calibration calibrate(JudgingDtos.CalibrationRequest request, CallUser admin, String traceparent);

    /** testDataDigest 是题目当前测试数据的指纹：标定记录的指纹对不上就视为过期。 */
    JudgingDtos.Readiness readiness(
            String problemId, String languageId, String testDataDigest, CallUser admin, String traceparent);
}
