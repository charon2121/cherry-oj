package com.cherryoj.judgingservice.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

/** 节点协议拥有单独的鉴权链，不依赖管理员 JWT 或其 JWKS 可用性。 */
@Configuration(proxyBeanMethods = false)
class JudgeNodeSecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain nodeSecurity(HttpSecurity http, JudgeNodeProperties properties) throws Exception {
        return http.securityMatcher("/internal/judge-nodes/v1/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(new OncePerRequestFilter() {
                    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                                              FilterChain chain) throws IOException, ServletException {
                        String header = request.getHeader("Authorization");
                        String token = properties.controlToken();
                        if (token == null || token.isBlank() || header == null
                                || !MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                                        header.getBytes(StandardCharsets.UTF_8))) {
                            response.setStatus(401);
                            response.setContentType("application/json");
                            response.getWriter().write("{\"code\":\"NODE_UNAUTHORIZED\",\"detail\":\"Node authentication required\"}");
                            return;
                        }
                        chain.doFilter(request, response);
                    }
                }, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(r -> r.anyRequest().permitAll()).build();
    }
}
