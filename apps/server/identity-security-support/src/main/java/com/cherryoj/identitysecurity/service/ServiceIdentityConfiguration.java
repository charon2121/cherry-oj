package com.cherryoj.identitysecurity.service;

import com.cherryoj.identitysecurity.IdentityVerifierProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.web.client.RestTemplate;

/** 被调方：用与用户委托 JWT 相同的签发方 JWKS 校验服务令牌。随 IdentityVerifierConfiguration 一起 @Import。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceIdentityProperties.class)
public class ServiceIdentityConfiguration {

    @Bean
    public ServiceCallVerifier serviceCallVerifier(
            IdentityVerifierProperties identity, ServiceIdentityProperties service) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(identity.connectTimeout());
        requestFactory.setReadTimeout(identity.readTimeout());
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(
                        identity.jwksUri().toString())
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(new RestTemplate(requestFactory))
                .build();
        decoder.setJwtValidator(ServiceCallVerifier.validator(identity.issuer(), service.name(), identity.clockSkew()));
        return new ServiceCallVerifier(decoder, service.name());
    }
}
