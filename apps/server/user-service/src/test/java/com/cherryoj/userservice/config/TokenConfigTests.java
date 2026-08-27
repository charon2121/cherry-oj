package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserRole;
import com.cherryoj.userservice.domain.UserStatus;
import com.cherryoj.userservice.security.SigningKeys;
import com.cherryoj.userservice.security.TokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenConfigTests {

    @Test
    void acceptsAnUnexpiredTokenSignedByThePreviousPublishedKey() throws Exception {
        RSAKey current = key("current-key");
        RSAKey previous = key("previous-key");
        SigningKeys keys = new SigningKeys(
                current,
                new JWKSet(List.of(current.toPublicJWK(), previous.toPublicJWK())),
                current.toRSAPublicKey(),
                current.toRSAPrivateKey());
        AuthProperties properties = properties("current-key");
        Instant now = Instant.now();
        TokenService previousSigner = new TokenService(
                new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(previous))),
                properties("previous-key"),
                Clock.fixed(now, ZoneOffset.UTC));

        String token = previousSigner.issue(account(now)).value();
        var decoded = new TokenConfig().jwtDecoder(keys, properties).decode(token);

        assertThat(decoded.getHeaders().get("kid")).isEqualTo("previous-key");
        assertThat(decoded.getSubject()).isEqualTo("019c8e42-7f70-7000-8000-000000000001");
    }

    private static RSAKey key(String keyId) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        return new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID(keyId)
                .algorithm(JWSAlgorithm.RS256)
                .build();
    }

    private static AuthProperties properties(String keyId) {
        return new AuthProperties(
                "server",
                "cherry-oj-user-service",
                "cherry-oj-internal",
                keyId,
                "unused",
                "unused",
                Map.of(),
                Duration.ofSeconds(120),
                Duration.ofSeconds(30),
                1_800,
                43_200,
                "true");
    }

    private static UserAccount account(Instant now) {
        LocalDateTime timestamp = LocalDateTime.ofInstant(now, ZoneOffset.UTC);
        return new UserAccount(
                "019c8e42-7f70-7000-8000-000000000001",
                "learner01",
                "learner01",
                "not-returned",
                UserRole.USER,
                UserStatus.ACTIVE,
                false,
                0,
                null,
                null,
                7,
                timestamp,
                timestamp,
                0);
    }
}
