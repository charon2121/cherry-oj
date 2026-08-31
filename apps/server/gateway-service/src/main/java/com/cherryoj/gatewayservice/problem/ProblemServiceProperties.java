package com.cherryoj.gatewayservice.problem;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("cherry.gateway.problem-service")
public record ProblemServiceProperties(
		URI baseUrl,
		Duration metadataTimeout,
		Duration streamingTimeout,
		int maxJsonBytes) {

	public ProblemServiceProperties {
		if (baseUrl == null || baseUrl.getScheme() == null || baseUrl.getHost() == null
				|| !("http".equals(baseUrl.getScheme()) || "https".equals(baseUrl.getScheme()))) {
			throw new IllegalArgumentException(
					"cherry.gateway.problem-service.base-url must be an absolute HTTP(S) URI");
		}
		if (metadataTimeout == null || metadataTimeout.isZero() || metadataTimeout.isNegative()
				|| metadataTimeout.compareTo(Duration.ofSeconds(30)) > 0) {
			throw new IllegalArgumentException(
					"cherry.gateway.problem-service.metadata-timeout must be within (0, 30s]");
		}
		if (streamingTimeout == null || streamingTimeout.isZero() || streamingTimeout.isNegative()
				|| streamingTimeout.compareTo(Duration.ofMinutes(10)) > 0) {
			throw new IllegalArgumentException(
					"cherry.gateway.problem-service.streaming-timeout must be within (0, 10m]");
		}
		if (maxJsonBytes < 262_144 || maxJsonBytes > 33_554_432) {
			throw new IllegalArgumentException(
					"cherry.gateway.problem-service.max-json-bytes must be within [256KiB, 32MiB]");
		}
	}
}
