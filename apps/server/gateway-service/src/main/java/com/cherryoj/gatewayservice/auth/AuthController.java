package com.cherryoj.gatewayservice.auth;

import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/auth")
final class AuthController {

	private final GatewayAuthenticationService authentication;
	private final UserServiceClient userService;
	private final LoginRateLimiter rateLimiter;

	AuthController(
			GatewayAuthenticationService authentication,
			UserServiceClient userService,
			LoginRateLimiter rateLimiter) {
		this.authentication = authentication;
		this.userService = userService;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping("/csrf")
	Mono<ResponseEntity<ApiSuccess<CsrfTokenData>>> csrf(ServerWebExchange exchange) {
		Mono<CsrfToken> token = exchange.getAttribute(CsrfToken.class.getName());
		if (token == null) {
			return Mono.error(new IllegalStateException("CSRF token was not initialized"));
		}
		return exchange.getSession().then(token).map(value -> noStore(ApiSuccess.of(
				new CsrfTokenData(value.getToken(), value.getHeaderName()),
				ApiRequestContext.requestId(exchange))));
	}

	@GetMapping("/session")
	Mono<ResponseEntity<ApiSuccess<Object>>> session(ServerWebExchange exchange) {
		return exchange.getSession().flatMap(session -> {
			AuthSessionState state = session.getAttribute(GatewayAuthenticationService.SESSION_STATE);
			if (state == null) {
				return Mono.just(noStore(ApiSuccess.of(
						(Object) Map.of("authenticated", false), ApiRequestContext.requestId(exchange))));
			}
			return authentication.current(session, ApiRequestContext.requestId(exchange), true)
					.map(current -> noStore(ApiSuccess.of(
							(Object) new AuthenticatedSessionData(true, current.user()),
							ApiRequestContext.requestId(exchange))));
		});
	}

	@PostMapping("/login")
	Mono<ResponseEntity<ApiSuccess<AuthenticatedSessionData>>> login(
			@Valid @RequestBody LoginRequest request,
			ServerWebExchange exchange) {
		rateLimiter.check(exchange);
		String requestId = ApiRequestContext.requestId(exchange);
		return exchange.getSession()
				.flatMap(session -> authentication.login(
						session, request.username(), request.password(), requestId))
				.map(state -> noStore(ApiSuccess.of(
						new AuthenticatedSessionData(true, state.user()), requestId)));
	}

	@PostMapping("/logout")
	Mono<ResponseEntity<Void>> logout(ServerWebExchange exchange) {
		return exchange.getSession()
				.flatMap(session -> authentication.logout(session, ApiRequestContext.requestId(exchange)))
				.thenReturn(ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build());
	}

	@PostMapping("/password/change")
	Mono<ResponseEntity<Void>> changePassword(
			@Valid @RequestBody ChangePasswordRequest request,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return exchange.getSession().flatMap(session ->
				authentication.current(session, requestId, true)
						.flatMap(state -> userService.changePassword(
								new DelegatedIdentity(state.accessToken(), state.accessTokenExpiresAt(), requestId),
								request.currentPassword(), request.newPassword())
								.onErrorMap(error -> GatewayAuthenticationService.mapDelegatedUpstream(error, requestId)))
						.then(authentication.invalidate(session))
						.thenReturn(ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build()));
	}

	private static <T> ResponseEntity<ApiSuccess<T>> noStore(ApiSuccess<T> body) {
		return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
	}

	record CsrfTokenData(String token, String headerName) {
	}

	record AuthenticatedSessionData(boolean authenticated, UserAccountData user) {
	}

	record LoginRequest(
			@NotBlank
			@Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$")
			String username,
			@NotBlank @Size(max = 128) String password) {
	}

	record ChangePasswordRequest(
			@NotBlank @Size(max = 128) String currentPassword,
			@NotBlank @Size(min = 12, max = 128) String newPassword) {
	}
}
