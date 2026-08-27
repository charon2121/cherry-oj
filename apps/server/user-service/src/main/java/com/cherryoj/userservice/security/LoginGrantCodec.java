package com.cherryoj.userservice.security;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public final class LoginGrantCodec {

    private final SecureRandom random;

    public LoginGrantCodec(SecureRandom random) {
        this.random = random;
    }

    public String generate() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public byte[] digest(String grant) {
        if (grant == null || grant.length() < 40 || grant.length() > 128) {
            return new byte[32];
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(grant.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
