package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenConfigTests {

    @Test
    void generatesAProcessLocalSigningKeyWithoutWritingASecret() throws Exception {
        TokenConfig config = new TokenConfig();
        var properties = properties(
                TokenConfig.LOCAL_KEY_ID,
                TokenConfig.GENERATED_LOCAL_KEY_LOCATION,
                TokenConfig.GENERATED_LOCAL_KEY_LOCATION);

        SigningKeys first = config.signingKeys(
                properties, new DefaultResourceLoader(), new MockEnvironment());
        SigningKeys second = config.signingKeys(
                properties, new DefaultResourceLoader(), new MockEnvironment());

        assertThat(first.current().getKeyID()).isEqualTo(TokenConfig.LOCAL_KEY_ID);
        assertThat(first.current().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(first.publicJwkSet().getKeys()).containsExactly(first.current().toPublicJWK());
        assertThat(first.privateKey().getModulus()).isNotEqualTo(second.privateKey().getModulus());
    }

    @Test
    void rejectsGeneratedSigningKeysInProduction() {
        var properties = properties(
                TokenConfig.LOCAL_KEY_ID,
                TokenConfig.GENERATED_LOCAL_KEY_LOCATION,
                TokenConfig.GENERATED_LOCAL_KEY_LOCATION);
        var production = new MockEnvironment().withProperty("spring.profiles.active", "prod");
        production.setActiveProfiles("prod");

        assertThatThrownBy(() -> new TokenConfig().signingKeys(
                        properties, new DefaultResourceLoader(), production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production requires explicit cherry.auth");
    }

    @Test
    void reportsMissingProductionKeyEnvironmentVariablesBeforeLoadingResources() {
        var properties = properties(
                "${CHERRY_AUTH_KEY_ID}",
                "${CHERRY_AUTH_PRIVATE_KEY_LOCATION}",
                "${CHERRY_AUTH_PUBLIC_KEY_LOCATION}");
        var production = new MockEnvironment();
        production.setActiveProfiles("production");

        assertThatThrownBy(() -> new TokenConfig().signingKeys(
                        properties, new DefaultResourceLoader(), production))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Production requires explicit cherry.auth key-id, private-key-location and public-key-location");
    }

    @Test
    void rejectsAOneSidedGeneratedKeyOverride() {
        var properties = properties(
                TokenConfig.LOCAL_KEY_ID,
                TokenConfig.GENERATED_LOCAL_KEY_LOCATION,
                "file:local-public.pem");

        assertThatThrownBy(() -> new TokenConfig().signingKeys(
                        properties, new DefaultResourceLoader(), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must both use generated:local");
    }

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
        return properties(keyId, "unused", "unused");
    }

    private static AuthProperties properties(String keyId, String privateKeyLocation, String publicKeyLocation) {
        return new AuthProperties(
                "server",
                "cherry-oj-user-service",
                "cherry-oj-internal",
                keyId,
                privateKeyLocation,
                publicKeyLocation,
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
