package com.cherryoj.gatewayservice.trial;

import org.springframework.stereotype.Component;
import org.springframework.web.server.*;
import org.springframework.core.io.buffer.*;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import reactor.core.publisher.*;
import org.springframework.http.HttpStatus;

@Component
public class RunBodyLimit implements WebFilter {

	public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
		if (!"/api/custom-runs".equals(exchange.getRequest().getPath().value())
				|| !"POST".equals(exchange.getRequest().getMethod().name()))
			return chain.filter(exchange);
		return DataBufferUtils.join(exchange.getRequest().getBody(), 2 * 1024 * 1024)
			.defaultIfEmpty(exchange.getResponse().bufferFactory().wrap(new byte[0]))
			.flatMap(buffer -> {
				byte[] bytes = new byte[buffer.readableByteCount()];
				buffer.read(bytes);
				DataBufferUtils.release(buffer);
				var request = new ServerHttpRequestDecorator(exchange.getRequest()) {
					@Override
					public Flux<DataBuffer> getBody() {
						return Flux.defer(() -> Flux.just(exchange.getResponse().bufferFactory().wrap(bytes)));
					}
				};
				return chain.filter(exchange.mutate().request(request).build());
			})
			.onErrorMap(DataBufferLimitException.class,
					e -> CustomRunController.error(HttpStatus.PAYLOAD_TOO_LARGE, "RUN_INPUT_TOO_LARGE", "请求正文过大。"));
	}

}
