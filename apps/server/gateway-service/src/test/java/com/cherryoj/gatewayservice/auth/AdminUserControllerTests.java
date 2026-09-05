package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebSession;

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

class AdminUserControllerTests {

	private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

	@Test
	void validatesOnceAndHandsOneImmutableIdentityToTheClient() {
		GatewayAuthenticationService authentication = mock(GatewayAuthenticationService.class);
		UserServiceClient userService = mock(UserServiceClient.class);
		MockServerWebExchange exchange = exchange();
		WebSession session = exchange.getSession().block();
		when(authentication.current(eq(session), anyString(), eq(true)))
				.thenReturn(Mono.just(state("delegated-access-token")));
		when(userService.listUsers(org.mockito.ArgumentMatchers.any(DelegatedIdentity.class), eq(1), eq(20)))
				.thenReturn(Mono.just(userPage()));

		var response = new AdminUserController(authentication, userService)
				.list(1, 20, exchange).block();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		ArgumentCaptor<DelegatedIdentity> identity = ArgumentCaptor.forClass(DelegatedIdentity.class);
		verify(userService).listUsers(identity.capture(), eq(1), eq(20));
		assertThat(identity.getValue().accessToken()).isEqualTo("delegated-access-token");
		assertThat(identity.getValue().requestId()).isEqualTo(response.getBody().meta().requestId());
		verify(authentication, times(1)).current(eq(session), anyString(), eq(true));
	}

	@Test
	void resourceTokenRejectionBecomes503WithoutExchangeOrReplay() {
		GatewayAuthenticationService authentication = mock(GatewayAuthenticationService.class);
		UserServiceClient userService = mock(UserServiceClient.class);
		MockServerWebExchange exchange = exchange();
		WebSession session = exchange.getSession().block();
		when(authentication.current(eq(session), anyString(), eq(true)))
				.thenReturn(Mono.just(state("delegated-access-token")));
		when(userService.listUsers(org.mockito.ArgumentMatchers.any(DelegatedIdentity.class), eq(1), eq(20)))
				.thenReturn(Mono.error(new UserServiceClientException(
						HttpStatus.UNAUTHORIZED, "INVALID_TOKEN")));

		assertThatThrownBy(() -> new AdminUserController(authentication, userService)
				.list(1, 20, exchange).block())
				.isInstanceOfSatisfying(ApiProblemException.class, error -> {
					assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(error.code()).isEqualTo("SERVICE_UNAVAILABLE");
				});
		verify(userService, times(1)).listUsers(
				org.mockito.ArgumentMatchers.any(DelegatedIdentity.class), eq(1), eq(20));
		verify(authentication, times(1)).current(eq(session), anyString(), eq(true));
	}

	private static MockServerWebExchange exchange() {
		return MockServerWebExchange.from(MockServerHttpRequest
				.get("/api/admin/users?page=1&size=20").build());
	}

	private static AuthSessionState state(String accessToken) {
		return new AuthSessionState(
				user().publicView(), "grant-value", accessToken, NOW.plusSeconds(7_200),
				NOW.plus(java.time.Duration.ofDays(30)));
	}

	private static UserServiceClient.UserPage userPage() {
		return new UserServiceClient.UserPage(java.util.List.of(user()), 1, 20, 1, 1);
	}

	private static UserServiceClient.InternalUser user() {
		LocalDateTime created = LocalDateTime.parse("2026-09-01T00:00:00");
		return new UserServiceClient.InternalUser(
				"019c8e42-7f70-7000-8000-000000000001", "admin01", "ADMIN", "ACTIVE",
				false, created, created, 0);
	}
}
