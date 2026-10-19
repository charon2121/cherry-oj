package com.cherryoj.userservice.security;

import com.cherryoj.serviceidentity.ServiceKeys;
import com.cherryoj.serviceidentity.ServiceNames;
import com.cherryoj.userservice.config.ServiceClientsProperties;
import java.security.interfaces.RSAPublicKey;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 已登记服务的公钥。启动时全部读入并校验：配错的公钥让 user-service 起不来，而不是等第一次换令牌。 */
@Component
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class ServiceClientRegistry {

    private final Map<String, RSAPublicKey> keys;

    public ServiceClientRegistry(ServiceClientsProperties properties) {
        var loaded = new LinkedHashMap<String, RSAPublicKey>();
        properties
                .serviceClients()
                .forEach((name, location) -> loaded.put(
                        ServiceNames.require(name, "cherry.service-clients key"), ServiceKeys.publicKey(location)));
        this.keys = Map.copyOf(loaded);
    }

    public RSAPublicKey publicKey(String service) {
        return keys.get(service);
    }

    /** 服务令牌可以发往哪些目标：接收服务调用的服务（含 user-service 自己，gateway 调 /internal/auth/*）。 */
    public boolean knownAudience(String service) {
        return ServiceNames.RECEIVERS.contains(service);
    }

    public Set<String> services() {
        return keys.keySet();
    }
}
