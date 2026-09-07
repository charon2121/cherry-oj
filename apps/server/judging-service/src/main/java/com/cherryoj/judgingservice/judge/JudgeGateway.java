package com.cherryoj.judgingservice.judge;

public interface JudgeGateway {
    JudgeResult judge(String endpointRef, JudgeRequest request, String traceId) throws JudgeCallException;

    default JudgeResult judge(String endpoint, JudgeRequest request, String trace, java.time.Duration budget) throws JudgeCallException {
        return judge(endpoint, request, trace);
    }

    record JudgeRequest(String submissionId, String problemId, String problemVersionId,
                        String testDataVersionId, String languageId, String source,
                        Limits limits, String mode) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record Limits(long cpuNs, long memoryBytes, Long clockNs) {}
    record JudgeResult(String verdict, String environmentFingerprint, Long cpuNs,
                       Long memoryBytes, Integer score, String message, java.util.List<CaseResult> caseResults) {
        public JudgeResult(String verdict, String environmentFingerprint, Long cpuNs, Long memoryBytes, Integer score) {
            this(verdict,environmentFingerprint,cpuNs,memoryBytes,score,null,null);
        }
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    record CaseResult(int idx, String verdict, Long cpuNs, Long memoryBytes) {}

    final class JudgeCallException extends Exception {
        public JudgeCallException(String message) { super(message); }
        public JudgeCallException(String message, Throwable cause) { super(message, cause); }
    }
}
