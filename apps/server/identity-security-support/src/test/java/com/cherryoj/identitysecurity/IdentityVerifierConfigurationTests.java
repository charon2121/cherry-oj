package com.cherryoj.identitysecurity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class IdentityVerifierConfigurationTests {

	private static final String ISSUER = "cherry-oj-user-service";
	private static final String AUDIENCE = "cherry-oj-internal";
	private static final String USER_ID = "019c8e42-7f70-7000-8000-000000000001";

	@Test
	void usesExpirationForLifetimeWhileStillRequiringIssuedAt() throws Exception {
		RSAKey key = new RSAKeyGenerator(2_048).keyID("identity-key").generate();
		HttpServer jwks = startJwksServer(key);
		try {
			URI baseUri = URI.create("http://127.0.0.1:" + jwks.getAddress().getPort());
			IdentityVerifierProperties properties = new IdentityVerifierProperties(
					ISSUER, AUDIENCE, baseUri.resolve("/jwks"), baseUri.resolve("/metadata"),
					Duration.ofHours(2), Duration.ofSeconds(30),
					Duration.ofSeconds(2), Duration.ofSeconds(2));
			IdentityVerifierConfiguration configuration = new IdentityVerifierConfiguration();
			var decoder = configuration.identityJwtDecoder(
					properties, configuration.identityVerifierHealth(properties));
			Instant now = Instant.now();

			assertThatCode(() -> decoder.decode(token(
					key, now.minus(Duration.ofMinutes(10)), now.plus(Duration.ofHours(1)), null)))
					.as("a token remains valid after its clock-skew window until exp")
					.doesNotThrowAnyException();
			assertThatThrownBy(() -> decoder.decode(token(
					key, null, now.plus(Duration.ofHours(1)), null)))
					.as("iat remains required")
					.isInstanceOf(JwtException.class);
			assertThatThrownBy(() -> decoder.decode(token(
					key, now.minus(Duration.ofMinutes(10)), now.minus(Duration.ofMinutes(1)), null)))
					.as("exp remains the hard lifetime deadline")
					.isInstanceOf(JwtException.class);
			assertThatThrownBy(() -> decoder.decode(token(
					key, now, now.plus(Duration.ofHours(1)), now.plus(Duration.ofMinutes(2)))))
					.as("nbf still rejects tokens that are not valid yet")
					.isInstanceOf(JwtException.class);
		}
		finally {
			jwks.stop(0);
		}
	}

	private static HttpServer startJwksServer(RSAKey key) throws Exception {
		HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/jwks", exchange -> {
			byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
			exchange.getResponseHeaders().set("Content-Type", "application/json");
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		server.start();
		return server;
	}

	private static String token(
			RSAKey key, Instant issuedAt, Instant expiresAt, Instant notBefore) {
		try {
			JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
					.issuer(ISSUER)
					.audience(AUDIENCE)
					.subject(USER_ID)
					.expirationTime(Date.from(expiresAt))
					.jwtID("jti")
					.claim("roles", List.of("ADMIN"))
					.claim("sv", 0)
					.claim("pwd", false);
			if (issuedAt != null) {
				claims.issueTime(Date.from(issuedAt));
			}
			if (notBefore != null) {
				claims.notBeforeTime(Date.from(notBefore));
			}
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(),
					claims.build());
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		}
		catch (Exception error) {
			throw new IllegalStateException("Could not sign test token", error);
		}
	}
}
