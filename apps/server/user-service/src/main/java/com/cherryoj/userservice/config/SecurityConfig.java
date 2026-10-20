package com.cherryoj.userservice.config;

import com.cherryoj.identity.resource.CallSecurity;
import com.cherryoj.identity.resource.EndpointPolicies;
import com.cherryoj.identity.token.ServiceNames;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;

/** 本服务接受哪些调用。bootstrap 模式不启动 Web，也不需要服务密钥。 */
@Configuration(proxyBeanMethods = false)
@Import(CallSecurity.class)
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class SecurityConfig {
    @Bean
    EndpointPolicies endpointPolicies() {
        return EndpointPolicies.builder()
                .endpoint(
                        HttpMethod.POST,
                        "/internal/auth/authenticate",
                        "/internal/auth/validate",
                        "/internal/auth/revoke")
                .callers(ServiceNames.GATEWAY)
                .withoutUser()
                .endpoint(HttpMethod.POST, "/internal/users/me/password")
                .callers(ServiceNames.GATEWAY)
                .anyUser()
                .endpoint("/internal/admin/users", "/internal/admin/users/**")
                .callers(ServiceNames.GATEWAY)
                .admin()
                .build();
    }
}
