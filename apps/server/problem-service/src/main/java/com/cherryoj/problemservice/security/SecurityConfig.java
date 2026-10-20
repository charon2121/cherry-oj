package com.cherryoj.problemservice.security;

import com.cherryoj.identity.resource.CallSecurity;
import com.cherryoj.identity.resource.EndpointPolicies;
import com.cherryoj.identity.token.ServiceNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;

/** 本服务接受哪些调用。 */
@Configuration(proxyBeanMethods = false)
@Import(CallSecurity.class)
class SecurityConfig {
    @Bean
    EndpointPolicies endpointPolicies() {
        return EndpointPolicies.builder()
                .endpoint(HttpMethod.GET, "/internal/public/problems", "/internal/public/problems/*")
                .callers(ServiceNames.GATEWAY)
                .withoutUser()
                .endpoint("/internal/admin/problems", "/internal/admin/problems/**")
                .callers(ServiceNames.GATEWAY)
                .admin()
                .endpoint("/internal/admin/languages", "/internal/admin/languages/**")
                .callers(ServiceNames.GATEWAY)
                .admin()
                .endpoint(HttpMethod.POST, "/internal/submission/problem-snapshot")
                .callers(ServiceNames.SUBMISSION)
                .withoutUser()
                .endpoint(HttpMethod.GET, "/internal/judging/problems/*/test-data")
                .callers(ServiceNames.JUDGING)
                .withoutUser()
                .build();
    }
}
