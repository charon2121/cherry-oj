package com.cherryoj.gatewayservice.auth;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** 浏览器会话 → 代表管理员调用下游的 ActingUser；改密码前与非管理员在 gateway 就拒绝。 */
@Component
public final class AdminGatewayAccess {

    private final GatewayAuthenticationService authentication;

    AdminGatewayAccess(GatewayAuthenticationService authentication) {
        this.authentication = authentication;
    }

    public Mono<ActingUser> actingUser(ServerWebExchange exchange, String requestId) {
        return exchange.getSession()
                .flatMap(session -> authentication.current(session, requestId))
                .map(state -> requireAdmin(state, requestId));
    }

    static ActingUser requireAdmin(AuthSessionState state, String requestId) {
        if (state.user().passwordChangeRequired()) {
            throw new ApiProblemException(
                    HttpStatus.FORBIDDEN, "PASSWORD_CHANGE_REQUIRED", "需要修改密码", "完成首次密码修改后才能访问管理功能。");
        }
        if (!"ADMIN".equals(state.user().role())) {
            throw new ApiProblemException(HttpStatus.FORBIDDEN, "FORBIDDEN", "无权访问", "当前身份无权执行此操作。");
        }
        return ActingUser.of(state, requestId);
    }
}
