package com.cherryoj.judgingservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.cherryoj.identitysecurity.IdentityVerifierConfiguration;
import com.cherryoj.identitysecurity.IdentityVerifierProperties;
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

class ResourceSecurityConfigTests {

	@Test
	void verifiesSignatureAndIssuerInsteadOfTrustingGatewayHeaders() throws Exception {
		RSAKey key = new RSAKeyGenerator(2_048).keyID("judging-key").generate();
		HttpServer jwks = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		jwks.createContext("/jwks", exchange -> {
			byte[] body = new JWKSet(key.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
			exchange.sendResponseHeaders(200, body.length);
			exchange.getResponseBody().write(body);
			exchange.close();
		});
		jwks.start();
		try {
			IdentityVerifierProperties properties = new IdentityVerifierProperties(
					"cherry-oj-user-service", "cherry-oj-internal",
					java.net.URI.create("http://127.0.0.1:" + jwks.getAddress().getPort() + "/jwks"),
					java.net.URI.create("http://127.0.0.1:" + jwks.getAddress().getPort() + "/metadata"),
					Duration.ofHours(2),
					Duration.ofSeconds(30), Duration.ofSeconds(2), Duration.ofSeconds(2));
			var configuration = new IdentityVerifierConfiguration();
			var decoder = configuration.identityJwtDecoder(properties, configuration.identityVerifierHealth(properties));
			assertThat(decoder.decode(token(key, "cherry-oj-user-service")).getSubject())
					.isEqualTo("019c8e42-7f70-7000-8000-000000000001");
			assertThatThrownBy(() -> decoder.decode(token(key, "wrong-issuer")))
					.isInstanceOf(JwtException.class);
			assertThatThrownBy(() -> decoder.decode(token(key, "cherry-oj-user-service", true)))
					.isInstanceOf(JwtException.class);
		}
		finally {
			jwks.stop(0);
		}
	}

	private static String token(RSAKey key, String issuer) throws Exception {
		return token(key, issuer, false);
	}

	private static String token(RSAKey key, String issuer, boolean passwordChangeRequired) throws Exception {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer(issuer).audience("cherry-oj-internal")
				.subject("019c8e42-7f70-7000-8000-000000000001")
				.issueTime(Date.from(now)).expirationTime(Date.from(now.plusSeconds(120))).jwtID("jti")
				.claim("roles", List.of("ADMIN")).claim("sv", 0)
				.claim("pwd", passwordChangeRequired).build();
		SignedJWT jwt = new SignedJWT(
				new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims);
		jwt.sign(new RSASSASigner(key));
		return jwt.serialize();
	}
}
