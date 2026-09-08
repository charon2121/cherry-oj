package com.cherryoj.judgingservice.security;

import com.cherryoj.identitysecurity.service.ServiceCredentials;
import com.cherryoj.identitysecurity.service.ServiceTokenFilter;
import java.util.Arrays;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
class SubmissionExecutionProfileSecurity {
    @Bean @Order(-10)
    SecurityFilterChain submissionExecutionProfileChain(HttpSecurity http,
            @Value("${cherry.service-calls.submission-judging-tokens:}") String tokens) throws Exception {
        http.securityMatcher("/internal/submission/execution-profile", "/internal/submission/trials")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        // 未配置新功能时关闭内部端点，不影响既有题目/节点业务启动。
        if (tokens.isBlank()) return http.authorizeHttpRequests(a -> a.anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> r.sendError(401))
                        .accessDeniedHandler((q, r, x) -> r.sendError(401))).build();
        return http.addFilterBefore(new ServiceTokenFilter("submission-service",
                        new ServiceCredentials(Arrays.stream(tokens.split(",")).map(String::trim).toList())), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("SERVICE_CALL")).build();
    }
}
