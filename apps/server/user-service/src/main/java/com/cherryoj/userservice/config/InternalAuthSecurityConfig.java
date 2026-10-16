package com.cherryoj.userservice.config;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.cherryoj.serviceidentity.ServiceNames;
import com.cherryoj.userservice.security.SigningKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 登录、换取委托令牌、校验与吊销会话的内部端点只给 gateway-service：此前它们对任何能连到本服务的
 * 进程开放，能直接拿密码换令牌。gateway 用自己的服务令牌（aud=user-service）调用。
 * 本服务就是签发方，直接用自己的公钥校验，不经 HTTP 取 JWKS。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
class InternalAuthSecurityConfig {

    /**
     * 换服务令牌的入口单独一条链、不装 Bearer 令牌过滤器：请求里带的是客户端断言而不是委托 JWT，
     * 交给主链会被当成无效的用户令牌直接 401。身份由 ServiceTokenController 校验断言签名证明。
     */
    @Bean
    @Order(0)
    SecurityFilterChain serviceTokenIssuanceChain(HttpSecurity http) throws Exception {
        return http.securityMatcher("/internal/service-tokens")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
                .build();
    }

    @Bean
    @Order(1)
    SecurityFilterChain internalAuthChain(HttpSecurity http, SigningKeys keys, AuthProperties properties) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256).build();
        decoder.setJwtValidator(ServiceCallVerifier.validator(properties.issuer(), ServiceNames.USER, properties.clockSkew()));
        http.securityMatcher("/internal/auth/**");
        return new ServiceCallVerifier(decoder, ServiceNames.USER).restrictTo(http, ServiceNames.GATEWAY);
    }
}
