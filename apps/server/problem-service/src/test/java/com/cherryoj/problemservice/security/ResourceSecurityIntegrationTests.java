package com.cherryoj.problemservice.security;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(ResourceSecurityIntegrationTests.SecurityProbeController.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ResourceSecurityIntegrationTests {

	private static final String USER_ID = "019c8e42-7f70-7000-8000-000000000001";
	private static final RSAKey KEY = newKey("key-1");
	private static final RSAKey UNKNOWN_KEY = newKey("key-unknown");
	private static final HttpServer JWKS = startJwksServer();

	private final MockMvc mockMvc;
	private final ResourceSecurityConfig configuration;
	private final IdentityProperties properties;

	@Autowired
	ResourceSecurityIntegrationTests(
			WebApplicationContext context,
			ResourceSecurityConfig configuration,
			IdentityProperties properties) {
		this.mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		this.configuration = configuration;
		this.properties = properties;
	}

	@DynamicPropertySource
	static void identityProperties(DynamicPropertyRegistry registry) {
		registry.add("cherry.identity.jwks-uri", () ->
				"http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
	}

	@AfterAll
	static void stopServer() {
		JWKS.stop(0);
	}

	@Test
	@Order(1)
	void acceptsVerifiedIdentityAndSeparatesUserFromAdmin() throws Exception {
		mockMvc.perform(get("/probe").header("X-User-Id", "attacker"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("INVALID_ACCESS_TOKEN"));

		mockMvc.perform(get("/probe")
				.header("Authorization", "Bearer " + token(KEY, "USER", Map.of()))
				.header("X-User-Id", "attacker"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.userId").value(USER_ID))
				.andExpect(jsonPath("$.roles[0]").value("USER"));

		mockMvc.perform(get("/internal/admin/probe")
				.header("Authorization", "Bearer " + token(KEY, "USER", Map.of())))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		mockMvc.perform(get("/internal/admin/probe")
				.header("Authorization", "Bearer " + token(KEY, "ADMIN", Map.of())))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.roles[0]").value("ADMIN"));
	}

	@Test
	@Order(2)
	void rejectsInvalidIssuerAudienceTimeRoleAndKid() {
		var decoder = configuration.jwtDecoder(properties);
		decoder.decode(token(KEY, "USER", Map.of()));
		assertThatThrownBy(() -> decoder.decode(token(KEY, "USER", Map.of("iss", "wrong"))))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(token(KEY, "USER", Map.of("aud", List.of("wrong")))))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(token(KEY, "USER", Map.of(
				"iat", Instant.now().minusSeconds(180), "exp", Instant.now().minusSeconds(90)))))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(token(KEY, "ROOT", Map.of())))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(token(KEY, "USER", Map.of("pwd", true))))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(token(UNKNOWN_KEY, "USER", Map.of())))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(tokenWithoutKid(KEY)))
				.isInstanceOf(JwtException.class);
		assertThatThrownBy(() -> decoder.decode(hs256Token()))
				.isInstanceOf(JwtException.class);
	}

	@Test
	@Order(3)
	void cachedKnownKeyContinuesAfterJwksOutageAndUnknownKeyFailsClosed() throws Exception {
		var decoder = configuration.jwtDecoder(properties);
		decoder.decode(token(KEY, "USER", Map.of()));
		JWKS.stop(0);
		decoder.decode(token(KEY, "USER", Map.of()));
		assertThatThrownBy(() -> decoder.decode(token(UNKNOWN_KEY, "USER", Map.of())))
				.isInstanceOf(JwtException.class);
		mockMvc.perform(get("/probe")
				.header("Authorization", "Bearer " + token(KEY, "USER", Map.of())))
				.andExpect(status().isOk());
		mockMvc.perform(get("/probe")
				.header("Authorization", "Bearer " + token(UNKNOWN_KEY, "USER", Map.of())))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.code").value("IDENTITY_KEY_UNAVAILABLE"));
	}

	private static String token(RSAKey key, String role, Map<String, Object> overrides) {
		Instant now = Instant.now();
		JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
				.issuer("cherry-oj-user-service")
				.audience("cherry-oj-internal")
				.subject(USER_ID)
				.issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(120)))
				.jwtID("jti-1")
				.claim("roles", List.of(role))
				.claim("sv", 0)
				.claim("pwd", false);
		overrides.forEach((name, value) -> {
			if ("iss".equals(name)) claims.issuer((String) value);
			else if ("aud".equals(name)) claims.audience((List<String>) value);
			else if ("iat".equals(name)) claims.issueTime(Date.from((Instant) value));
			else if ("exp".equals(name)) claims.expirationTime(Date.from((Instant) value));
			else claims.claim(name, value);
		});
		return sign(key, new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims.build());
	}

	private static String tokenWithoutKid(RSAKey key) {
		Instant now = Instant.now();
		JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("cherry-oj-user-service")
				.audience("cherry-oj-internal").subject(USER_ID).issueTime(Date.from(now))
				.expirationTime(Date.from(now.plusSeconds(120))).jwtID("jti-2")
				.claim("roles", List.of("USER")).claim("sv", 0).claim("pwd", false).build();
		return sign(key, new JWSHeader.Builder(JWSAlgorithm.RS256).build(), claims);
	}

	private static String hs256Token() {
		try {
			Instant now = Instant.now();
			JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("cherry-oj-user-service")
					.audience("cherry-oj-internal").subject(USER_ID).issueTime(Date.from(now))
					.expirationTime(Date.from(now.plusSeconds(120))).jwtID("jti-hs")
					.claim("roles", List.of("ADMIN")).claim("sv", 0).claim("pwd", false).build();
			SignedJWT jwt = new SignedJWT(
					new JWSHeader.Builder(JWSAlgorithm.HS256).keyID("key-1").build(), claims);
			jwt.sign(new MACSigner("01234567890123456789012345678901"));
			return jwt.serialize();
		}
		catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	private static String sign(RSAKey key, JWSHeader header, JWTClaimsSet claims) {
		try {
			SignedJWT jwt = new SignedJWT(header, claims);
			jwt.sign(new RSASSASigner(key));
			return jwt.serialize();
		}
		catch (Exception error) {
			throw new IllegalStateException(error);
		}
	}

	private static RSAKey newKey(String keyId) {
		try { return new RSAKeyGenerator(2_048).keyID(keyId).algorithm(JWSAlgorithm.RS256).generate(); }
		catch (Exception error) { throw new IllegalStateException(error); }
	}

	private static HttpServer startJwksServer() {
		try {
			HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			server.createContext("/jwks", exchange -> {
				byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
				exchange.getResponseHeaders().set("Content-Type", MediaType.APPLICATION_JSON_VALUE);
				exchange.sendResponseHeaders(200, body.length);
				exchange.getResponseBody().write(body);
				exchange.close();
			});
			server.start();
			return server;
		}
		catch (IOException error) {
			throw new IllegalStateException(error);
		}
	}

	@RestController
	static class SecurityProbeController {
		@GetMapping({"/probe", "/internal/admin/probe"})
		CurrentIdentity identity(Authentication authentication) {
			return CurrentIdentity.from((JwtAuthenticationToken) authentication);
		}
	}
}
