package com.cherryoj.judgingservice.security;

import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.identity")
record IdentityProperties(String issuer, String audience, URI jwksUri, Duration clockSkew) {
	IdentityProperties {
		if (issuer == null || issuer.isBlank() || audience == null || audience.isBlank()) {
			throw new IllegalArgumentException("identity issuer and audience must be configured");
		}
		if (jwksUri == null || jwksUri.getScheme() == null || jwksUri.getHost() == null) {
			throw new IllegalArgumentException("identity jwks-uri must be an absolute URI");
		}
		if (clockSkew == null || clockSkew.isNegative() || clockSkew.compareTo(Duration.ofSeconds(60)) > 0) {
			throw new IllegalArgumentException("identity clock-skew must be within [0, 60s]");
		}
	}
}
