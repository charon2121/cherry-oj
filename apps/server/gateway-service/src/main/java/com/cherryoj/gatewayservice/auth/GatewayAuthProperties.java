package com.cherryoj.gatewayservice.auth;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.gateway")
public record GatewayAuthProperties(
		URI userServiceBaseUrl,
		Duration userServiceTimeout,
		String sessionLifetimePolicy,
		long sessionAbsoluteTimeoutSeconds,
		Duration accessTokenTtl,
		Duration tokenRefreshAhead,
		List<String> trustedOrigins,
		int loginRateLimitPerMinute) {

	public GatewayAuthProperties {
		validateIdentityServiceUri(userServiceBaseUrl);
		if (userServiceTimeout == null || userServiceTimeout.isNegative() || userServiceTimeout.isZero()
				|| userServiceTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
			throw new IllegalArgumentException("cherry.gateway.user-service-timeout must be within (0, 30s]");
		}
		if (!"fixed-absolute".equals(sessionLifetimePolicy)) {
			throw new IllegalArgumentException(
					"cherry.gateway.session-lifetime-policy must be fixed-absolute");
		}
		if (sessionAbsoluteTimeoutSeconds < 86_400 || sessionAbsoluteTimeoutSeconds > 7_776_000) {
			throw new IllegalArgumentException(
					"cherry.gateway.session-absolute-timeout-seconds must be within [86400, 7776000]");
		}
		if (accessTokenTtl == null || accessTokenTtl.compareTo(Duration.ofMinutes(1)) < 0
				|| accessTokenTtl.compareTo(Duration.ofHours(24)) > 0) {
			throw new IllegalArgumentException("cherry.gateway.access-token-ttl must be within [1m, 24h]");
		}
		if (tokenRefreshAhead == null || tokenRefreshAhead.isNegative()
				|| tokenRefreshAhead.isZero() || tokenRefreshAhead.compareTo(accessTokenTtl) >= 0) {
			throw new IllegalArgumentException(
					"cherry.gateway.token-refresh-ahead must be within (0, access-token-ttl)");
		}
		trustedOrigins = trustedOrigins == null ? List.of() : List.copyOf(trustedOrigins);
		if (trustedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank() || origin.contains("*"))) {
			throw new IllegalArgumentException("cherry.gateway.trusted-origins must contain exact origins");
		}
		if (loginRateLimitPerMinute < 1 || loginRateLimitPerMinute > 1_000) {
			throw new IllegalArgumentException("cherry.gateway.login-rate-limit-per-minute must be within [1, 1000]");
		}
	}

	private static void validateIdentityServiceUri(URI value) {
		if (value == null || !value.isAbsolute() || value.getHost() == null) {
			throw new IllegalArgumentException("cherry.gateway.user-service-base-url must be an absolute URI");
		}
		if (!"https".equalsIgnoreCase(value.getScheme())
				&& !"http".equalsIgnoreCase(value.getScheme())) {
			throw new IllegalArgumentException("cherry.gateway.user-service-base-url must use HTTP or HTTPS");
		}
	}

	Duration sessionAbsoluteTimeout() {
		return Duration.ofSeconds(sessionAbsoluteTimeoutSeconds);
	}
}
