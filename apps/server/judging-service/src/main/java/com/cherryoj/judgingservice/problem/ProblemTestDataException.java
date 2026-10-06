package com.cherryoj.judgingservice.problem;

/** code 只用于日志与判题失败原因，不带地址或响应正文。 */
public final class ProblemTestDataException extends RuntimeException {
    private final String code;

    public ProblemTestDataException(String code) {
        super(code);
        this.code = code;
    }

    public String code() { return code; }
}
