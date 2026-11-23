package com.cherryoj.judgingservice.formal;

public interface JudgeInputClient {
    JudgeInput get(String submissionId, String traceParent);
}
