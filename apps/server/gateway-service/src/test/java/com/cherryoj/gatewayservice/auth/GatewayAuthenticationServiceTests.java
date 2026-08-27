package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockWebSession;
import org.springframework.web.server.WebSession;

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

class GatewayAuthenticationServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

	@Test
	void concurrentRefreshUsesOneUpstreamExchange() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		AuthSessionState initial = state(NOW.plusSeconds(5));
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, initial);
		when(client.exchange("grant-value", "req_test")).thenReturn(Mono.just(exchangeResult()));
		GatewayAuthenticationService service = service(client);

		List<AuthSessionState> results = Mono.zip(
				service.current(session, "req_test", true),
				service.current(session, "req_test", true),
				List::of).block();

		assertThat(results).hasSize(2).allSatisfy(result ->
				assertThat(result.accessToken()).isEqualTo("fresh-access-token"));
		verify(client).exchange("grant-value", "req_test");
	}

	@Test
	void temporaryRefreshFailurePreservesSessionState() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		AuthSessionState initial = state(NOW.plusSeconds(5));
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, initial);
		when(client.exchange("grant-value", "req_test"))
				.thenReturn(Mono.error(new UserServiceClientException(
						HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_ERROR")));

		assertThatThrownBy(() -> service(client).current(session, "req_test", true).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.status().value())
						.isEqualTo(503));
		assertThat(session.getAttributes().get(GatewayAuthenticationService.SESSION_STATE))
				.isSameAs(initial);
	}

	@Test
	void revokedGrantInvalidatesSession() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, state(NOW.plusSeconds(5)));
		when(client.exchange("grant-value", "req_test"))
				.thenReturn(Mono.error(new UserServiceClientException(
						HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED")));
		assertThatThrownBy(() -> service(client).current(session, "req_test", true).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code())
						.isEqualTo("UNAUTHENTICATED"));
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test
	void temporaryRevokeFailureStillInvalidatesCurrentBrowserSession() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, state(NOW.plusSeconds(120)));
		when(client.revoke("grant-value", "req_test"))
				.thenReturn(Mono.error(new UserServiceClientException(
						HttpStatus.SERVICE_UNAVAILABLE, "UPSTREAM_ERROR")));

		assertThatThrownBy(() -> service(client).logout(session, "req_test").block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.status().value()).isEqualTo(503));
		assertThat(session.getAttributes()).isEmpty();
	}

	private static GatewayAuthenticationService service(UserServiceClient client) {
		return new GatewayAuthenticationService(client, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static GatewayAuthProperties properties() {
		return new GatewayAuthProperties(
				URI.create("http://localhost:8081"), Duration.ofSeconds(5), Duration.ofHours(12),
				Duration.ofSeconds(15), List.of("http://localhost:5173"), 10);
	}

	private static AuthSessionState state(Instant tokenExpiry) {
		return new AuthSessionState(user(), "grant-value", "old-access-token", tokenExpiry, NOW.plusSeconds(3_600));
	}

	private static UserServiceClient.TokenExchangeResult exchangeResult() {
		return new UserServiceClient.TokenExchangeResult(
				internalUser(), "fresh-access-token", NOW.plusSeconds(120),
				LocalDateTime.ofInstant(NOW.plusSeconds(1_800), ZoneOffset.UTC),
				LocalDateTime.ofInstant(NOW.plusSeconds(3_600), ZoneOffset.UTC));
	}

	private static UserAccountData user() {
		return internalUser().publicView();
	}

	private static UserServiceClient.InternalUser internalUser() {
		LocalDateTime created = LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC);
		return new UserServiceClient.InternalUser(
				"019c8e42-7f70-7000-8000-000000000001", "admin01", "ADMIN", "ACTIVE",
				false, created, created, 0);
	}
}
