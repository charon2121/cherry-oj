package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiProblemException;

class LoginRateLimiterTests {

	@Test
	void rateLimitedResponseTellsTheBrowserWhenRetryIsAllowed() {
		LoginRateLimiter limiter = new LoginRateLimiter(
				1, Clock.fixed(Instant.parse("2026-08-26T12:00:00Z"), ZoneOffset.UTC));
		MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
				.post("/api/auth/login")
				.remoteAddress(new InetSocketAddress("192.0.2.10", 12345))
				.build());

		limiter.check(exchange);

		assertThatThrownBy(() -> limiter.check(exchange))
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("RATE_LIMITED"));
		assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER))
				.isEqualTo("60");
	}
}
