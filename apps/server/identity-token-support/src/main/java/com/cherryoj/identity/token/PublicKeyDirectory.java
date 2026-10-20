package com.cherryoj.identity.token;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

/** 公钥目录：每个服务一把公钥，文件名 <服务名>-public.pem。缺任何一个服务的公钥都不能使用。 */
public final class PublicKeyDirectory {
    private final Map<String, RSAPublicKey> keys;

    private PublicKeyDirectory(Map<String, RSAPublicKey> keys) {
        this.keys = Map.copyOf(keys);
    }

    public static PublicKeyDirectory load(Path directory) {
        if (directory == null || !Files.isDirectory(directory)) {
            throw new IllegalStateException("service public key directory does not exist: " + directory);
        }
        Map<String, RSAPublicKey> keys = new HashMap<>();
        for (String service : ServiceNames.ALL) {
            Path file = directory.resolve(service + "-public.pem");
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException("missing public key for " + service + ": " + file);
            }
            keys.put(service, ServiceKeys.publicKey(file.toString()));
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
