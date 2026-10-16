package com.cherryoj.judgingservice.problem;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** problem-service 地址。调用身份用本服务的服务令牌（cherry.service-identity），不再配共享令牌。 */
@ConfigurationProperties("cherry.judging.problem")
public record ProblemServiceProperties(@DefaultValue("http://127.0.0.1:8082") String url) {
    public ProblemServiceProperties {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("problem-service url is required");
    }
}
