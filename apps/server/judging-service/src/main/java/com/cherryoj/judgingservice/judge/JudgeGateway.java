package com.cherryoj.judgingservice.judge;

public interface JudgeGateway {
    JudgeResult judge(String endpointRef, JudgeRequest request, String traceId) throws JudgeCallException;

    record JudgeRequest(String submissionId, String problemId, String problemVersionId,
                        String testDataVersionId, String languageId, String source,
                        Limits limits, String mode) {}
    record Limits(long cpuNs, long memoryBytes, Long clockNs) {}
    record JudgeResult(String verdict, String environmentFingerprint, Long cpuNs,
                       Long memoryBytes, Integer score) {}

    final class JudgeCallException extends Exception {
        public JudgeCallException(String message) { super(message); }
        public JudgeCallException(String message, Throwable cause) { super(message, cause); }
    }
}
