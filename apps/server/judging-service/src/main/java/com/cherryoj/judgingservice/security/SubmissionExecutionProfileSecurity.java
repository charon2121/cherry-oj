package com.cherryoj.judgingservice.security;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.cherryoj.serviceidentity.ServiceNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** submission-service 取执行限制与转发自测；只接受 submission-service 的服务令牌（管理员 JWT 也不行）。 */
@Configuration(proxyBeanMethods = false)
class SubmissionExecutionProfileSecurity {
    @Bean
    @Order(-10)
    SecurityFilterChain submissionExecutionProfileChain(HttpSecurity http, ServiceCallVerifier services)
            throws Exception {
        http.securityMatcher("/internal/submission/execution-profile", "/internal/submission/trials");
        return services.restrictTo(http, ServiceNames.SUBMISSION);
    }
}
