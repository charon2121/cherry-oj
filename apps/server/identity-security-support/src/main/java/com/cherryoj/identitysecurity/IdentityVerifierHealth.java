package com.cherryoj.identitysecurity;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

public final class IdentityVerifierHealth implements HealthIndicator {

	private final IdentityVerifierProperties properties;
	private final RestTemplate restTemplate;
	private final AtomicReference<Instant> lastKeyFailure = new AtomicReference<>();
	private final AtomicReference<Instant> lastSuccessfulVerification = new AtomicReference<>();
	private final AtomicReference<Instant> lastSuccessfulProbe = new AtomicReference<>();

	public IdentityVerifierHealth(IdentityVerifierProperties properties) {
		this(properties, restTemplate(properties));
	}

	IdentityVerifierHealth(IdentityVerifierProperties properties, RestTemplate restTemplate) {
		this.properties = properties;
		this.restTemplate = restTemplate;
	}

	void verified() {
		lastSuccessfulVerification.set(Instant.now());
		lastKeyFailure.set(null);
	}

	void keyServiceUnavailable() {
		lastKeyFailure.set(Instant.now());
	}

	@Override
	public Health health() {
		try {
			ProbeResult probe = probeTrustState();
			Instant now = Instant.now();
			lastSuccessfulProbe.set(now);
			lastKeyFailure.set(null);
			Health.Builder builder = Health.up()
					.withDetail("activeKid", probe.activeKid())
					.withDetail("publishedKeyCount", probe.publishedKeyCount())
					.withDetail("publishedKids", probe.publishedKids())
					.withDetail("lastSuccessfulProbeAt", now.toString());
			addLastVerification(builder);
			return builder.build();
		}
		catch (Exception error) {
			keyServiceUnavailable();
			Health.Builder builder = Health.down()
					.withDetail("reason", "identity_trust_state_unavailable")
					.withDetail("lastFailureAt", lastKeyFailure.get().toString());
			Instant previousProbe = lastSuccessfulProbe.get();
			if (previousProbe != null) {
				builder.withDetail("lastSuccessfulProbeAt", previousProbe.toString());
			}
			addLastVerification(builder);
			return builder.build();
		}
	}

	private ProbeResult probeTrustState() throws Exception {
		String jwksJson = restTemplate.getForObject(properties.jwksUri(), String.class);
		IdentityMetadata metadata = restTemplate.getForObject(properties.metadataUri(), IdentityMetadata.class);
		if (jwksJson == null || metadata == null) {
			throw new IllegalStateException("identity trust response is empty");
		}
		List<JWK> keys = JWKSet.parse(jwksJson).getKeys();
		Set<String> kids = new HashSet<>();
		for (JWK key : keys) {
			if (key.isPrivate() || !(key instanceof RSAKey rsaKey)
					|| rsaKey.size() < 3_072
					|| !JWSAlgorithm.RS256.equals(key.getAlgorithm())
					|| key.getKeyID() == null || key.getKeyID().isBlank()
					|| !kids.add(key.getKeyID())) {
				throw new IllegalStateException("identity JWKS violates the verifier contract");
			}
		}
		Set<String> published = metadata.publishedKids() == null
				? Set.of()
				: new HashSet<>(metadata.publishedKids());
		if (metadata.activeKid() == null || !kids.contains(metadata.activeKid())
				|| !kids.equals(published)
				|| !"RS256".equals(metadata.algorithm())
				|| metadata.accessTokenTtlSeconds() != properties.accessTokenTtl().toSeconds()) {
			throw new IllegalStateException("identity metadata does not match the published key set");
		}
		return new ProbeResult(metadata.activeKid(), kids.size(), kids.stream().sorted().toList());
	}

	private void addLastVerification(Health.Builder builder) {
		Instant success = lastSuccessfulVerification.get();
		if (success != null) {
			builder.withDetail("lastSuccessfulVerificationAt", success.toString());
		}
	}

	private static RestTemplate restTemplate(IdentityVerifierProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		return new RestTemplate(requestFactory);
	}

	private record IdentityMetadata(String activeKid, List<String> publishedKids, String algorithm,
			long accessTokenTtlSeconds, String generation) {
	}

	private record ProbeResult(String activeKid, int publishedKeyCount, List<String> publishedKids) {
	}
}
