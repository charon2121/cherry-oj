package com.cherryoj.gatewayservice.auth;

import java.util.function.Function;
import java.util.function.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebSession;

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

/** Resolves a browser session into the short-lived delegated token used by admin clients. */
@Component
public final class AdminGatewayAccess {

	private static final Logger LOGGER = LoggerFactory.getLogger(AdminGatewayAccess.class);

	private final GatewayAuthenticationService authentication;

	AdminGatewayAccess(GatewayAuthenticationService authentication) {
		this.authentication = authentication;
	}

	public Mono<String> accessToken(ServerWebExchange exchange, String requestId) {
		return exchange.getSession()
				.flatMap(session -> authentication.current(session, requestId, true))
				.map(AdminGatewayAccess::requireAdmin);
	}

	/** Runs one side-effect-free admin read and recovers one explicit delegated-token rejection. */
	public <T> Mono<T> readWithRecovery(
			ServerWebExchange exchange,
			String requestId,
			Function<String, Mono<T>> action,
			Predicate<Throwable> tokenRejected,
			Function<Throwable, ApiProblemException> errorMapper) {
		return exchange.getSession().flatMap(session -> authentication.current(session, requestId, true)
				.map(AdminGatewayAccess::requireAdminState)
				.flatMap(state -> readWithRecovery(
						session, state, requestId, action, tokenRejected, errorMapper)));
	}

	private <T> Mono<T> readWithRecovery(
			WebSession session,
			AuthSessionState rejectedState,
			String requestId,
			Function<String, Mono<T>> action,
			Predicate<Throwable> tokenRejected,
			Function<Throwable, ApiProblemException> errorMapper) {
		return Mono.defer(() -> action.apply(rejectedState.accessToken()))
				.onErrorResume(error -> {
					if (!tokenRejected.test(error)) {
						return Mono.error(errorMapper.apply(error));
					}
					log("resource_token_rejected", requestId, "retrying");
					return authentication.recoverRejectedAccessToken(
							session, rejectedState, requestId)
							.flatMap(refreshed -> Mono.defer(() -> action.apply(refreshed.accessToken()))
									.onErrorMap(retried -> tokenRejected.test(retried)
											? freshTokenRejected() : errorMapper.apply(retried)))
							.doOnSuccess(ignored -> log(
									"token_recovery_succeeded", requestId, "recovered"))
							.doOnError(recoveryError -> log(
									"token_recovery_failed", requestId, failureKind(recoveryError)));
				});
	}

	static String requireAdmin(AuthSessionState state) {
		return requireAdminState(state).accessToken();
	}

	private static AuthSessionState requireAdminState(AuthSessionState state) {
		if (state.user().passwordChangeRequired()) {
			throw new ApiProblemException(
					HttpStatus.FORBIDDEN,
					"PASSWORD_CHANGE_REQUIRED",
					"需要修改密码",
					"完成首次密码修改后才能访问管理功能。");
		}
		if (!"ADMIN".equals(state.user().role())) {
			throw new ApiProblemException(
					HttpStatus.FORBIDDEN,
					"FORBIDDEN",
					"无权访问",
					"当前身份无权执行此操作。");
		}
		return state;
	}

	private static ApiProblemException freshTokenRejected() {
		return new ApiProblemException(
				HttpStatus.SERVICE_UNAVAILABLE,
				"SERVICE_UNAVAILABLE",
				"服务暂不可用",
				"身份服务暂时无法确认当前权限，请稍后重试。");
	}

	private static String failureKind(Throwable error) {
		if (error instanceof ApiProblemException problem) {
			return problem.code();
		}
		return "UPSTREAM_ERROR";
	}

	private static void log(String event, String requestId, String result) {
		LOGGER.atWarn()
				.addKeyValue("event", event)
				.addKeyValue("request_id", requestId)
				.addKeyValue("result", result)
				.log("Admin delegated-token recovery event");
	}
}
