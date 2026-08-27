package com.cherryoj.gatewayservice.auth;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiRequestContext;

import reactor.core.publisher.Mono;

@Component
final class CsrfProblemWriter implements ServerAccessDeniedHandler {

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
		String requestId = ApiRequestContext.requestId(exchange);
		String body = "{" +
				"\"type\":\"urn:cherry-oj:problem:csrf-rejected\"," +
				"\"title\":\"CSRF 校验失败\"," +
				"\"status\":403," +
				"\"detail\":\"请求缺少有效的 CSRF token。\"," +
				"\"instance\":\"urn:cherry-oj:request:" + requestId + "\"," +
				"\"code\":\"CSRF_REJECTED\"," +
				"\"meta\":{\"requestId\":\"" + requestId + "\"}}";
		byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
		exchange.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
		exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
		exchange.getResponse().getHeaders().set(ApiRequestContext.REQUEST_ID_HEADER, requestId);
		DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(bytes);
		return exchange.getResponse().writeWith(Mono.just(buffer));
	}
}
