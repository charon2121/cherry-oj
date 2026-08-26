package com.cherryoj.logging;

import io.micrometer.tracing.Tracer;
import io.micrometer.observation.Observation;
import io.micrometer.observation.contextpropagation.ObservationThreadLocalAccessor;

import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

final class ReactiveHttpLoggingFilter implements WebFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String BEST_MATCHING_PATTERN_ATTRIBUTE =
            "org.springframework.web.reactive.HandlerMapping.bestMatchingPattern";

    private final Tracer tracer;

    ReactiveHttpLoggingFilter(Tracer tracer) {
        this.tracer = tracer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        return Mono.deferContextual(context -> {
            long startedNs = System.nanoTime();
            Observation observation = context.getOrDefault(ObservationThreadLocalAccessor.KEY, null);
            HttpLogEvent.TraceFields trace = currentTrace(observation);
            return chain.filter(exchange).doFinally(signal -> {
                Object route = exchange.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
                HttpLogEvent.completed(
                        exchange.getRequest().getMethod().name(),
                        route == null ? null : route.toString(),
                        exchange.getResponse().getStatusCode() == null
                                ? 200
                                : exchange.getResponse().getStatusCode().value(),
                        startedNs,
                        trace,
                        exchange.getResponse().getHeaders().getFirst(REQUEST_ID_HEADER));
            });
        });
    }

    private HttpLogEvent.TraceFields currentTrace(Observation observation) {
        if (observation == null) {
            return HttpLogEvent.currentTrace(tracer);
        }
        try (Observation.Scope ignored = observation.openScope()) {
            return HttpLogEvent.currentTrace(tracer);
        }
    }
}
