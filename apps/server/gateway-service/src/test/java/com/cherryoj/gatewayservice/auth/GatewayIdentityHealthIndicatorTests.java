package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

class GatewayIdentityHealthIndicatorTests {

	@Test
	void readinessRequiresReachableMatchingIssuerMetadata() {
		AtomicReference<ClientRequest> captured = new AtomicReference<>();
		WebClient.Builder builder = WebClient.builder().exchangeFunction(request -> {
			captured.set(request);
			return Mono.just(ClientResponse.create(HttpStatus.OK)
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.body("""
							{"activeKid":"rsa-key","publishedKids":["rsa-key"],"algorithm":"RS256",
							 "accessTokenTtlSeconds":7200,"generation":"generation-1"}
							""").build());
		});
		GatewayAuthProperties properties = properties();
		UserServiceClient userService = new UserServiceClient(
				builder, properties, new InternalRequestFactory());

		var health = new GatewayIdentityHealthIndicator(userService, properties).health().block();

		assertThat(health.getStatus()).isEqualTo(Status.UP);
		assertThat(captured.get().url().getPath()).isEqualTo("/internal/identity/metadata");
		assertThat(captured.get().headers().getFirst("X-Request-Id"))
				.matches("^req_[0-9a-f]{32}$");
	}

	private static GatewayAuthProperties properties() {
		return new GatewayAuthProperties(
				URI.create("http://127.0.0.1:8081"), Duration.ofSeconds(5), "fixed-absolute",
				2_592_000, Duration.ofHours(2), Duration.ofMinutes(5),
				List.of("http://localhost:5173"), 10);
	}
}
