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
