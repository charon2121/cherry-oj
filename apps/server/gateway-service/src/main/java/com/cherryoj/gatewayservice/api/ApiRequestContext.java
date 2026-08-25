package com.cherryoj.gatewayservice.api;

import java.util.UUID;

import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ServerWebExchange;

public final class ApiRequestContext {

	public static final String REQUEST_ID_HEADER = "X-Request-Id";
	static final String REQUEST_ID_ATTRIBUTE = ApiRequestContext.class.getName() + ".requestId";

	private ApiRequestContext() {
	}

	public static String requestId(ServerWebExchange exchange) {
		Object existing = exchange.getAttribute(REQUEST_ID_ATTRIBUTE);
		if (existing instanceof String requestId) {
			return requestId;
		}

		String requestId = newRequestId();
		setRequestId(exchange, requestId);
		return requestId;
	}

	static String newRequestId() {
		return "req_" + UUID.randomUUID().toString().replace("-", "");
	}

	static void setRequestId(ServerWebExchange exchange, String requestId) {
		exchange.getAttributes().put(REQUEST_ID_ATTRIBUTE, requestId);
		HttpHeaders headers = exchange.getResponse().getHeaders();
		headers.set(REQUEST_ID_HEADER, requestId);
	}

}
