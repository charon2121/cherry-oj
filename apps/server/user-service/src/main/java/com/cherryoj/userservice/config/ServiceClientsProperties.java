package com.cherryoj.userservice.config;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 可以向本服务换取服务令牌的调用方：服务名 → 它的公钥位置（PEM，X.509）。这里只登记公钥，
 * 不保存任何秘密；私钥只在各服务自己手里。
 */
@ConfigurationProperties("cherry")
public record ServiceClientsProperties(Map<String, String> serviceClients) {

    public ServiceClientsProperties {
        serviceClients = serviceClients == null ? Map.of() : Map.copyOf(serviceClients);
    }
}
