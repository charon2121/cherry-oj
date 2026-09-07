package com.cherryoj.submissionservice.api;
import org.springframework.http.HttpStatus;
public final class SubmissionException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    public SubmissionException(HttpStatus status, String code, String message) { super(message); this.status=status; this.code=code; }
    public HttpStatus status() { return status; }
    public String code() { return code; }
}
