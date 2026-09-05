package com.cherryoj.gatewayservice.auth;

import java.util.UUID;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.ReactiveHealthIndicator;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/** Readiness proof that the configured issuer is reachable and agrees on token lifetime. */
@Component("gatewayIdentity")
final class GatewayIdentityHealthIndicator implements ReactiveHealthIndicator {

	private final UserServiceClient userService;
	private final GatewayAuthProperties properties;

	GatewayIdentityHealthIndicator(UserServiceClient userService, GatewayAuthProperties properties) {
		this.userService = userService;
		this.properties = properties;
	}

	@Override
	public Mono<Health> health() {
		return userService.identityMetadata("req_" + UUID.randomUUID().toString().replace("-", ""))
				.map(metadata -> valid(metadata)
						? Health.up().withDetail("publishedKeyCount", metadata.publishedKids().size()).build()
						: Health.down().withDetail("reason", "identity-metadata-mismatch").build())
				.onErrorReturn(Health.down()
						.withDetail("reason", "identity-metadata-unavailable").build());
	}

	private boolean valid(UserServiceClient.IdentityMetadata metadata) {
		return metadata != null
				&& "RS256".equals(metadata.algorithm())
				&& metadata.activeKid() != null
				&& metadata.publishedKids() != null
				&& metadata.publishedKids().contains(metadata.activeKid())
				&& metadata.accessTokenTtlSeconds() == properties.accessTokenTtl().toSeconds()
				&& metadata.generation() != null
				&& !metadata.generation().isBlank();
	}
}
