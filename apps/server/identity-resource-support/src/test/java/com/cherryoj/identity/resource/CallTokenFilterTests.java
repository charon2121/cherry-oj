package com.cherryoj.identity.resource;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.identity.token.CallToken;
import com.cherryoj.identity.token.CallUser;
import com.cherryoj.identity.token.PublicKeyDirectory;
import com.cherryoj.identity.token.ServiceNames;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class CallTokenFilterTests {
    private static final KeyPair GATEWAY = rsa();
    private static final KeyPair JUDGING = rsa();
    private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
    private static final CallUser USER = new CallUser("d0e35399-6487-4ac8-8138-8d5bd60eb003", CallUser.USER);
    private static final CallUser ADMIN = new CallUser("0f6f9a0e-5b0c-4f44-9d0a-0b8c4a8d2e11", CallUser.ADMIN);

    private final CallTokenFilter filter = new CallTokenFilter(
            EndpointPolicies.builder()
                    .endpoint(HttpMethod.GET, "/internal/judging/problems/*/test-data")
                    .callers(ServiceNames.JUDGING)
                    .withoutUser()
                    .endpoint("/internal/admin/**")
                    .callers(ServiceNames.GATEWAY)
                    .admin()
                    .endpoint("/api/things/**")
                    .callers(ServiceNames.GATEWAY)
                    .anyUser()
                    .build(),
            PublicKeyDirectory.of(Map.of(
                    ServiceNames.GATEWAY, (RSAPublicKey) GATEWAY.getPublic(),
                    ServiceNames.JUDGING, (RSAPublicKey) JUDGING.getPublic())),
            ServiceNames.PROBLEM,
            CallSecurity.PUBLIC_PATHS,
            Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void anAllowedCallReachesTheHandlerWithItsCallerAndUser() throws Exception {
        var seen = new AtomicReference<CallAuthentication>();
        var response = call("GET", "/api/things/1", token(GATEWAY, USER), seen);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get().caller()).isEqualTo(ServiceNames.GATEWAY);
        assertThat(seen.get().user()).contains(USER);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();

        var withoutUser = new AtomicReference<CallAuthentication>();
        call("GET", "/internal/judging/problems/7/test-data", token(JUDGING, USER), withoutUser);
        assertThat(withoutUser.get().user())
                .as("an endpoint without a user ignores user")
                .isEmpty();
    }

    @Test
    void failuresFollowTheProtocolOrderAndShape() throws Exception {
        rejects("GET", "/api/things/1", null, 401, "CALL_UNAUTHENTICATED");
        rejects("GET", "/api/things/1", "garbage", 401, "CALL_UNAUTHENTICATED");
        rejects("GET", "/api/things/1", token(GATEWAY, ServiceNames.SUBMISSION, USER), 401, "CALL_UNAUTHENTICATED");
        rejects("GET", "/api/things/1", token(JUDGING, USER), 403, "CALLER_FORBIDDEN");
        rejects("GET", "/internal/unknown", token(GATEWAY, USER), 403, "CALLER_FORBIDDEN");
        rejects("POST", "/internal/judging/problems/7/test-data", token(JUDGING, null), 403, "CALLER_FORBIDDEN");
        rejects("GET", "/api/things/1", token(GATEWAY, null), 401, "USER_REQUIRED");
        rejects("GET", "/internal/admin/problems", token(GATEWAY, USER), 403, "FORBIDDEN");

        var admin = call("GET", "/internal/admin/problems", token(GATEWAY, ADMIN), new AtomicReference<>());
        assertThat(admin.getStatus()).isEqualTo(200);
        var health = call("GET", "/actuator/health/readiness", null, new AtomicReference<>());
        assertThat(health.getStatus()).isEqualTo(200);
    }

    private void rejects(String method, String path, String token, int status, String code) throws Exception {
        var response = call(method, path, token, new AtomicReference<>());
        assertThat(response.getStatus()).as(path + " " + code).isEqualTo(status);
        assertThat(response.getContentType()).isEqualTo("application/problem+json;charset=UTF-8");
        assertThat(response.getContentAsString())
                .contains("\"type\":\"about:blank\"", "\"status\":" + status, "\"code\":\"" + code + "\"");
    }

    /** 发给本服务（problem-service）的令牌。 */
    private static String token(KeyPair key, CallUser user) {
        return token(key, ServiceNames.PROBLEM, user);
    }

    private static String token(KeyPair key, String audience, CallUser user) {
        String caller = key == GATEWAY ? ServiceNames.GATEWAY : ServiceNames.JUDGING;
        return CallToken.sign(caller, (RSAPrivateKey) key.getPrivate(), audience, user, NOW);
    }

    private MockHttpServletResponse call(
            String method, String path, String token, AtomicReference<CallAuthentication> seen) throws Exception {
        var request = new MockHttpServletRequest(method, path);
        if (token != null) request.addHeader("Authorization", "Bearer " + token);
        var response = new MockHttpServletResponse();
        filter.doFilter(
                request, response, new MockFilterChain(new jakarta.servlet.http.HttpServlet() {}, (req, res, chain) -> {
                    if (SecurityContextHolder.getContext().getAuthentication() instanceof CallAuthentication call)
                        seen.set(call);
                }));
        return response;
    }

    private static KeyPair rsa() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
