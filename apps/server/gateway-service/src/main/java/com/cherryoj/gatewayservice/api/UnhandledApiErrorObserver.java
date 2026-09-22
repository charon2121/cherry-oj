package com.cherryoj.gatewayservice.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.ErrorResponse;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebExceptionHandler;

import reactor.core.publisher.Mono;

@Component
@Order(-2)
public final class UnhandledApiErrorObserver implements WebExceptionHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(UnhandledApiErrorObserver.class);

	@Override
	public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
		try {
			if (error instanceof ErrorResponse response && response.getStatusCode().is4xxClientError()) {
				return Mono.error(error);
			}
			// The response may already be committed; never create an ID or change its headers here.
			Object existing = exchange.getAttribute(ApiRequestContext.REQUEST_ID_ATTRIBUTE);
			String requestId = existing instanceof String value && value.matches("req_[0-9a-f]{32}")
					? value : "unavailable";
			UnexpectedErrorFacts facts = UnexpectedErrorFacts.from(error);
			LOGGER.atError()
					.addKeyValue("event", "api.unexpected_error")
					.addKeyValue("request_id", requestId)
					.addKeyValue("error_type", facts.errorType())
					.addKeyValue("exception_types", facts.exceptionTypes())
					.addKeyValue("application_frames", facts.applicationFrames())
					.log("Unhandled browser API error");
		}
		catch (RuntimeException ignored) {
			// A diagnostic failure must not replace the failure handled by the existing error chain.
		}
		return Mono.error(error);
	}
}
