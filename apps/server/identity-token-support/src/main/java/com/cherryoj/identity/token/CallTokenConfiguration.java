package com.cherryoj.identity.token;

import java.time.Clock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 本服务的签名器与公钥目录。私钥或任一服务的公钥缺失时启动失败。各服务 @Import 它。 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ServiceIdentityProperties.class)
public class CallTokenConfiguration {
    @Bean
    public CallTokenSigner callTokenSigner(ServiceIdentityProperties properties) {
        return new CallTokenSigner(
                properties.name(), ServiceKeys.privateKey(properties.privateKeyLocation()), Clock.systemUTC());
    }

    @Bean
    public PublicKeyDirectory publicKeyDirectory(ServiceIdentityProperties properties) {
        return PublicKeyDirectory.load(properties.publicKeysDir());
    }
}
