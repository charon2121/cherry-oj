package com.cherryoj.gatewayservice.auth;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.gateway")
public record GatewayAuthProperties(
		URI userServiceBaseUrl,
		Duration userServiceTimeout,
		long sessionIdleTimeoutSeconds,
		long sessionAbsoluteTimeoutSeconds,
		String sessionRefreshIdleOnActivity,
		Duration tokenRefreshSkew,
		List<String> trustedOrigins,
		int loginRateLimitPerMinute) {

	public GatewayAuthProperties {
		if (userServiceBaseUrl == null || userServiceBaseUrl.getHost() == null) {
			throw new IllegalArgumentException("cherry.gateway.user-service-base-url must be an absolute URI");
		}
		if (userServiceTimeout == null || userServiceTimeout.isNegative() || userServiceTimeout.isZero()
				|| userServiceTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
			throw new IllegalArgumentException("cherry.gateway.user-service-timeout must be within (0, 30s]");
		}
		if (sessionIdleTimeoutSeconds < 300 || sessionIdleTimeoutSeconds > 7_200) {
			throw new IllegalArgumentException(
					"cherry.gateway.session-idle-timeout-seconds must be within [300, 7200]");
		}
		if (sessionAbsoluteTimeoutSeconds < 3_600 || sessionAbsoluteTimeoutSeconds > 604_800) {
			throw new IllegalArgumentException(
					"cherry.gateway.session-absolute-timeout-seconds must be within [3600, 604800]");
		}
		if (sessionIdleTimeoutSeconds > sessionAbsoluteTimeoutSeconds) {
			throw new IllegalArgumentException(
					"cherry.gateway.session-idle-timeout-seconds must not exceed session-absolute-timeout-seconds");
		}
		if (!"true".equals(sessionRefreshIdleOnActivity)
				&& !"false".equals(sessionRefreshIdleOnActivity)) {
			throw new IllegalArgumentException(
					"cherry.gateway.session-refresh-idle-on-activity must be true or false");
		}
		if (tokenRefreshSkew == null || tokenRefreshSkew.isNegative()
				|| tokenRefreshSkew.compareTo(Duration.ofSeconds(60)) > 0) {
			throw new IllegalArgumentException("cherry.gateway.token-refresh-skew must be within [0, 60s]");
		}
		trustedOrigins = trustedOrigins == null ? List.of() : List.copyOf(trustedOrigins);
		if (trustedOrigins.stream().anyMatch(origin -> origin == null || origin.isBlank() || origin.contains("*"))) {
			throw new IllegalArgumentException("cherry.gateway.trusted-origins must contain exact origins");
		}
		if (loginRateLimitPerMinute < 1 || loginRateLimitPerMinute > 1_000) {
			throw new IllegalArgumentException("cherry.gateway.login-rate-limit-per-minute must be within [1, 1000]");
		}
	}

	Duration sessionIdleTimeout() {
		return Duration.ofSeconds(sessionIdleTimeoutSeconds);
	}

	Duration sessionAbsoluteTimeout() {
		return Duration.ofSeconds(sessionAbsoluteTimeoutSeconds);
	}

	boolean refreshIdleOnActivity() {
		return Boolean.parseBoolean(sessionRefreshIdleOnActivity);
	}
}
