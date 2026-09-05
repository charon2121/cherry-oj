package com.cherryoj.identitysecurity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.net.URI;
import java.time.Duration;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

class IdentityVerifierHealthTests {

	@Test
	void readinessActivelyProvesJwksAndMetadataDescribeTheSameTrustState() throws Exception {
		RSAKey key = new RSAKeyGenerator(3_072).keyID("active-key")
				.algorithm(JWSAlgorithm.RS256).generate();
		RestTemplate client = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
		server.expect(requestTo("https://identity.example/jwks"))
				.andRespond(withSuccess(new JWKSet(key.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://identity.example/metadata"))
				.andRespond(withSuccess(metadata("active-key", 7_200), MediaType.APPLICATION_JSON));
		IdentityVerifierHealth health = new IdentityVerifierHealth(properties(), client);

		var result = health.health();
		assertThat(result.getStatus()).isEqualTo(Status.UP);
		assertThat(result.getDetails())
					.containsEntry("activeKid", "active-key")
					.containsEntry("publishedKeyCount", 1)
					.containsEntry("publishedKids", java.util.List.of("active-key"));
		server.verify();
	}

	@Test
	void readinessFailsClosedWhenMetadataPolicyDoesNotMatchTheVerifier() throws Exception {
		RSAKey key = new RSAKeyGenerator(3_072).keyID("active-key")
				.algorithm(JWSAlgorithm.RS256).generate();
		RestTemplate client = new RestTemplate();
		MockRestServiceServer server = MockRestServiceServer.bindTo(client).build();
		server.expect(requestTo("https://identity.example/jwks"))
				.andRespond(withSuccess(new JWKSet(key.toPublicJWK()).toString(), MediaType.APPLICATION_JSON));
		server.expect(requestTo("https://identity.example/metadata"))
				.andRespond(withSuccess(metadata("active-key", 7_199), MediaType.APPLICATION_JSON));
		IdentityVerifierHealth health = new IdentityVerifierHealth(properties(), client);

		var result = health.health();
		assertThat(result.getStatus()).isEqualTo(Status.DOWN);
		assertThat(result.getDetails())
					.containsEntry("reason", "identity_trust_state_unavailable")
					.doesNotContainKeys("error", "exception", "token");
		server.verify();
	}

	private static IdentityVerifierProperties properties() {
		return new IdentityVerifierProperties(
				"cherry-oj-user-service", "cherry-oj-internal",
				URI.create("https://identity.example/jwks"),
				URI.create("https://identity.example/metadata"), Duration.ofHours(2),
				Duration.ofSeconds(30), Duration.ofSeconds(2), Duration.ofSeconds(2));
	}

	private static String metadata(String activeKid, long ttlSeconds) {
		return """
				{"activeKid":"%s","publishedKids":["%s"],"algorithm":"RS256",\
				"accessTokenTtlSeconds":%d,"generation":"test"}
				""".formatted(activeKid, activeKid, ttlSeconds);
	}
}
