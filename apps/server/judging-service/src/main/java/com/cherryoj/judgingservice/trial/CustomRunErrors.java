package com.cherryoj.judgingservice.trial;

import com.cherryoj.judgingservice.api.JudgingApiException;
import org.springframework.http.HttpStatus;

final class CustomRunErrors {
    private CustomRunErrors() {}

    static JudgingApiException error(HttpStatus status, String code, String detail) {
        return new JudgingApiException(status, code, detail);
    }

    static JudgingApiException unavailable() {
        return error(HttpStatus.SERVICE_UNAVAILABLE, "CUSTOM_RUN_UNAVAILABLE", "运行服务暂不可用。");
    }

    static JudgingApiException timeout() {
        return error(HttpStatus.GATEWAY_TIMEOUT, "CUSTOM_RUN_TIMEOUT", "本次运行结果未能确认，请稍后重试。");
    }
}
