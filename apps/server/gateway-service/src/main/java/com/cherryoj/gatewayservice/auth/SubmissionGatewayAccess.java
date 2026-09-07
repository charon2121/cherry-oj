package com.cherryoj.gatewayservice.auth;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/** Resolves the current user; ADMIN has no broader submission ownership rights. */
@Component
public final class SubmissionGatewayAccess {
    private final GatewayAuthenticationService authentication;
    SubmissionGatewayAccess(GatewayAuthenticationService authentication) { this.authentication=authentication; }
    public Mono<DelegatedIdentity> identity(ServerWebExchange exchange,String requestId) {
        return identity(exchange,requestId,null);
    }
    public Mono<DelegatedIdentity> identity(ServerWebExchange exchange,String requestId,java.util.UUID expectedUserId) {
        return exchange.getSession().flatMap(session -> authentication.current(session,requestId,true))
                .map(state -> {
                    if(expectedUserId!=null && !expectedUserId.toString().equals(state.user().id().toString()))
                        throw new ApiProblemException(HttpStatus.CONFLICT,"SESSION_CHANGED","登录账号已切换",
                                "当前登录账号与编辑器所属账号不同，请刷新登录状态后继续。原代码不会提交到其他账号。");
                    if(state.user().passwordChangeRequired()) throw new ApiProblemException(HttpStatus.FORBIDDEN,
                            "PASSWORD_CHANGE_REQUIRED","需要修改密码","完成密码修改后才能提交和查看结果。");
                    return new DelegatedIdentity(state.accessToken(),state.accessTokenExpiresAt(),requestId);
                });
    }
}
