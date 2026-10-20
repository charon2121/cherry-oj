package com.cherryoj.gatewayservice.auth;

import com.cherryoj.identity.token.CallUser;

/** 这次浏览器请求代表的用户：会话核对后交给下游客户端，签进调用令牌的 user。 */
public record ActingUser(CallUser user, String requestId) {
    public ActingUser {
        if (user == null) {
            throw new IllegalArgumentException("user must not be null");
        }
        if (requestId == null || !requestId.matches("^req_[0-9a-f]{32}$")) {
            throw new IllegalArgumentException("requestId is invalid");
        }
    }

    static ActingUser of(AuthSessionState state, String requestId) {
        return new ActingUser(new CallUser(state.user().id(), state.user().role()), requestId);
    }
}
