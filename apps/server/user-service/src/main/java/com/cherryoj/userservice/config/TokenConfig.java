package com.cherryoj.userservice.config;

import com.cherryoj.userservice.security.SigningKeys;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.converter.RsaKeyConverters;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuedAtValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class TokenConfig {

    @Bean
    SigningKeys signingKeys(AuthProperties properties, ResourceLoader resourceLoader) throws IOException {
        validate(properties);
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (InputStream stream = resource(resourceLoader, properties.privateKeyLocation()).getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(stream);
        }
        try (InputStream stream = resource(resourceLoader, properties.publicKeyLocation()).getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(stream);
        }
        RSAKey current = new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(properties.keyId())
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
        List<JWK> publicKeys = new ArrayList<>();
        publicKeys.add(current.toPublicJWK());
        for (var entry : properties.previousPublicKeys().entrySet()) {
            try (InputStream stream = resource(resourceLoader, entry.getValue()).getInputStream()) {
                RSAPublicKey previous = RsaKeyConverters.x509().convert(stream);
                publicKeys.add(new RSAKey.Builder(previous)
                        .keyID(entry.getKey())
                        .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                        .build());
            }
        }
        return new SigningKeys(current, new JWKSet(publicKeys), publicKey, privateKey);
    }

    @Bean
    JwtEncoder jwtEncoder(SigningKeys keys) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(keys.current())));
    }

    @Bean
    JwtDecoder jwtDecoder(SigningKeys keys, AuthProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSource(new ImmutableJWKSet<SecurityContext>(keys.publicJwkSet()))
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .build();
        JwtTimestampValidator timestamp = new JwtTimestampValidator(properties.clockSkew());
        timestamp.setAllowEmptyExpiryClaim(false);
        JwtIssuedAtValidator issuedAt = new JwtIssuedAtValidator(true);
        issuedAt.setClockSkew(properties.clockSkew());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                timestamp,
                issuedAt,
                new JwtIssuerValidator(properties.issuer()),
                new JwtAudienceValidator(properties.audience()),
                requiredClaims()));
        return decoder;
    }

    private static OAuth2TokenValidator<Jwt> requiredClaims() {
        OAuth2Error invalid = new OAuth2Error(
                "invalid_token", "Required identity claims are invalid", null);
        return jwt -> validSubject(jwt.getSubject())
                        && validRoles(jwt.getClaimAsStringList("roles"))
                        && jwt.getClaim("sv") instanceof Number
                        && jwt.getClaim("pwd") instanceof Boolean
                        && jwt.getId() != null && !jwt.getId().isBlank()
                        && jwt.getHeaders().get("kid") instanceof String kid && !kid.isBlank()
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(invalid);
    }

    private static boolean validSubject(String subject) {
        try {
            UUID.fromString(subject);
            return true;
        } catch (RuntimeException error) {
            return false;
        }
    }

    private static boolean validRoles(List<String> roles) {
        return roles != null && !roles.isEmpty() && Set.of("USER", "ADMIN").containsAll(roles);
    }

    private static Resource resource(ResourceLoader resourceLoader, String location) {
        return resourceLoader.getResource(location);
    }

    private static void validate(AuthProperties properties) {
        requireText(properties.issuer(), "issuer");
        requireText(properties.audience(), "audience");
        requireText(properties.keyId(), "key-id");
        requireText(properties.privateKeyLocation(), "private-key-location");
        requireText(properties.publicKeyLocation(), "public-key-location");
        requireDuration(properties.accessTokenTtl(), Duration.ofSeconds(30), Duration.ofMinutes(5), "access-token-ttl");
        requireDuration(properties.clockSkew(), Duration.ZERO, Duration.ofSeconds(60), "clock-skew");
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("cherry.auth." + name + " must be configured");
        }
    }

    private static void requireDuration(Duration value, Duration minimum, Duration maximum, String name) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
            throw new IllegalStateException("cherry.auth." + name + " is outside the allowed range");
        }
    }
}
