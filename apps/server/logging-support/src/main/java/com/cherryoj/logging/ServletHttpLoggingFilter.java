package com.cherryoj.logging;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.filter.OncePerRequestFilter;

final class ServletHttpLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String BEST_MATCHING_PATTERN_ATTRIBUTE =
            "org.springframework.web.servlet.HandlerMapping.bestMatchingPattern";

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long startedNs = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            Object route = request.getAttribute(BEST_MATCHING_PATTERN_ATTRIBUTE);
            HttpLogEvent.completed(
                    request.getMethod(),
                    route == null ? null : route.toString(),
                    response.getStatus(),
                    startedNs,
                    HttpLogEvent.TraceFields.EMPTY,
                    request.getHeader(REQUEST_ID_HEADER));
        }
    }
}
