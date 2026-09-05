package com.cherryoj.userservice.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
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
import com.cherryoj.identitysecurity.PublicKeyFingerprint;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class TokenConfigTests {

    @TempDir
    Path tempDirectory;

    @Test
    void reloadsTheSamePersistentKeyWithContentDerivedKid() throws Exception {
        TokenConfig config = new TokenConfig();
        RSAKey key = key("operator-value-is-ignored");
        Path privateKey = writePrivateKey("active-private.pem", key);
        Path publicKey = writePublicKey("active-public.pem", key);
        var properties = properties("legacy-kid", privateKey.toUri().toString(), publicKey.toUri().toString());

        SigningKeys first = config.signingKeys(
                properties, new DefaultResourceLoader(), new MockEnvironment());
        SigningKeys second = config.signingKeys(
                properties, new DefaultResourceLoader(), new MockEnvironment());

        assertThat(first.current().getKeyID()).startsWith("rsa-").hasSize(47);
        assertThat(first.current().getAlgorithm()).isEqualTo(JWSAlgorithm.RS256);
        assertThat(first.publicJwkSet().getKeys()).containsExactly(first.current().toPublicJWK());
        assertThat(first.privateKey().getModulus()).isEqualTo(second.privateKey().getModulus());
        assertThat(first.activeKid()).isEqualTo(second.activeKid());
    }

    @Test
    void rejectsUnresolvedSigningKeysInProduction() {
        var properties = properties("", "${CHERRY_AUTH_PRIVATE_KEY_LOCATION}",
                "${CHERRY_AUTH_PUBLIC_KEY_LOCATION}");
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
                .hasMessage("Production requires explicit cherry.auth private-key-location and public-key-location");
    }

    @Test
    void rejectsMismatchedDuplicateAndOverexposedKeyMaterial() throws Exception {
        RSAKey first = key("first");
        RSAKey second = key("second");
        Path privateKey = writePrivateKey("first-private.pem", first);
        Path wrongPublicKey = writePublicKey("second-public.pem", second);
        assertThatThrownBy(() -> new TokenConfig().signingKeys(
                properties("", privateKey.toUri().toString(), wrongPublicKey.toUri().toString()),
                new DefaultResourceLoader(), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");

        Path firstPublicKey = writePublicKey("first-public.pem", first);
        AuthProperties duplicate = new AuthProperties(
                "server", "cherry-oj-user-service", "cherry-oj-internal", "",
                privateKey.toUri().toString(), firstPublicKey.toUri().toString(),
                Map.of("duplicate", firstPublicKey.toUri().toString()), Duration.ofHours(2),
                Duration.ofSeconds(30), "fixed-absolute", 2_592_000);
        assertThatThrownBy(() -> new TokenConfig().signingKeys(
                duplicate, new DefaultResourceLoader(), new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate public key");

        try {
            Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-r--r--"));
            assertThatThrownBy(() -> new TokenConfig().signingKeys(
                    properties("", privateKey.toUri().toString(), firstPublicKey.toUri().toString()),
                    new DefaultResourceLoader(), new MockEnvironment()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("owner-only");
        } catch (UnsupportedOperationException ignored) {
        }
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
                new SigningKeys(previous, new JWKSet(previous.toPublicJWK()),
                        previous.toRSAPublicKey(), previous.toRSAPrivateKey()),
                properties("previous-key"),
                Clock.fixed(now, ZoneOffset.UTC));

        String token = previousSigner.issue(account(now)).value();
        var decoded = new TokenConfig().jwtDecoder(keys, properties).decode(token);

        assertThat(decoded.getHeaders().get("kid")).isEqualTo("previous-key");
        assertThat(decoded.getSubject()).isEqualTo("019c8e42-7f70-7000-8000-000000000001");
    }

	@Test
	void rotationStateAutomaticallyPublishesPreparedAndPreviousKeys() throws Exception {
		RSAKey first = key("first");
		RSAKey second = key("second");
		Path privateKey = writePrivateKey("active-private.pem", first);
		Path publicKey = writePublicKey("active-public.pem", first);
		writePrivateKey("next-private.pem", second);
		writePublicKey("next-public.pem", second);
		String firstKid = PublicKeyFingerprint.kid(first.toRSAPublicKey());
		String secondKid = PublicKeyFingerprint.kid(second.toRSAPublicKey());
		writeState("prepared", firstKid, "", secondKid);

		SigningKeys prepared = new TokenConfig().signingKeys(
				properties("", privateKey.toUri().toString(), publicKey.toUri().toString()),
				new DefaultResourceLoader(), new MockEnvironment());

		assertThat(prepared.publicJwkSet().getKeys()).extracting(JWK::getKeyID)
				.containsExactlyInAnyOrder(firstKid, secondKid);

		Path previous = tempDirectory.resolve("previous-public-" + firstKid + ".pem");
		Files.move(publicKey, previous);
		writePrivateKey("active-private.pem", second);
		Path activePublic = writePublicKey("active-public.pem", second);
		Files.deleteIfExists(tempDirectory.resolve("next-private.pem"));
		Files.deleteIfExists(tempDirectory.resolve("next-public.pem"));
		writeState("activated", secondKid, firstKid, "");

		SigningKeys activated = new TokenConfig().signingKeys(
				properties("", tempDirectory.resolve("active-private.pem").toUri().toString(),
						activePublic.toUri().toString()),
				new DefaultResourceLoader(), new MockEnvironment());
		assertThat(activated.publicJwkSet().getKeys()).extracting(JWK::getKeyID)
				.containsExactlyInAnyOrder(firstKid, secondKid);
	}

	@Test
	void rejectsKeysBelowThe3072BitSecurityFloor() throws Exception {
		RSAKey weak = key("weak", 2_048);
		Path privateKey = writePrivateKey("weak-private.pem", weak);
		Path publicKey = writePublicKey("weak-public.pem", weak);

		assertThatThrownBy(() -> new TokenConfig().signingKeys(
				properties("", privateKey.toUri().toString(), publicKey.toUri().toString()),
				new DefaultResourceLoader(), new MockEnvironment()))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("at least 3072 bits");
	}

    private static RSAKey key(String keyId) throws Exception {
		return key(keyId, 3_072);
	}

	private static RSAKey key(String keyId, int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
		generator.initialize(bits);
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
                Duration.ofHours(2),
                Duration.ofSeconds(30),
                "fixed-absolute",
                2_592_000);
    }

    private Path writePrivateKey(String name, RSAKey key) throws Exception {
        Path path = tempDirectory.resolve(name);
        writePem(path, "PRIVATE KEY", key.toRSAPrivateKey().getEncoded());
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString("rw-------"));
        } catch (UnsupportedOperationException ignored) {
        }
        return path;
    }

    private Path writePublicKey(String name, RSAKey key) throws Exception {
        Path path = tempDirectory.resolve(name);
        writePem(path, "PUBLIC KEY", key.toRSAPublicKey().getEncoded());
        return path;
    }

    private static void writePem(Path path, String type, byte[] encoded) throws Exception {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII))
                .encodeToString(encoded);
        Files.writeString(path, "-----BEGIN " + type + "-----\n" + body
                + "\n-----END " + type + "-----\n", StandardCharsets.US_ASCII);
    }

	private void writeState(String stage, String activeKid, String previousKid, String nextKid)
			throws Exception {
		Path state = tempDirectory.resolve("rotation.state");
		Files.writeString(state, """
				stage=%s
				active_kid=%s
				previous_kid=%s
				next_kid=%s
				prepared_at_epoch=1
				activated_at_epoch=1
				""".formatted(stage, activeKid, previousKid, nextKid), StandardCharsets.US_ASCII);
		try {
			Files.setPosixFilePermissions(state, PosixFilePermissions.fromString("rw-------"));
		} catch (UnsupportedOperationException ignored) {
		}
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
