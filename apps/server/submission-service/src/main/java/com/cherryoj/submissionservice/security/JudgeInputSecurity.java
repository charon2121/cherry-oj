package com.cherryoj.submissionservice.security;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.cherryoj.serviceidentity.ServiceNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** judging-service 判题时拉取冻结的 JudgeInput；只接受 judging-service 的服务令牌。 */
@Configuration(proxyBeanMethods = false)
class JudgeInputSecurity {
    @Bean @Order(-10)
    SecurityFilterChain judgeInputChain(HttpSecurity http, ServiceCallVerifier services) throws Exception {
        http.securityMatcher("/internal/judging/judge-inputs/*");
        return services.restrictTo(http, ServiceNames.JUDGING);
    }
}
