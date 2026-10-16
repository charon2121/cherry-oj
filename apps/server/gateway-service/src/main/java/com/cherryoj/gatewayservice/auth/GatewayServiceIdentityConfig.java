package com.cherryoj.gatewayservice.auth;

import java.net.URI;
import java.time.Clock;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.cherryoj.serviceidentity.ServiceKeys;
import com.cherryoj.serviceidentity.ServiceTokenClient;

/** gateway 自己的服务身份：私钥只在这里，向 user-service 换服务令牌。私钥缺失时启动即失败。 */
@Configuration(proxyBeanMethods = false)
class GatewayServiceIdentityConfig {

	@Bean
	ServiceTokenClient serviceTokenClient(
			@Value("${cherry.service-identity.name}") String name,
			@Value("${cherry.service-identity.private-key-location}") String privateKeyLocation,
			@Value("${cherry.service-identity.issuer}") String issuer,
			@Value("${cherry.service-identity.token-uri}") URI tokenUri) {
		return new ServiceTokenClient(name, ServiceKeys.privateKey(privateKeyLocation), issuer, tokenUri,
				Clock.systemUTC());
	}
}
