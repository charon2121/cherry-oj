package com.cherryoj.identity.token;

import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

/** 公钥目录：每个服务一把公钥，文件名 <服务名>-public.pem。缺任何一个服务的公钥都不能使用。 */
public final class PublicKeyDirectory {
    private final Map<String, RSAPublicKey> keys;

    private PublicKeyDirectory(Map<String, RSAPublicKey> keys) {
        this.keys = Map.copyOf(keys);
    }

    /** location 写作目录路径、file:目录或 classpath:目录。 */
    public static PublicKeyDirectory load(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("service public key directory is not configured");
        }
        String base = location.endsWith("/") ? location.substring(0, location.length() - 1) : location;
        Map<String, RSAPublicKey> keys = new HashMap<>();
        for (String service : ServiceNames.ALL) {
            keys.put(service, ServiceKeys.publicKey(base + "/" + service + "-public.pem"));
        }
        return new PublicKeyDirectory(keys);
    }

    /** 只给测试与单元组装使用。 */
    public static PublicKeyDirectory of(Map<String, RSAPublicKey> keys) {
        return new PublicKeyDirectory(keys);
    }

    /** 服务名对应的公钥；不是目录里的服务时为 null。 */
    public RSAPublicKey key(String service) {
        return keys.get(service);
    }
}
