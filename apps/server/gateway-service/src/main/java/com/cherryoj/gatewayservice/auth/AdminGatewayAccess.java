package com.cherryoj.gatewayservice.auth;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiProblemException;

import reactor.core.publisher.Mono;

/** Resolves a browser session into the short-lived delegated token used by admin clients. */
@Component
public final class AdminGatewayAccess {

	private final GatewayAuthenticationService authentication;

	AdminGatewayAccess(GatewayAuthenticationService authentication) {
		this.authentication = authentication;
	}

	public Mono<DelegatedIdentity> delegatedIdentity(ServerWebExchange exchange, String requestId) {
		return exchange.getSession()
				.flatMap(session -> authentication.current(session, requestId, true))
				.map(state -> requireAdmin(state, requestId));
	}

	static DelegatedIdentity requireAdmin(AuthSessionState state, String requestId) {
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
		return new DelegatedIdentity(state.accessToken(), state.accessTokenExpiresAt(), requestId);
	}
}
