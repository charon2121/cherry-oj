package com.cherryoj.userservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserRole;
import com.cherryoj.userservice.domain.UserStatus;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenServiceTests {

    @Test
    void issuesOnlyTheApprovedClaimsWithTwoHourLifetime() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        var pair = generator.generateKeyPair();
        var rsa = new RSAKey.Builder((RSAPublicKey) pair.getPublic())
                .privateKey((RSAPrivateKey) pair.getPrivate())
                .keyID("test-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
        var encoder = new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsa)));
        Instant instant = Instant.parse("2026-08-26T01:00:00Z");
        var properties = new AuthProperties(
                "server",
                "cherry-oj-user-service",
                "cherry-oj-internal",
                "test-key",
                "unused",
                "unused",
                Map.of(),
                Duration.ofHours(2),
                Duration.ofSeconds(30),
                "fixed-absolute",
                2_592_000);
        var keys = new SigningKeys(rsa, new JWKSet(rsa.toPublicJWK()),
                (RSAPublicKey) pair.getPublic(), (RSAPrivateKey) pair.getPrivate());
        var service = new TokenService(encoder, keys, properties, Clock.fixed(instant, ZoneOffset.UTC));
        LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        var account = new UserAccount(
                "019c8e42-7f70-7000-8000-000000000001",
                "learner01",
                "learner01",
                "not-returned",
                UserRole.USER,
                UserStatus.ACTIVE,
                true,
                0,
                null,
                null,
                7,
                now,
                now,
                0);

        TokenValue token = service.issue(account);
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) pair.getPublic()).build();
        decoder.setJwtValidator(jwt -> OAuth2TokenValidatorResult.success());
        var decoded = decoder.decode(token.value());

        assertThat(decoded.getSubject()).isEqualTo(account.id());
        assertThat(decoded.getAudience()).containsExactly("cherry-oj-internal");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("USER");
        assertThat(decoded.<Long>getClaim("sv")).isEqualTo(7);
        assertThat(decoded.getClaimAsBoolean("pwd")).isTrue();
        assertThat(token.expiresAt()).isEqualTo(instant.plus(Duration.ofHours(2)));
        assertThat(decoded.getHeaders().get("kid")).isEqualTo("test-key");
        assertThat(decoded.getClaims()).doesNotContainKeys("username", "password", "loginGrant");
    }
}
