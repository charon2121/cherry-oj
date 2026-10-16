package com.cherryoj.problemservice.security;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.cherryoj.serviceidentity.ServiceNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

/** judging-service 取题目当前测试数据地址；只接受 judging-service 的服务令牌。 */
@Configuration(proxyBeanMethods = false)
class JudgingTestDataSecurity {
    @Bean @Order(-9)
    SecurityFilterChain judgingTestDataChain(HttpSecurity http, ServiceCallVerifier services) throws Exception {
        http.securityMatcher("/internal/judging/**");
        return services.restrictTo(http, ServiceNames.JUDGING);
    }
}
