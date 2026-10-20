package com.cherryoj.submissionservice.security;

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
                .endpoint("/api/submissions", "/api/submissions/**", "/api/submission-requests/**")
                .callers(ServiceNames.GATEWAY)
                .anyUser()
                .endpoint(HttpMethod.POST, "/api/custom-runs")
                .callers(ServiceNames.GATEWAY)
                .anyUser()
                .endpoint(HttpMethod.GET, "/internal/judging/judge-inputs/*")
                .callers(ServiceNames.JUDGING)
                .withoutUser()
                .build();
    }
}
