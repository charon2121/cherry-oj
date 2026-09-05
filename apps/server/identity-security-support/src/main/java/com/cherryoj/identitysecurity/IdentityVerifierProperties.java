package com.cherryoj.identitysecurity;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.identity")
public record IdentityVerifierProperties(
		String issuer,
		String audience,
		URI jwksUri,
		URI metadataUri,
		Duration accessTokenTtl,
		Duration clockSkew,
		Duration connectTimeout,
		Duration readTimeout) {

	public IdentityVerifierProperties {
		if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
			throw new IllegalArgumentException("identity issuer and audience must be configured");
		}
		validateHttpUri(jwksUri, "jwks-uri");
		validateHttpUri(metadataUri, "metadata-uri");
		accessTokenTtl = bounded(accessTokenTtl, Duration.ofMinutes(5), Duration.ofHours(24), "access-token-ttl");
		clockSkew = bounded(clockSkew, Duration.ZERO, Duration.ofSeconds(60), "clock-skew");
		connectTimeout = bounded(connectTimeout, Duration.ofMillis(100), Duration.ofSeconds(10), "connect-timeout");
		readTimeout = bounded(readTimeout, Duration.ofMillis(100), Duration.ofSeconds(10), "read-timeout");
	}

	private static void validateHttpUri(URI value, String name) {
		if (value == null || !value.isAbsolute() || value.getHost() == null) {
			throw new IllegalArgumentException("identity " + name + " must be an absolute HTTP(S) URI");
		}
		if (!"http".equalsIgnoreCase(value.getScheme()) && !"https".equalsIgnoreCase(value.getScheme())) {
			throw new IllegalArgumentException("identity " + name + " must use HTTP or HTTPS");
		}
	}

	private static Duration bounded(Duration value, Duration minimum, Duration maximum, String name) {
		if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0) {
			throw new IllegalArgumentException("identity " + name + " must be within [" + minimum + ", " + maximum + "]");
		}
		return value;
	}
}
