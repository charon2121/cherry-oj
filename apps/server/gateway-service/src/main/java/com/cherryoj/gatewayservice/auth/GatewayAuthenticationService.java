package com.cherryoj.gatewayservice.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.WebSession;

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

@Service
final class GatewayAuthenticationService {
	private static final Logger LOGGER = LoggerFactory.getLogger(GatewayAuthenticationService.class);
	private static final Duration IDENTITY_CLOCK_SKEW = Duration.ofSeconds(30);

	static final String SESSION_STATE = GatewayAuthenticationService.class.getName() + ".state";

	private final UserServiceClient userService;
	private final GatewayAuthProperties properties;
	private final ConcurrentHashMap<String, SessionSynchronization> synchronizations = new ConcurrentHashMap<>();
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
					validateConfiguration(result.sessionAbsoluteTimeoutSeconds(), result.sessionLifetimePolicy());
					Instant absolute = result.sessionAbsoluteExpiresAt().toInstant(ZoneOffset.UTC);
					validateInitialDeadline(absolute);
					validateIssuedTokenDeadline(result.accessTokenExpiresAt());
					AuthSessionState state = new AuthSessionState(
							result.user().publicView(),
							result.loginGrant(),
							result.accessToken(),
							result.accessTokenExpiresAt(),
							absolute);
					return session.changeSessionId()
							.then(Mono.fromRunnable(() -> store(session, state)))
							.thenReturn(state);
				});
	}

	Mono<AuthSessionState> current(WebSession session, String requestId, boolean refreshToken) {
		AuthSessionState state = session.getAttribute(SESSION_STATE);
		if (state == null) {
			return Mono.error(unauthenticated());
		}
		Instant now = clock.instant();
		if (!now.isBefore(state.absoluteExpiresAt())) {
			return invalidateThenUnauthenticated(session);
		}
		applySessionDeadline(session, state);
		if (refreshToken
				&& !state.accessTokenExpiresAt().isAfter(now.plus(properties.tokenRefreshAhead()))) {
			return singleFlightExchange(session, state, requestId);
		}
		return singleFlightValidate(session, state, requestId);
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

	private Mono<AuthSessionState> singleFlightExchange(
			WebSession session, AuthSessionState previous, String requestId) {
		String sessionId = session.getId();
		SessionSynchronization synchronization = acquireSynchronization(
				sessionId, SynchronizationOperation.EXCHANGE, () -> userService
				.exchange(previous.loginGrant(), requestId)
				.flatMap(result -> {
					ensureCurrent(session, previous);
					validateConfiguration(result.sessionAbsoluteTimeoutSeconds(), result.sessionLifetimePolicy());
					Instant absolute = result.sessionAbsoluteExpiresAt().toInstant(ZoneOffset.UTC);
					validateUpdatedDeadline(previous, absolute);
					validateIssuedTokenDeadline(result.accessTokenExpiresAt());
					AuthSessionState refreshed = new AuthSessionState(
							result.user().publicView(), previous.loginGrant(), result.accessToken(),
							result.accessTokenExpiresAt(),
							absolute);
					return Mono.just(refreshed);
				})
				.onErrorResume(UserServiceClientException.class, this::upstreamSessionError)
				.onErrorMap(TimeoutException.class, error -> gatewayTimeout()));
		Mono<AuthSessionState> applied = applySynchronization(
				session, previous, synchronization);
		if (synchronization.operation() == SynchronizationOperation.VALIDATE) {
			return applied.then(Mono.defer(() -> singleFlightExchange(
					session, requireCurrent(session, previous), requestId)));
		}
		return applied;
	}

	private Mono<AuthSessionState> singleFlightValidate(
			WebSession session, AuthSessionState previous, String requestId) {
		String sessionId = session.getId();
		SessionSynchronization synchronization = acquireSynchronization(
				sessionId, SynchronizationOperation.VALIDATE, () -> userService
				.validate(previous.loginGrant(), requestId)
				.flatMap(result -> {
					ensureCurrent(session, previous);
					validateConfiguration(result.sessionAbsoluteTimeoutSeconds(), result.sessionLifetimePolicy());
					Instant absolute = result.sessionAbsoluteExpiresAt().toInstant(ZoneOffset.UTC);
					validateUpdatedDeadline(previous, absolute);
					AuthSessionState validated = new AuthSessionState(
							previous.user(), previous.loginGrant(), previous.accessToken(),
							previous.accessTokenExpiresAt(), absolute);
					return Mono.just(validated);
				})
				.onErrorResume(UserServiceClientException.class, this::upstreamSessionError)
				.onErrorMap(TimeoutException.class, error -> gatewayTimeout()));
		return applySynchronization(session, previous, synchronization);
	}

	private SessionSynchronization acquireSynchronization(
			String sessionId,
			SynchronizationOperation operation,
			Supplier<? extends Mono<AuthSessionState>> source) {
		AtomicReference<SessionSynchronization> createdReference = new AtomicReference<>();
		Mono<AuthSessionState> result = Mono.defer(source)
				.doOnEach(signal -> {
					if (signal.isOnComplete() || signal.isOnError()) {
						synchronizations.remove(sessionId, createdReference.get());
					}
				})
				.cache();
		SessionSynchronization created = new SessionSynchronization(operation, result);
		createdReference.set(created);
		SessionSynchronization existing = synchronizations.putIfAbsent(sessionId, created);
		return existing == null ? created : existing;
	}

	private Mono<AuthSessionState> applySynchronization(
			WebSession session,
			AuthSessionState previous,
			SessionSynchronization synchronization) {
		return synchronization.result()
				.map(updated -> storeCurrent(session, previous, updated))
				.onErrorResume(GatewayAuthenticationService::isUnauthenticated,
						error -> invalidateThenUnauthenticated(session));
	}

	private AuthSessionState storeCurrent(
			WebSession session, AuthSessionState previous, AuthSessionState updated) {
		AuthSessionState current = requireCurrent(session, previous);
		if (!current.accessToken().equals(previous.accessToken())
				&& updated.accessToken().equals(previous.accessToken())) {
			return current;
		}
		if (!current.accessToken().equals(previous.accessToken())
				&& current.accessTokenExpiresAt().isAfter(updated.accessTokenExpiresAt())) {
			return current;
		}
		store(session, updated);
		return updated;
	}

	private Mono<AuthSessionState> upstreamSessionError(UserServiceClientException error) {
		if (error.status().value() == 401) {
			return Mono.error(unauthenticated());
		}
		return Mono.error(mapUpstream(error, false));
	}

	private static boolean isUnauthenticated(Throwable error) {
		return error instanceof ApiProblemException problem
				&& problem.status().value() == HttpStatus.UNAUTHORIZED.value()
				&& "UNAUTHENTICATED".equals(problem.code());
	}

	private void ensureCurrent(WebSession session, AuthSessionState previous) {
		requireCurrent(session, previous);
	}

	private AuthSessionState requireCurrent(WebSession session, AuthSessionState previous) {
		AuthSessionState current = session.getAttribute(SESSION_STATE);
		if (current == null || !current.loginGrant().equals(previous.loginGrant())) {
			throw unauthenticated();
		}
		return current;
	}

	private void validateConfiguration(long absoluteSeconds, String lifetimePolicy) {
		if (absoluteSeconds != properties.sessionAbsoluteTimeoutSeconds()
				|| !properties.sessionLifetimePolicy().equals(lifetimePolicy)) {
			throw configurationMismatch();
		}
	}

	private void validateUpdatedDeadline(AuthSessionState previous, Instant absolute) {
		if (!absolute.equals(previous.absoluteExpiresAt())) {
			throw configurationMismatch();
		}
	}

	private void validateInitialDeadline(Instant absolute) {
		Instant now = clock.instant();
		if (!near(absolute, now.plus(properties.sessionAbsoluteTimeout()))) {
			throw configurationMismatch();
		}
	}

	private void validateIssuedTokenDeadline(Instant expiresAt) {
		Instant expected = clock.instant().plus(properties.accessTokenTtl());
		if (expiresAt == null || !near(expiresAt, expected)) {
			throw configurationMismatch();
		}
	}

	private boolean near(Instant actual, Instant expected) {
		Duration difference = Duration.between(actual, expected).abs();
		return difference.compareTo(properties.userServiceTimeout().plus(IDENTITY_CLOCK_SKEW)) <= 0;
	}

	private void store(WebSession session, AuthSessionState state) {
		session.getAttributes().put(SESSION_STATE, state);
		applySessionDeadline(session, state);
	}

	private void applySessionDeadline(WebSession session, AuthSessionState state) {
		Duration remaining = Duration.between(clock.instant(), state.absoluteExpiresAt());
		session.setMaxIdleTime(remaining.isNegative() ? Duration.ZERO : remaining);
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
			if (!authentication && upstream.status().value() == 401) {
				return new ApiProblemException(
						HttpStatus.SERVICE_UNAVAILABLE,
						"SERVICE_UNAVAILABLE",
						"服务暂不可用",
						"身份信任状态暂时不一致，请稍后重试。");
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

	static ApiProblemException mapDelegatedUpstream(Throwable error, String requestId) {
		if (error instanceof UserServiceClientException upstream && upstream.status().value() == 401) {
			LOGGER.atWarn()
					.addKeyValue("event", "identity_trust_violation")
					.addKeyValue("upstream_service", "user-service")
					.addKeyValue("request_id", requestId)
					.addKeyValue("upstream_code", upstream.code())
					.log("Resource service rejected a prevalidated delegated identity");
		}
		return mapUpstream(error, false);
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

	private static ApiProblemException configurationMismatch() {
		return new ApiProblemException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"SERVICE_UNAVAILABLE",
				"服务配置不一致",
				"身份服务配置不一致，请联系管理员。");
	}

	private enum SynchronizationOperation {
		VALIDATE,
		EXCHANGE
	}

	private record SessionSynchronization(
			SynchronizationOperation operation, Mono<AuthSessionState> result) {
	}
}
