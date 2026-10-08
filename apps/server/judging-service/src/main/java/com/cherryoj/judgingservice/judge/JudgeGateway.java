package com.cherryoj.judgingservice.judge;

public interface JudgeGateway {
    JudgeResult judge(String endpointRef, JudgeRequest request, String traceId) throws JudgeCallException;

    default JudgeResult judge(String endpoint, JudgeRequest request, String trace, java.time.Duration budget) throws JudgeCallException {
        return judge(endpoint, request, trace);
    }

    /** testDataLocation 只有 mode=submit 才带；节点按它读取测试数据，协议见 docs/testdata-protocol.md。 */
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record JudgeRequest(String submissionId, String problemId, String testDataLocation, String languageId,
                        String source, Limits limits, String mode) {}
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    record Limits(long cpuNs, long memoryBytes, Long clockNs) {}
    record JudgeResult(String verdict, Long cpuNs, Long memoryBytes, Integer score, String message,
                       java.util.List<TestcaseResult> testcaseResults, String testDataDigest) {
        public JudgeResult(String verdict, Long cpuNs, Long memoryBytes, Integer score) {
            this(verdict,cpuNs,memoryBytes,score,null,null,null);
        }
        public JudgeResult(String verdict, Long cpuNs, Long memoryBytes, Integer score, String message,
                           java.util.List<TestcaseResult> testcaseResults) {
            this(verdict,cpuNs,memoryBytes,score,message,testcaseResults,null);
        }
    }
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown=true)
    record TestcaseResult(int idx, String verdict, Long cpuNs, Long memoryBytes) {}

    final class JudgeCallException extends Exception {
        public JudgeCallException(String message) { super(message); }
        public JudgeCallException(String message, Throwable cause) { super(message, cause); }
    }
}
