package com.cherryoj.judgingservice.problem;

import com.cherryoj.identitysecurity.service.ServiceCredentials;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** token 必须属于 problem-service 的 cherry.service-calls.judging-problem-tokens；未配置时取数据一律失败。 */
@ConfigurationProperties("cherry.judging.problem")
public record ProblemServiceProperties(@DefaultValue("http://127.0.0.1:8082") String url,
                                       @DefaultValue("") String token) {
    public ProblemServiceProperties {
        if (url == null || url.isBlank()) throw new IllegalArgumentException("problem-service url is required");
        if (token != null && !token.isBlank()) ServiceCredentials.validate(token);
    }
}
