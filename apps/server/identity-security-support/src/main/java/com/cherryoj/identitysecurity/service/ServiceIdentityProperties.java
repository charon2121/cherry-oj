package com.cherryoj.identitysecurity.service;

import com.cherryoj.serviceidentity.ServiceNames;
import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 本服务的服务身份。name 是令牌里的服务名（默认取 spring.application.name）；只做被调方的服务
 * 不需要 private-key-location 与 token-uri，要调用其他服务的才需要。
 */
@ConfigurationProperties("cherry.service-identity")
public record ServiceIdentityProperties(String name, String privateKeyLocation, URI tokenUri) {

    public ServiceIdentityProperties {
        ServiceNames.require(name, "cherry.service-identity.name");
    }
}
