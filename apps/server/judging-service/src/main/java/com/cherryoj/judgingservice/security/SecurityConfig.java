package com.cherryoj.judgingservice.security;

import com.cherryoj.identity.resource.CallSecurity;
import com.cherryoj.identity.resource.EndpointPolicies;
import com.cherryoj.identity.token.ServiceNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;

/** 本服务接受哪些调用。判题节点的 /internal/judge-nodes/v1/** 走 JudgeNodeSecurityConfig 的控制令牌，不在这里。 */
@Configuration(proxyBeanMethods = false)
@Import(CallSecurity.class)
class SecurityConfig {
    @Bean
    EndpointPolicies endpointPolicies() {
        return EndpointPolicies.builder()
                .endpoint(HttpMethod.POST, "/internal/submission/execution-profile", "/internal/submission/trials")
                .callers(ServiceNames.SUBMISSION)
                .withoutUser()
                .endpoint(HttpMethod.GET, "/internal/admin/judge-nodes")
                .callers(ServiceNames.GATEWAY)
                .admin()
                .endpoint(HttpMethod.POST, "/internal/admin/calibrations")
                .callers(ServiceNames.PROBLEM)
                .admin()
                .endpoint(HttpMethod.GET, "/internal/admin/readiness")
                .callers(ServiceNames.PROBLEM)
                .admin()
                .build();
    }
}
