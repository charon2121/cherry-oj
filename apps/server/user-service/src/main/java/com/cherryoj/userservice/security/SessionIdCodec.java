package com.cherryoj.userservice.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

/** sessionId 是 32 字节随机数的 base64url；库里只存它的 SHA-256。 */
@Component
public final class SessionIdCodec {

    private final SecureRandom random;

    public SessionIdCodec(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public byte[] digest(String sessionId) {
        if (sessionId == null || sessionId.length() < 40 || sessionId.length() > 128) {
            return new byte[32];
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(sessionId.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
