package com.cherryoj.identitysecurity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** 每条调用链单独配置。只校验凭据，不授予业务角色，也不接收客户端自报的 caller。 */
public final class ServiceCredentials {
    private final List<byte[]> accepted;

    public ServiceCredentials(List<String> tokens) {
        if (tokens == null || tokens.isEmpty()) throw new IllegalArgumentException("service credentials required");
        accepted = tokens.stream().map(ServiceCredentials::validate).map(ServiceCredentials::digest).toList();
    }

    public boolean accepts(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 519) return false;
        byte[] candidate = digest(authorization.substring(7));
        boolean matches = false;
        // 比较定长摘要，并检查整个轮换集合，避免按匹配位置提前返回。
        for (byte[] token : accepted) matches |= MessageDigest.isEqual(token, candidate);
        return matches;
    }

    public static String validate(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{32,512}")) {
            throw new IllegalArgumentException("service credential must be 32-512 URL-safe characters");
        }
        return token;
    }

    private static byte[] digest(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (java.security.NoSuchAlgorithmException error) { throw new IllegalStateException(error); }
    }
}
