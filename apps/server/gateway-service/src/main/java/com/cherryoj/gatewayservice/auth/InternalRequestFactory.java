package com.cherryoj.gatewayservice.auth;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import com.cherryoj.gatewayservice.api.ApiRequestContext;

/** The only boundary allowed to add identity and correlation headers to internal requests. */
@Component
public final class InternalRequestFactory {

	public WebClient.RequestHeadersSpec<?> request(
			WebClient.RequestHeadersSpec<?> request, String requestId) {
		return request.headers(headers -> headers.set(ApiRequestContext.REQUEST_ID_HEADER, requestId));
	}

	public WebClient.RequestBodySpec request(
			WebClient.RequestBodySpec request, String requestId) {
		request.headers(headers -> headers.set(ApiRequestContext.REQUEST_ID_HEADER, requestId));
		return request;
	}

	/** 以 gateway 自己的服务身份调用（没有用户在场的内部认证端点，例如登录与换取委托令牌）。 */
	public WebClient.RequestBodySpec asService(WebClient.RequestBodySpec request, String requestId, String serviceToken) {
		request.headers(headers -> {
			headers.setBearerAuth(serviceToken);
			headers.set(ApiRequestContext.REQUEST_ID_HEADER, requestId);
		});
		return request;
	}

	public WebClient.RequestHeadersSpec<?> authenticated(
			WebClient.RequestHeadersSpec<?> request, DelegatedIdentity identity) {
		return request.headers(headers -> {
			headers.setBearerAuth(identity.accessToken());
			headers.set(ApiRequestContext.REQUEST_ID_HEADER, identity.requestId());
		});
	}

	public WebClient.RequestBodySpec authenticated(
			WebClient.RequestBodySpec request, DelegatedIdentity identity) {
		request.headers(headers -> {
			headers.setBearerAuth(identity.accessToken());
			headers.set(ApiRequestContext.REQUEST_ID_HEADER, identity.requestId());
		});
		return request;
	}
}
