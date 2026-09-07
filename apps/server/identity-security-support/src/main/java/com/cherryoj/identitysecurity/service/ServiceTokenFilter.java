package com.cherryoj.identitysecurity.service;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/** 仅装入明确匹配内部端点的 SecurityFilterChain，不注册为全局 Servlet filter。 */
public final class ServiceTokenFilter extends OncePerRequestFilter {
    private final ServiceCredentials credentials;
    private final String caller;

    public ServiceTokenFilter(String caller, ServiceCredentials credentials) {
        if (caller == null || caller.isBlank()) throw new IllegalArgumentException("caller required");
        this.caller = caller;
        this.credentials = java.util.Objects.requireNonNull(credentials);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var headers = Collections.list(request.getHeaders("Authorization"));
        if (headers.size() != 1 || !credentials.accepts(headers.getFirst())) {
            response.setStatus(401);
            response.setContentType("application/problem+json");
            response.setHeader("Cache-Control", "no-store");
            response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"Service authentication required\",\"status\":401,\"code\":\"SERVICE_UNAUTHORIZED\"}");
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(caller, null,
                List.of(new SimpleGrantedAuthority("SERVICE_CALL"))));
        SecurityContextHolder.setContext(context);
        response.setHeader("Cache-Control", "no-store");
        try { chain.doFilter(request, response); }
        finally { SecurityContextHolder.clearContext(); }
    }
}
