package com.cherryoj.problemservice.security;

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

/** judging-service 取题目当前测试数据地址的内部端点，只接受 judging-service 的服务令牌。 */
@Configuration(proxyBeanMethods = false)
class JudgingTestDataSecurity {
    @Bean @Order(-9)
    SecurityFilterChain judgingTestDataChain(HttpSecurity http,
            @Value("${cherry.service-calls.judging-problem-tokens:}") String tokens) throws Exception {
        http.securityMatcher("/internal/judging/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        // 未配置令牌时关闭内部端点，不影响题目业务启动。
        if (tokens.isBlank()) return http.authorizeHttpRequests(a -> a.anyRequest().denyAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((q, r, x) -> r.sendError(401))
                        .accessDeniedHandler((q, r, x) -> r.sendError(401))).build();
        return http.addFilterBefore(new ServiceTokenFilter("judging-service",
                        new ServiceCredentials(Arrays.stream(tokens.split(",")).map(String::trim).toList())), BasicAuthenticationFilter.class)
                .authorizeHttpRequests(a -> a.anyRequest().hasAuthority("SERVICE_CALL")).build();
    }
}
