package com.cherryoj.logging;

import java.util.concurrent.TimeUnit;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;

final class HttpLogEvent {

    private static final Logger log = LoggerFactory.getLogger(HttpLogEvent.class);

    private HttpLogEvent() {
    }

    static TraceFields currentTrace(Tracer tracer) {
        Span span = tracer.currentSpan();
        if (span == null) {
            return TraceFields.EMPTY;
        }
        return new TraceFields(span.context().traceId(), span.context().spanId());
    }

    static void completed(
            String method, String route, int status, long startedNs, TraceFields trace, String requestId) {
        long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNs);
        LoggingEventBuilder event = log.atInfo()
                .addKeyValue("event", "http.server.completed")
                .addKeyValue("http_method", method)
                .addKeyValue("http_route", route == null || route.isBlank() ? "unmatched" : route)
                .addKeyValue("http_status", status)
                .addKeyValue("duration_ms", durationMs);
        if (trace.present()) {
            event.addKeyValue("trace_id", trace.traceId())
                    .addKeyValue("span_id", trace.spanId());
        }
        if (validRequestId(requestId)) {
            event.addKeyValue("request_id", requestId);
        }
        event.log("http.server.completed");
    }

    private static boolean validRequestId(String requestId) {
        if (requestId == null || requestId.length() > 128 || requestId.isBlank()) {
            return false;
        }
        for (int i = 0; i < requestId.length(); i++) {
            char ch = requestId.charAt(i);
            boolean asciiLetter = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z');
            boolean digit = ch >= '0' && ch <= '9';
            if (!asciiLetter && !digit && ch != '_' && ch != '-') {
                return false;
            }
        }
        return true;
    }

    record TraceFields(String traceId, String spanId) {
        static final TraceFields EMPTY = new TraceFields("", "");

        boolean present() {
            return !traceId.isBlank() && !spanId.isBlank();
        }
    }
}
