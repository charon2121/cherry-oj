package com.cherryoj.identity.resource;

import com.cherryoj.identity.token.CallTokenConfiguration;
import com.cherryoj.identity.token.CallTokenSigner;
import com.cherryoj.identity.token.PublicKeyDirectory;
import jakarta.servlet.DispatcherType;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;

/**
 * 服务的主安全链：除健康检查外，每个请求都要带调用令牌并满足本服务声明的 EndpointPolicies。
 * 各服务 @Import 它并提供一个 EndpointPolicies bean；另有专用链（如判题节点的控制令牌）时用更小的 @Order 声明。
 */
@Configuration(proxyBeanMethods = false)
@Import(CallTokenConfiguration.class)
public class CallSecurity {
    static final List<String> PUBLIC_PATHS = List.of("/actuator/health", "/actuator/health/**", "/actuator/info");

    @Bean
    @Order(Ordered.LOWEST_PRECEDENCE - 10)
    public SecurityFilterChain callSecurityChain(
            HttpSecurity http, EndpointPolicies policies, PublicKeyDirectory keys, CallTokenSigner signer)
            throws Exception {
        var filter = new CallTokenFilter(policies, keys, signer.self(), PUBLIC_PATHS, Clock.systemUTC());
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .addFilterBefore(filter, AnonymousAuthenticationFilter.class)
                // 错误转发（/error）发生在原请求已通过校验之后，不再校验一次。
                .authorizeHttpRequests(requests -> requests.dispatcherTypeMatchers(DispatcherType.ERROR)
                        .permitAll()
                        .requestMatchers(PUBLIC_PATHS.toArray(String[]::new))
                        .permitAll()
                        .anyRequest()
                        .authenticated())
                .build();
    }
}
