package com.cherryoj.gatewayservice.auth;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.identity.token.CallTokenSigner;
import com.cherryoj.identity.token.CallUser;
import org.springframework.web.reactive.function.client.WebClient;

/** 给发往一个下游服务的请求加上调用令牌与请求编号；这是 gateway 唯一添加身份头的地方。每个下游客户端一个。 */
public final class InternalRequests {
    private final CallTokenSigner calls;
    private final String audience;

    public InternalRequests(CallTokenSigner calls, String audience) {
        this.calls = calls;
        this.audience = audience;
    }

    /** 不代表用户的调用（登录、会话核对、公开题目）。 */
    public WebClient.RequestHeadersSpec<?> request(WebClient.RequestHeadersSpec<?> request, String requestId) {
        return request.headers(headers -> headers(headers, null, requestId));
    }

    public WebClient.RequestBodySpec request(WebClient.RequestBodySpec request, String requestId) {
        request.headers(headers -> headers(headers, null, requestId));
        return request;
    }

    /** 代表用户的调用。 */
    public WebClient.RequestHeadersSpec<?> authenticated(WebClient.RequestHeadersSpec<?> request, ActingUser acting) {
        return request.headers(headers -> headers(headers, acting.user(), acting.requestId()));
    }

    public WebClient.RequestBodySpec authenticated(WebClient.RequestBodySpec request, ActingUser acting) {
        request.headers(headers -> headers(headers, acting.user(), acting.requestId()));
        return request;
    }

    private void headers(org.springframework.http.HttpHeaders headers, CallUser user, String requestId) {
        // 每次请求现签：令牌只活 60 秒，WebClient 重试或延迟发送也不会超出。
        headers.set("Authorization", calls.authorization(audience, user));
        headers.set(ApiRequestContext.REQUEST_ID_HEADER, requestId);
    }
}
