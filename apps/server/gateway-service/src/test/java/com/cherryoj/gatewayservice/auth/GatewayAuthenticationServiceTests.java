package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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
	void rejectedAccessTokenForcesExchangeBeforeRefreshWindow() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		AuthSessionState initial = state(NOW.plusSeconds(120));
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, initial);
		when(client.exchange("grant-value", "req_test")).thenReturn(Mono.just(exchangeResult()));

		AuthSessionState recovered = service(client)
				.recoverRejectedAccessToken(session, initial, "req_test").block();

		assertThat(recovered.accessToken()).isEqualTo("fresh-access-token");
		assertThat((AuthSessionState) session.getAttribute(GatewayAuthenticationService.SESSION_STATE))
				.isEqualTo(recovered);
		verify(client).exchange("grant-value", "req_test");
		verify(client, never()).touch("grant-value", "req_test");
	}

	@Test
	void concurrentRejectedTokenRecoveryUpdatesEverySessionInstanceWithOneExchange() {
		UserServiceClient client = mock(UserServiceClient.class);
		AuthSessionState initial = state(NOW.plusSeconds(120));
		WebSession first = session("shared-session", initial);
		WebSession second = session("shared-session", initial);
		when(client.exchange("grant-value", "req_test"))
				.thenReturn(Mono.delay(Duration.ofMillis(10)).map(ignored -> exchangeResult()));
		GatewayAuthenticationService service = service(client);

		List<AuthSessionState> recovered = Mono.zip(
				service.recoverRejectedAccessToken(first, initial, "req_test"),
				service.recoverRejectedAccessToken(second, initial, "req_test"),
				List::of).block();

		assertThat(recovered).hasSize(2).allSatisfy(state ->
				assertThat(state.accessToken()).isEqualTo("fresh-access-token"));
		assertThat(((AuthSessionState) first.getAttribute(GatewayAuthenticationService.SESSION_STATE))
				.accessToken()).isEqualTo("fresh-access-token");
		assertThat(((AuthSessionState) second.getAttribute(GatewayAuthenticationService.SESSION_STATE))
				.accessToken()).isEqualTo("fresh-access-token");
		verify(client).exchange("grant-value", "req_test");
	}

	@Test
	void concurrentRejectedGrantInvalidatesEverySessionInstanceWithOneExchange() {
		UserServiceClient client = mock(UserServiceClient.class);
		AuthSessionState initial = state(NOW.plusSeconds(120));
		WebSession first = session("shared-session", initial);
		WebSession second = session("shared-session", initial);
		when(client.exchange("grant-value", "req_test"))
				.thenReturn(Mono.delay(Duration.ofMillis(10)).flatMap(ignored -> Mono.error(
						new UserServiceClientException(
								HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED"))));
		GatewayAuthenticationService service = service(client);

		Mono.when(
				service.recoverRejectedAccessToken(first, initial, "req_test")
						.onErrorResume(error -> assertUnauthenticated(error)),
				service.recoverRejectedAccessToken(second, initial, "req_test")
						.onErrorResume(error -> assertUnauthenticated(error)))
				.block();

		assertThat(first.getAttributes()).isEmpty();
		assertThat(second.getAttributes()).isEmpty();
		verify(client).exchange("grant-value", "req_test");
	}

	@Test
	void lateTouchDoesNotOverwriteNewerAccessToken() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		AuthSessionState initial = state(NOW.plusSeconds(120));
		AuthSessionState newer = new AuthSessionState(
				initial.user(), initial.loginGrant(), "newer-access-token", NOW.plusSeconds(240),
				initial.idleExpiresAt(), initial.absoluteExpiresAt());
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, initial);
		when(client.touch("grant-value", "req_test"))
				.thenReturn(Mono.delay(Duration.ofMillis(10)).map(ignored -> touchResult(true)));

		Mono<AuthSessionState> touching = service(client).current(session, "req_test", true);
		AuthSessionState result = touching
				.doOnSubscribe(ignored -> session.getAttributes().put(
						GatewayAuthenticationService.SESSION_STATE, newer))
				.block();

		assertThat(result).isSameAs(newer);
		assertThat((AuthSessionState) session.getAttribute(GatewayAuthenticationService.SESSION_STATE))
				.isSameAs(newer);
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

	@Test
	void authenticatedActivityRefreshesIdleWhenEnabled() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		AuthSessionState initial = new AuthSessionState(
				user(), "grant-value", "old-access-token", NOW.plusSeconds(120),
				NOW.plusSeconds(600), NOW.plusSeconds(3_600));
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, initial);
		when(client.touch("grant-value", "req_test")).thenReturn(Mono.just(touchResult(true)));

		AuthSessionState touched = service(client).current(session, "req_test", true).block();

		assertThat(touched.idleExpiresAt()).isEqualTo(NOW.plusSeconds(1_800));
		assertThat(session.getMaxIdleTime()).isEqualTo(Duration.ofSeconds(1_800));
		verify(client).touch("grant-value", "req_test");
	}

	@Test
	void authenticatedActivityKeepsFixedIdleDeadlineWhenRefreshIsDisabled() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		AuthSessionState initial = new AuthSessionState(
				user(), "grant-value", "old-access-token", NOW.plusSeconds(120),
				NOW.plusSeconds(600), NOW.plusSeconds(3_600));
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, initial);

		AuthSessionState current = service(client, properties(false)).current(session, "req_test", true).block();

		assertThat(current).isSameAs(initial);
		assertThat(session.getMaxIdleTime()).isEqualTo(Duration.ofSeconds(600));
		verify(client, never()).touch("grant-value", "req_test");
		verify(client, never()).exchange("grant-value", "req_test");
	}

	@Test
	void reachingIdleDeadlineInvalidatesSessionWithoutCallingUpstream() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		session.getAttributes().put(
				GatewayAuthenticationService.SESSION_STATE,
				new AuthSessionState(
						user(), "grant-value", "old-access-token", NOW.plusSeconds(120),
						NOW, NOW.plusSeconds(3_600)));

		assertThatThrownBy(() -> service(client).current(session, "req_test", true).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.code()).isEqualTo("UNAUTHENTICATED"));
		assertThat(session.getAttributes()).isEmpty();
		verify(client, never()).touch("grant-value", "req_test");
		verify(client, never()).exchange("grant-value", "req_test");
	}

	@Test
	void authenticatedActivityCapsIdleAtAbsoluteDeadline() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		AuthSessionState initial = new AuthSessionState(
				user(), "grant-value", "old-access-token", NOW.plusSeconds(120),
				NOW.plusSeconds(300), NOW.plusSeconds(600));
		session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, initial);
		UserServiceClient.SessionTouchResult capped = new UserServiceClient.SessionTouchResult(
				LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC),
				LocalDateTime.ofInstant(NOW.plusSeconds(600), ZoneOffset.UTC),
				1_800, 3_600, true);
		when(client.touch("grant-value", "req_test")).thenReturn(Mono.just(capped));

		AuthSessionState touched = service(client).current(session, "req_test", true).block();

		assertThat(touched.idleExpiresAt()).isEqualTo(NOW.plusSeconds(600));
		assertThat(session.getMaxIdleTime()).isEqualTo(Duration.ofSeconds(600));
	}

	@Test
	void mismatchedUserServiceSessionConfigurationFailsClosed() {
		UserServiceClient client = mock(UserServiceClient.class);
		WebSession session = new MockWebSession();
		session.getAttributes().put(
				GatewayAuthenticationService.SESSION_STATE, state(NOW.plusSeconds(120)));
		UserServiceClient.SessionTouchResult mismatched = new UserServiceClient.SessionTouchResult(
				LocalDateTime.ofInstant(NOW.plusSeconds(1_800), ZoneOffset.UTC),
				LocalDateTime.ofInstant(NOW.plusSeconds(3_600), ZoneOffset.UTC),
				900, 3_600, true);
		when(client.touch("grant-value", "req_test")).thenReturn(Mono.just(mismatched));

		assertThatThrownBy(() -> service(client).current(session, "req_test", true).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
	}

	private static GatewayAuthenticationService service(UserServiceClient client) {
		return service(client, properties(true));
	}

	private static GatewayAuthenticationService service(
			UserServiceClient client, GatewayAuthProperties properties) {
		return new GatewayAuthenticationService(client, properties, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static GatewayAuthProperties properties(boolean refreshIdle) {
		return new GatewayAuthProperties(
				URI.create("http://localhost:8081"), Duration.ofSeconds(5), 1_800, 3_600,
				Boolean.toString(refreshIdle),
				Duration.ofSeconds(15), List.of("http://localhost:5173"), 10);
	}

	private static AuthSessionState state(Instant tokenExpiry) {
		return new AuthSessionState(
				user(), "grant-value", "old-access-token", tokenExpiry,
				NOW.plusSeconds(1_800), NOW.plusSeconds(3_600));
	}

	private static WebSession session(String id, AuthSessionState initial) {
		WebSession session = mock(WebSession.class);
		Map<String, Object> attributes = new ConcurrentHashMap<>();
		attributes.put(GatewayAuthenticationService.SESSION_STATE, initial);
		when(session.getId()).thenReturn(id);
		when(session.getAttributes()).thenReturn(attributes);
		when(session.getAttribute(GatewayAuthenticationService.SESSION_STATE))
				.thenAnswer(ignored -> attributes.get(GatewayAuthenticationService.SESSION_STATE));
		when(session.invalidate()).thenAnswer(ignored -> {
			attributes.clear();
			return Mono.empty();
		});
		return session;
	}

	private static Mono<AuthSessionState> assertUnauthenticated(Throwable error) {
		assertThat(error).isInstanceOfSatisfying(ApiProblemException.class, problem -> {
			assertThat(problem.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
			assertThat(problem.code()).isEqualTo("UNAUTHENTICATED");
		});
		return Mono.empty();
	}

	private static UserServiceClient.TokenExchangeResult exchangeResult() {
		return new UserServiceClient.TokenExchangeResult(
				internalUser(), "fresh-access-token", NOW.plusSeconds(120),
				LocalDateTime.ofInstant(NOW.plusSeconds(1_800), ZoneOffset.UTC),
				LocalDateTime.ofInstant(NOW.plusSeconds(3_600), ZoneOffset.UTC),
				1_800, 3_600, true);
	}

	private static UserServiceClient.SessionTouchResult touchResult(boolean refreshIdle) {
		return new UserServiceClient.SessionTouchResult(
				LocalDateTime.ofInstant(NOW.plusSeconds(1_800), ZoneOffset.UTC),
				LocalDateTime.ofInstant(NOW.plusSeconds(3_600), ZoneOffset.UTC),
				1_800, 3_600, refreshIdle);
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
