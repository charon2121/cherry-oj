package com.cherryoj.identity.token;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 本服务的身份：服务名、私钥位置、公钥目录。 */
@ConfigurationProperties("cherry.service-identity")
public record ServiceIdentityProperties(String name, String privateKeyLocation, String publicKeysDir) {
    public ServiceIdentityProperties {
        if (!ServiceNames.ALL.contains(name)) {
            throw new IllegalArgumentException("cherry.service-identity.name must be one of " + ServiceNames.ALL);
        }
        if (privateKeyLocation == null || privateKeyLocation.isBlank()) {
            throw new IllegalArgumentException("cherry.service-identity.private-key-location is required");
        }
        if (publicKeysDir == null || publicKeysDir.isBlank()) {
            throw new IllegalArgumentException("cherry.service-identity.public-keys-dir is required");
        }
    }
}
