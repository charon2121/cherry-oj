package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.ExecutionProfileService;
import org.springframework.http.HttpStatus;

public final class JudgingApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;

    public JudgingApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public static JudgingApiException from(ExecutionProfileService.Rejected rejected) {
        return switch (rejected.reason()) {
            case NOT_READY ->
                new JudgingApiException(HttpStatus.SERVICE_UNAVAILABLE, "JUDGING_NOT_READY", "当前题目暂时无法判题。");
            case TRIAL_BUDGET_EXCEEDED ->
                new JudgingApiException(HttpStatus.UNPROCESSABLE_ENTITY, "RUN_LIMIT_UNSUPPORTED", "此题目的执行预算超过自测期限。");
        };
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }
}
