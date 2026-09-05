package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

class GatewayAuthenticationServiceTests {

	private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
	private static final Instant ABSOLUTE = NOW.plus(Duration.ofDays(30));

	@Test
	void concurrentRefreshUsesOneExchangeAndKeepsFixedAbsoluteDeadline() {
		UserServiceClient client = mock(UserServiceClient.class);
		MockWebSession session = session(state(NOW.plusSeconds(5), ABSOLUTE));
		when(client.exchange("grant-value", "req_test")).thenReturn(Mono.just(exchangeResult(ABSOLUTE)));
		GatewayAuthenticationService service = service(client);

		List<AuthSessionState> results = Mono.zip(
				service.current(session, "req_test", true),
				service.current(session, "req_test", true), List::of).block();

		assertThat(results).allSatisfy(result -> {
			assertThat(result.accessToken()).isEqualTo("fresh-access-token");
			assertThat(result.absoluteExpiresAt()).isEqualTo(ABSOLUTE);
		});
		verify(client).exchange("grant-value", "req_test");
	}

	@Test
	void refreshesAtExactlyFiveMinutesButNotOneSecondBeforeTheWindow() {
		UserServiceClient client = mock(UserServiceClient.class);
		when(client.exchange("grant-value", "req_test")).thenReturn(Mono.just(exchangeResult(ABSOLUTE)));
		when(client.validate("grant-value", "req_test")).thenReturn(Mono.just(validationResult(ABSOLUTE)));

		service(client).current(session(state(NOW.plus(Duration.ofMinutes(5)), ABSOLUTE)),
				"req_test", true).block();
		service(client).current(session(state(NOW.plus(Duration.ofMinutes(5)).plusSeconds(1), ABSOLUTE)),
				"req_test", true).block();

		verify(client, times(1)).exchange("grant-value", "req_test");
		verify(client, times(1)).validate("grant-value", "req_test");
	}

	@Test
	void refreshSingleFlightIsIsolatedPerBrowserSession() {
		UserServiceClient client = mock(UserServiceClient.class);
		when(client.exchange("grant-value", "req_test")).thenReturn(Mono.just(exchangeResult(ABSOLUTE)));
		GatewayAuthenticationService service = service(client);

		Mono.zip(
				service.current(session(state(NOW.plusSeconds(5), ABSOLUTE)), "req_test", true),
				service.current(session(state(NOW.plusSeconds(5), ABSOLUTE)), "req_test", true))
				.block();

		verify(client, times(2)).exchange("grant-value", "req_test");
	}

	@Test
	void ordinaryRequestValidatesGrantWithoutSlidingDeadline() {
		UserServiceClient client = mock(UserServiceClient.class);
		MockWebSession session = session(state(NOW.plus(Duration.ofHours(1)), ABSOLUTE));
		when(client.validate("grant-value", "req_test")).thenReturn(Mono.just(validationResult(ABSOLUTE)));

		AuthSessionState current = service(client).current(session, "req_test", true).block();

		assertThat(current.absoluteExpiresAt()).isEqualTo(ABSOLUTE);
		assertThat(session.getMaxIdleTime()).isEqualTo(Duration.ofDays(30));
		verify(client).validate("grant-value", "req_test");
		verify(client, never()).exchange("grant-value", "req_test");
	}

	@Test
	void oldIdleBoundaryNoLongerInvalidatesButAbsoluteDeadlineDoes() {
		UserServiceClient client = mock(UserServiceClient.class);
		MockWebSession validAfterOldIdle = session(state(NOW.plus(Duration.ofHours(1)), ABSOLUTE));
		when(client.validate("grant-value", "req_test")).thenReturn(Mono.just(validationResult(ABSOLUTE)));
		assertThat(service(client).current(validAfterOldIdle, "req_test", true).block()).isNotNull();

		MockWebSession expired = session(state(NOW.plus(Duration.ofHours(1)), NOW));
		assertThatThrownBy(() -> service(client).current(expired, "req_test", true).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("UNAUTHENTICATED"));
		assertThat(expired.getAttributes()).isEmpty();
	}

	@Test
	void revokedGrantInvalidatesGatewaySession() {
		UserServiceClient client = mock(UserServiceClient.class);
		MockWebSession session = session(state(NOW.plus(Duration.ofHours(1)), ABSOLUTE));
		when(client.validate("grant-value", "req_test")).thenReturn(Mono.error(
				new UserServiceClientException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED")));

		assertThatThrownBy(() -> service(client).current(session, "req_test", true).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("UNAUTHENTICATED"));
		assertThat(session.getAttributes()).isEmpty();
	}

	@Test
	void changedAbsoluteDeadlineFailsClosedWithoutMutatingSession() {
		UserServiceClient client = mock(UserServiceClient.class);
		AuthSessionState initial = state(NOW.plus(Duration.ofHours(1)), ABSOLUTE);
		MockWebSession session = session(initial);
		when(client.validate("grant-value", "req_test")).thenReturn(Mono.just(
				new UserServiceClient.SessionValidationResult(
						LocalDateTime.ofInstant(ABSOLUTE.plusSeconds(1), ZoneOffset.UTC),
						2_592_000, "fixed-absolute")));

		assertThatThrownBy(() -> service(client).current(session, "req_test", true).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
		assertThat((AuthSessionState) session.getAttribute(GatewayAuthenticationService.SESSION_STATE))
				.isSameAs(initial);
	}

	private static GatewayAuthenticationService service(UserServiceClient client) {
		return new GatewayAuthenticationService(client, properties(), Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static GatewayAuthProperties properties() {
		return new GatewayAuthProperties(
				URI.create("http://localhost:8081"), Duration.ofSeconds(5), "fixed-absolute", 2_592_000,
				Duration.ofHours(2), Duration.ofMinutes(5), List.of("http://localhost:5173"), 10);
	}

	private static MockWebSession session(AuthSessionState state) {
		MockWebSession session = new MockWebSession();
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, state);
		return session;
	}

	private static AuthSessionState state(Instant tokenExpiry, Instant absolute) {
		return new AuthSessionState(user(), "grant-value", "old-access-token", tokenExpiry, absolute);
	}

	private static UserServiceClient.TokenExchangeResult exchangeResult(Instant absolute) {
		return new UserServiceClient.TokenExchangeResult(
				internalUser(), "fresh-access-token", NOW.plus(Duration.ofHours(2)),
				LocalDateTime.ofInstant(absolute, ZoneOffset.UTC), 2_592_000, "fixed-absolute");
	}

	private static UserServiceClient.SessionValidationResult validationResult(Instant absolute) {
		return new UserServiceClient.SessionValidationResult(
				LocalDateTime.ofInstant(absolute, ZoneOffset.UTC), 2_592_000, "fixed-absolute");
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
