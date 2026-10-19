package com.cherryoj.identitysecurity.service;

import com.cherryoj.identitysecurity.IdentityVerifierProperties;
import com.cherryoj.serviceidentity.ServiceKeys;
import com.cherryoj.serviceidentity.ServiceTokenClient;
import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 调用方：本服务要调用其他服务时 @Import 它。私钥与 token-uri 缺失时启动即失败，而不是等到第一次调用。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceIdentityProperties.class)
public class ServiceTokenClientConfiguration {

    @Bean
    public ServiceTokenClient serviceTokenClient(
            ServiceIdentityProperties service, IdentityVerifierProperties identity) {
        if (service.tokenUri() == null) {
            throw new IllegalStateException("cherry.service-identity.token-uri is required for " + service.name());
        }
        return new ServiceTokenClient(
                service.name(),
                ServiceKeys.privateKey(service.privateKeyLocation()),
                identity.issuer(),
                service.tokenUri(),
                Clock.systemUTC());
    }
}
