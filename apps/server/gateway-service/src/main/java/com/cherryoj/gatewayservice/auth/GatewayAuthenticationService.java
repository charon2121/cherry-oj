package com.cherryoj.gatewayservice.auth;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.WebSession;

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

@Service
final class GatewayAuthenticationService {

	static final String SESSION_STATE = GatewayAuthenticationService.class.getName() + ".state";

	private final UserServiceClient userService;
	private final GatewayAuthProperties properties;
	private final ConcurrentHashMap<String, Mono<AuthSessionState>> refreshes = new ConcurrentHashMap<>();
	private final Clock clock;

	@Autowired
	GatewayAuthenticationService(UserServiceClient userService, GatewayAuthProperties properties) {
		this(userService, properties, Clock.systemUTC());
	}

	GatewayAuthenticationService(
			UserServiceClient userService, GatewayAuthProperties properties, Clock clock) {
		this.userService = userService;
		this.properties = properties;
		this.clock = clock;
	}

	Mono<AuthSessionState> login(
			WebSession session, String username, String password, String requestId) {
		return userService.authenticate(username, password, requestId)
				.onErrorMap(error -> mapUpstream(error, true))
				.flatMap(result -> {
					Instant absolute = result.sessionAbsoluteExpiresAt().toInstant(ZoneOffset.UTC);
					Instant localCap = clock.instant().plus(properties.sessionAbsoluteTimeout());
					AuthSessionState state = new AuthSessionState(
							result.user().publicView(),
							result.loginGrant(),
							result.accessToken(),
							result.accessTokenExpiresAt(),
							absolute.isBefore(localCap) ? absolute : localCap);
					return session.changeSessionId()
							.then(Mono.fromRunnable(() -> session.getAttributes().put(SESSION_STATE, state)))
							.thenReturn(state);
				});
	}

	Mono<AuthSessionState> current(WebSession session, String requestId, boolean refreshToken) {
		AuthSessionState state = session.getAttribute(SESSION_STATE);
		if (state == null) {
			return Mono.error(unauthenticated());
		}
		if (!clock.instant().isBefore(state.absoluteExpiresAt())) {
			return invalidateThenUnauthenticated(session);
		}
		if (!refreshToken
				|| state.accessTokenExpiresAt().isAfter(clock.instant().plus(properties.tokenRefreshSkew()))) {
			return Mono.just(state);
		}
		return singleFlightRefresh(session, state, requestId);
	}

	Mono<Void> logout(WebSession session, String requestId) {
		AuthSessionState state = session.getAttribute(SESSION_STATE);
		if (state == null) {
			return session.invalidate();
		}
		return userService.revoke(state.loginGrant(), requestId)
				.onErrorMap(error -> mapUpstream(error, false))
				.then(session.invalidate())
				.onErrorResume(error -> session.invalidate().then(Mono.error(error)));
	}

	Mono<Void> invalidate(WebSession session) {
		return session.invalidate();
	}

	private Mono<AuthSessionState> singleFlightRefresh(
			WebSession session, AuthSessionState previous, String requestId) {
		String sessionId = session.getId();
		return refreshes.computeIfAbsent(sessionId, ignored -> userService
				.exchange(previous.loginGrant(), requestId)
				.flatMap(result -> {
					AuthSessionState current = session.getAttribute(SESSION_STATE);
					if (current == null || !current.loginGrant().equals(previous.loginGrant())) {
						return Mono.error(unauthenticated());
					}
					Instant absolute = result.sessionAbsoluteExpiresAt().toInstant(ZoneOffset.UTC);
					AuthSessionState refreshed = new AuthSessionState(
							result.user().publicView(), previous.loginGrant(), result.accessToken(),
							result.accessTokenExpiresAt(),
							absolute.isBefore(previous.absoluteExpiresAt())
									? absolute : previous.absoluteExpiresAt());
					session.getAttributes().put(SESSION_STATE, refreshed);
					return Mono.just(refreshed);
				})
				.onErrorResume(UserServiceClientException.class, error -> {
					if (error.status().value() == 401) {
						return invalidateThenUnauthenticated(session);
					}
					return Mono.error(mapUpstream(error, false));
				})
				.onErrorMap(TimeoutException.class, error -> gatewayTimeout())
				.doFinally(signal -> refreshes.remove(sessionId))
				.cache());
	}

	private <T> Mono<T> invalidateThenUnauthenticated(WebSession session) {
		return session.invalidate().then(Mono.error(unauthenticated()));
	}

	static ApiProblemException mapUpstream(Throwable error, boolean authentication) {
		if (error instanceof ApiProblemException apiProblem) {
			return apiProblem;
		}
		if (error instanceof TimeoutException) {
			return gatewayTimeout();
		}
		if (error instanceof UserServiceClientException upstream) {
			if (authentication && upstream.status().value() == 401) {
				return new ApiProblemException(
						HttpStatus.UNAUTHORIZED,
						"AUTHENTICATION_FAILED",
						"登录失败",
						"用户名或密码错误，或账号当前不可登录。");
			}
			if (upstream.status().is4xxClientError()) {
				HttpStatus status = HttpStatus.resolve(upstream.status().value());
				return new ApiProblemException(
						status == null ? HttpStatus.BAD_GATEWAY : status,
						upstream.code(),
						clientTitle(upstream.status().value()),
						clientDetail(upstream.status().value()));
			}
		}
		return new ApiProblemException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"SERVICE_UNAVAILABLE",
				"服务暂不可用",
				"服务暂时不可用，请稍后重试。");
	}

	private static String clientTitle(int status) {
		return switch (status) {
			case 401 -> "未认证";
			case 403 -> "无权访问";
			case 404 -> "资源不存在";
			case 409 -> "资源状态冲突";
			case 422 -> "请求参数校验失败";
			case 429 -> "请求过于频繁";
			default -> "请求失败";
		};
	}

	private static String clientDetail(int status) {
		return switch (status) {
			case 401 -> "请先完成登录认证。";
			case 403 -> "当前身份无权执行此操作。";
			case 404 -> "请求的资源不存在。";
			case 409 -> "资源当前状态不允许此操作。";
			case 422 -> "请检查请求参数。";
			case 429 -> "请稍后重试。";
			default -> "请求无法完成。";
		};
	}

	private static ApiProblemException unauthenticated() {
		return new ApiProblemException(
				HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "未认证", "请先完成登录认证。");
	}

	private static ApiProblemException gatewayTimeout() {
		return new ApiProblemException(
				HttpStatus.GATEWAY_TIMEOUT,
				"GATEWAY_TIMEOUT",
				"上游响应超时",
				"服务暂时不可用，请稍后重试。");
	}
}
