package com.cherryoj.userservice.config;

import com.cherryoj.userservice.security.SigningKeys;
import com.cherryoj.identitysecurity.PublicKeyFingerprint;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.io.IOException;
import java.io.InputStream;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(TokenConfig.class);

    @Bean
    SigningKeys signingKeys(
            AuthProperties properties,
            ResourceLoader resourceLoader,
            Environment environment) throws IOException {
        validate(properties);
        boolean production = isProduction(environment);
        if (production && (unresolvedPlaceholder(properties.privateKeyLocation())
                || unresolvedPlaceholder(properties.publicKeyLocation()))) {
            throw new IllegalStateException(
                    "Production requires explicit cherry.auth private-key-location and public-key-location");
        }
        return resourceSigningKeys(properties, resourceLoader);
    }

    private static SigningKeys resourceSigningKeys(AuthProperties properties, ResourceLoader resourceLoader)
            throws IOException {
        validatePrivateKeyFile(resourceLoader, properties.privateKeyLocation());
        RSAPrivateKey privateKey;
        RSAPublicKey publicKey;
        try (InputStream stream = resource(resourceLoader, properties.privateKeyLocation()).getInputStream()) {
            privateKey = RsaKeyConverters.pkcs8().convert(stream);
        }
        try (InputStream stream = resource(resourceLoader, properties.publicKeyLocation()).getInputStream()) {
            publicKey = RsaKeyConverters.x509().convert(stream);
        }
        validateKeyPair(publicKey, privateKey);
        String activeKid = PublicKeyFingerprint.kid(publicKey);
        if (properties.keyId() != null && !properties.keyId().isBlank()
                && !properties.keyId().equals(activeKid)) {
            LOGGER.warn("Configured cherry.auth.key-id is deprecated and ignored; kid is derived from public key content");
        }
        RSAKey current = rsaKey(activeKid, publicKey, privateKey);
        List<JWK> publicKeys = new ArrayList<>();
        publicKeys.add(current.toPublicJWK());
        Set<String> fingerprints = new HashSet<>();
        fingerprints.add(activeKid);
		for (RSAPublicKey published : managedVerificationKeys(
				resourceLoader, properties, activeKid)) {
			addVerificationKey(publicKeys, fingerprints, published);
        }
        for (var entry : properties.previousPublicKeys().entrySet()) {
			try (InputStream stream = resource(resourceLoader, entry.getValue()).getInputStream()) {
				addVerificationKey(publicKeys, fingerprints,
						RsaKeyConverters.x509().convert(stream));
			}
        }
        publicKeys.sort(Comparator.comparing(JWK::getKeyID));
        return new SigningKeys(current, new JWKSet(publicKeys), publicKey, privateKey);
    }

	private static List<RSAPublicKey> managedVerificationKeys(
			ResourceLoader loader, AuthProperties properties, String activeKid) throws IOException {
		if (!properties.privateKeyLocation().startsWith("file:")
				|| !properties.publicKeyLocation().startsWith("file:")) {
			return List.of();
		}
		Path privatePath = securePath(loader, properties.privateKeyLocation());
		Path publicPath = securePath(loader, properties.publicKeyLocation());
		if (!privatePath.getParent().equals(publicPath.getParent())) {
			throw new IllegalStateException("identity active private and public keys must share one key ring directory");
		}
		Path directory = publicPath.getParent();
		validateDirectoryPermissions(directory);
		Path statePath = directory.resolve("rotation.state");
		if (!Files.exists(statePath, LinkOption.NOFOLLOW_LINKS)) {
			LOGGER.warn("Identity key ring has no rotation.state; using one-cycle legacy active-key mode");
			return List.of();
		}
		validateOwnerOnlyFile(statePath, "identity rotation state");
		RotationState state = readRotationState(statePath);
		if (!activeKid.equals(state.activeKid())) {
			throw new IllegalStateException("identity rotation state does not match the active public key");
		}
		return switch (state.stage()) {
			case "active", "retired" -> List.of();
			case "prepared" -> {
				RSAPublicKey next = loadManagedPublicKey(
						directory.resolve("next-public.pem"), state.nextKid(), "prepared");
				validatePreparedPrivateKey(directory.resolve("next-private.pem"), next);
				yield List.of(next);
			}
			case "activated" -> List.of(loadManagedPublicKey(
					directory.resolve("previous-public-" + state.previousKid() + ".pem"),
					state.previousKid(), "previous"));
			default -> throw new IllegalStateException("identity rotation state has an invalid stage");
		};
	}

	private static RotationState readRotationState(Path path) throws IOException {
		Map<String, String> values = new LinkedHashMap<>();
		for (String line : Files.readAllLines(path)) {
			int separator = line.indexOf('=');
			if (separator <= 0 || values.putIfAbsent(
					line.substring(0, separator), line.substring(separator + 1)) != null) {
				throw new IllegalStateException("identity rotation state is malformed");
			}
		}
		String stage = values.get("stage");
		String activeKid = requiredKid(values.get("active_kid"), "active");
		String previousKid = optionalKid(values.get("previous_kid"), "previous");
		String nextKid = optionalKid(values.get("next_kid"), "next");
		if ("prepared".equals(stage) && nextKid == null) {
			throw new IllegalStateException("prepared identity rotation is missing next kid");
		}
		if ("activated".equals(stage) && previousKid == null) {
			throw new IllegalStateException("activated identity rotation is missing previous kid");
		}
		return new RotationState(stage, activeKid, previousKid, nextKid);
	}

	private static String requiredKid(String value, String name) {
		String kid = optionalKid(value, name);
		if (kid == null) {
			throw new IllegalStateException("identity rotation state is missing " + name + " kid");
		}
		return kid;
	}

	private static String optionalKid(String value, String name) {
		if (value == null || value.isBlank()) {
			return null;
		}
		if (!value.matches("^rsa-[A-Za-z0-9_-]{43}$")) {
			throw new IllegalStateException("identity rotation state has an invalid " + name + " kid");
		}
		return value;
	}

	private static RSAPublicKey loadManagedPublicKey(Path path, String expectedKid, String role)
			throws IOException {
		validateRegularFile(path, "identity " + role + " public key");
		RSAPublicKey key;
		try (InputStream stream = Files.newInputStream(path)) {
			key = RsaKeyConverters.x509().convert(stream);
		}
		validatePublicKey(key);
		if (!PublicKeyFingerprint.kid(key).equals(expectedKid)) {
			throw new IllegalStateException("identity " + role + " public key does not match rotation state");
		}
		return key;
	}

	private static void validatePreparedPrivateKey(Path path, RSAPublicKey publicKey) throws IOException {
		validateOwnerOnlyFile(path, "identity prepared private key");
		RSAPrivateKey privateKey;
		try (InputStream stream = Files.newInputStream(path)) {
			privateKey = RsaKeyConverters.pkcs8().convert(stream);
		}
		validateKeyPair(publicKey, privateKey);
	}

	private static void addVerificationKey(
			List<JWK> publicKeys, Set<String> fingerprints, RSAPublicKey key) {
		validatePublicKey(key);
		String kid = PublicKeyFingerprint.kid(key);
		if (!fingerprints.add(kid)) {
			throw new IllegalStateException("identity key ring contains a duplicate public key");
		}
		publicKeys.add(new RSAKey.Builder(key)
				.keyID(kid)
				.algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
				.build());
	}

    private static void validateKeyPair(RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        validatePublicKey(publicKey);
        if (!publicKey.getModulus().equals(privateKey.getModulus())) {
            throw new IllegalStateException("identity signing private/public key pair does not match");
        }
        if (privateKey instanceof RSAPrivateCrtKey crt
                && !publicKey.getPublicExponent().equals(crt.getPublicExponent())) {
            throw new IllegalStateException("identity signing private/public key pair does not match");
        }
    }

    private static void validatePublicKey(RSAPublicKey publicKey) {
		if (publicKey.getModulus().bitLength() < 3_072) {
			throw new IllegalStateException("identity RSA keys must be at least 3072 bits");
        }
    }

    private static void validatePrivateKeyFile(ResourceLoader loader, String location) throws IOException {
        if (!location.startsWith("file:")) {
            return;
        }
		Path path = securePath(loader, location);
		validateOwnerOnlyFile(path, "identity private key");
	}

	private static Path securePath(ResourceLoader loader, String location) throws IOException {
		Path path = resource(loader, location).getFile().toPath().toAbsolutePath().normalize();
		validateRegularFile(path, "identity key material");
		return path;
	}

	private static void validateRegularFile(Path path, String description) throws IOException {
		if (Files.isSymbolicLink(path) || !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
			throw new IllegalStateException(description + " must be a regular file");
		}
	}

	private static void validateOwnerOnlyFile(Path path, String description) throws IOException {
		validateRegularFile(path, description);
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path);
            if (permissions.contains(PosixFilePermission.GROUP_READ)
                    || permissions.contains(PosixFilePermission.GROUP_WRITE)
                    || permissions.contains(PosixFilePermission.GROUP_EXECUTE)
                    || permissions.contains(PosixFilePermission.OTHERS_READ)
                    || permissions.contains(PosixFilePermission.OTHERS_WRITE)
                    || permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
				throw new IllegalStateException(description + " permissions must be owner-only");
            }
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX stores rely on the deployment Secret mount ACL.
		}
	}

	private static void validateDirectoryPermissions(Path directory) throws IOException {
		if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) {
			throw new IllegalStateException("identity key ring directory must be a real directory");
		}
		try {
			Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(directory);
			if (permissions.contains(PosixFilePermission.GROUP_WRITE)
					|| permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
				throw new IllegalStateException("identity key ring directory must not be group/world writable");
			}
		} catch (UnsupportedOperationException ignored) {
			// Non-POSIX stores rely on the deployment Secret mount ACL.
		}
	}

	private record RotationState(String stage, String activeKid, String previousKid, String nextKid) {
	}

    private static RSAKey rsaKey(String keyId, RSAPublicKey publicKey, RSAPrivateKey privateKey) {
        return new RSAKey.Builder(publicKey)
                .privateKey(privateKey)
                .keyID(keyId)
                .algorithm(com.nimbusds.jose.JWSAlgorithm.RS256)
                .build();
    }

    private static boolean isProduction(Environment environment) {
        return environment.acceptsProfiles(Profiles.of("prod", "production"));
    }

    private static boolean unresolvedPlaceholder(String value) {
        return value != null && value.contains("${");
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
        requireText(properties.privateKeyLocation(), "private-key-location");
        requireText(properties.publicKeyLocation(), "public-key-location");
        requireDuration(properties.accessTokenTtl(), Duration.ofMinutes(5), Duration.ofHours(24), "access-token-ttl");
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
