package com.cherryoj.identity.resource;

import com.cherryoj.identity.token.CallToken;
import com.cherryoj.identity.token.CallTokenException;
import com.cherryoj.identity.token.PublicKeyDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 校验调用令牌并执行端点策略（协议 §3.2、§4），按协议的顺序给出失败：令牌无效 → 调用方不允许 → 缺用户 → 角色不够。
 * 不是 Spring bean：作为 bean 时 Spring Boot 会把它挂到所有请求上，绕过安全链；由 CallSecurity 在链内创建。
 */
public final class CallTokenFilter extends OncePerRequestFilter {
    private static final String BEARER = "Bearer ";
    private static final AntPathMatcher PATHS = new AntPathMatcher();

    private final EndpointPolicies policies;
    private final PublicKeyDirectory keys;
    private final String self;
    private final List<String> publicPaths;
    private final Clock clock;

    CallTokenFilter(
            EndpointPolicies policies, PublicKeyDirectory keys, String self, List<String> publicPaths, Clock clock) {
        this.policies = policies;
        this.keys = keys;
        this.self = self;
        this.publicPaths = List.copyOf(publicPaths);
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = path(request);
        return publicPaths.stream().anyMatch(pattern -> PATHS.match(pattern, path));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER) || header.length() > 8192) {
            CallRejection.CALL_UNAUTHENTICATED.write(request, response, null, "MISSING_TOKEN");
            return;
        }
        CallToken.Verified call;
        try {
            call = CallToken.verify(header.substring(BEARER.length()), keys, self, clock.instant());
        } catch (CallTokenException error) {
            CallRejection.CALL_UNAUTHENTICATED.write(request, response, null, error.reason());
            return;
        }
        var policy = policies.match(request.getMethod(), path(request));
        if (policy.isEmpty() || !policy.get().callers().contains(call.caller())) {
            CallRejection.CALLER_FORBIDDEN.write(
                    request, response, call.caller(), policy.isEmpty() ? "NO_POLICY" : "CALLER_NOT_ALLOWED");
            return;
        }
        var requirement = policy.get().user();
        var user = requirement == EndpointPolicies.UserRequirement.NONE
                ? null
                : call.user().orElse(null);
        if (requirement != EndpointPolicies.UserRequirement.NONE && user == null) {
            CallRejection.USER_REQUIRED.write(request, response, call.caller(), "NO_USER");
            return;
        }
        if (requirement == EndpointPolicies.UserRequirement.ADMIN && !user.admin()) {
            CallRejection.FORBIDDEN.write(request, response, call.caller(), "ROLE");
            return;
        }
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new CallAuthentication(call.caller(), user));
        SecurityContextHolder.setContext(context);
        try {
            chain.doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String context = request.getContextPath();
        return context != null && !context.isEmpty() && uri.startsWith(context) ? uri.substring(context.length()) : uri;
    }
}
