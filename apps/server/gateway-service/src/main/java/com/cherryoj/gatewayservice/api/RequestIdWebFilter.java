package com.cherryoj.gatewayservice.api;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestIdWebFilter implements WebFilter {

	@Override
	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		String requestId = ApiRequestContext.newRequestId();
		ApiRequestContext.setRequestId(exchange, requestId);
		ServerWebExchange downstreamExchange = exchange.mutate()
				.request(exchange.getRequest().mutate()
						.headers(headers -> headers.set(ApiRequestContext.REQUEST_ID_HEADER, requestId))
						.build())
				.build();
		return chain.filter(downstreamExchange);
	}

}
