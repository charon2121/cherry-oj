package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebSession;

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

class AdminUserControllerTests {

	private static final Instant NOW = Instant.parse("2026-09-02T00:00:00Z");

	@Test
	void listsUsersWithoutRecoveryWhenDelegatedTokenIsAccepted() {
		GatewayAuthenticationService authentication = mock(GatewayAuthenticationService.class);
		UserServiceClient userService = mock(UserServiceClient.class);
		MockServerWebExchange exchange = exchange();
		WebSession session = exchange.getSession().block();
		AuthSessionState state = state("old-access-token");
		when(authentication.current(eq(session), anyString(), eq(true))).thenReturn(Mono.just(state));
		when(userService.listUsers(
				eq("old-access-token"), eq(1), eq(20), anyString()))
				.thenReturn(Mono.just(userPage()));

		var response = new AdminUserController(authentication, userService)
				.list(1, 20, exchange).block();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().data().items()).hasSize(1);
		verify(authentication, never())
				.recoverRejectedAccessToken(session, state, response.getBody().meta().requestId());
	}

	@Test
	void exchangesRejectedDelegatedTokenAndRetriesListOnce() {
		GatewayAuthenticationService authentication = mock(GatewayAuthenticationService.class);
		UserServiceClient userService = mock(UserServiceClient.class);
		MockServerWebExchange exchange = exchange();
		WebSession session = exchange.getSession().block();
		AuthSessionState oldState = state("old-access-token");
		AuthSessionState freshState = state("fresh-access-token");
		when(authentication.current(eq(session), anyString(), eq(true))).thenReturn(Mono.just(oldState));
		when(authentication.recoverRejectedAccessToken(eq(session), eq(oldState), anyString()))
				.thenReturn(Mono.just(freshState));
		when(userService.listUsers(
				eq("old-access-token"), eq(1), eq(20), anyString()))
				.thenReturn(Mono.error(unauthorized()));
		when(userService.listUsers(
				eq("fresh-access-token"), eq(1), eq(20), anyString()))
				.thenReturn(Mono.just(userPage()));

		var response = new AdminUserController(authentication, userService)
				.list(1, 20, exchange).block();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(response.getBody().data().items()).hasSize(1);
		InOrder order = inOrder(userService, authentication);
		order.verify(userService).listUsers("old-access-token", 1, 20, response.getBody().meta().requestId());
		order.verify(authentication)
				.recoverRejectedAccessToken(session, oldState, response.getBody().meta().requestId());
		order.verify(userService).listUsers(
				"fresh-access-token", 1, 20, response.getBody().meta().requestId());
	}

	@Test
	void mapsSecondDelegatedTokenRejectionToServiceUnavailableWithoutLooping() {
		GatewayAuthenticationService authentication = mock(GatewayAuthenticationService.class);
		UserServiceClient userService = mock(UserServiceClient.class);
		MockServerWebExchange exchange = exchange();
		WebSession session = exchange.getSession().block();
		AuthSessionState oldState = state("old-access-token");
		AuthSessionState freshState = state("fresh-access-token");
		when(authentication.current(eq(session), anyString(), eq(true))).thenReturn(Mono.just(oldState));
		when(authentication.recoverRejectedAccessToken(eq(session), eq(oldState), anyString()))
				.thenReturn(Mono.just(freshState));
		when(userService.listUsers(
				eq("old-access-token"), eq(1), eq(20), anyString()))
				.thenReturn(Mono.error(unauthorized()));
		when(userService.listUsers(
				eq("fresh-access-token"), eq(1), eq(20), anyString()))
				.thenReturn(Mono.error(unauthorized()));

		assertThatThrownBy(() -> new AdminUserController(authentication, userService)
				.list(1, 20, exchange).block())
				.isInstanceOfSatisfying(ApiProblemException.class, error -> {
					assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(error.code()).isEqualTo("SERVICE_UNAVAILABLE");
				});
		verify(authentication).recoverRejectedAccessToken(
				eq(session), eq(oldState), anyString());
	}

	@Test
	void preservesConfirmedGrantFailureFromRecovery() {
		GatewayAuthenticationService authentication = mock(GatewayAuthenticationService.class);
		UserServiceClient userService = mock(UserServiceClient.class);
		MockServerWebExchange exchange = exchange();
		WebSession session = exchange.getSession().block();
		AuthSessionState state = state("old-access-token");
		when(authentication.current(eq(session), anyString(), eq(true))).thenReturn(Mono.just(state));
		when(authentication.recoverRejectedAccessToken(eq(session), eq(state), anyString()))
				.thenReturn(Mono.error(new ApiProblemException(
						HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "未认证", "请先完成登录认证。")));
		when(userService.listUsers(
				eq("old-access-token"), eq(1), eq(20), anyString()))
				.thenReturn(Mono.error(unauthorized()));

		assertThatThrownBy(() -> new AdminUserController(authentication, userService)
				.list(1, 20, exchange).block())
				.isInstanceOfSatisfying(ApiProblemException.class, error -> {
					assertThat(error.status()).isEqualTo(HttpStatus.UNAUTHORIZED);
					assertThat(error.code()).isEqualTo("UNAUTHENTICATED");
				});
	}

	private static MockServerWebExchange exchange() {
		return MockServerWebExchange.from(MockServerHttpRequest
				.get("/api/admin/users?page=1&size=20").build());
	}

	private static AuthSessionState state(String accessToken) {
		return new AuthSessionState(
				user().publicView(), "grant-value", accessToken, NOW.plusSeconds(120),
				NOW.plusSeconds(1_800), NOW.plusSeconds(43_200));
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

	private static UserServiceClientException unauthorized() {
		return new UserServiceClientException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN");
	}
}
