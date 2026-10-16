package com.cherryoj.problemservice.security;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.cherryoj.serviceidentity.ServiceNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** submission-service 创建提交时取题目快照；只接受 submission-service 的服务令牌。 */
@Configuration(proxyBeanMethods = false)
class SubmissionProblemSnapshotSecurity {
    @Bean @Order(-10)
    SecurityFilterChain submissionProblemSnapshotChain(HttpSecurity http, ServiceCallVerifier services) throws Exception {
        http.securityMatcher("/internal/submission/problem-snapshot");
        return services.restrictTo(http, ServiceNames.SUBMISSION);
    }
}
