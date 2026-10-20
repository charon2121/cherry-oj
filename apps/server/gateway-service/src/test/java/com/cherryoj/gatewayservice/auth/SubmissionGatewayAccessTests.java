package com.cherryoj.gatewayservice.auth;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class SubmissionGatewayAccessTests {
    @Test
    void ordinaryUserCanSubmitButMustChangePasswordCannot() {
        var authentication = mock(GatewayAuthenticationService.class);
        var exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/submissions/x").build());
        var session = exchange.getSession().block();
        var access = new SubmissionGatewayAccess(authentication);
        String requestId = "req_" + "a".repeat(32);
        var now = Instant.now();
        var user = mock(UserAccountData.class);
        when(user.passwordChangeRequired()).thenReturn(false);
        var userId = java.util.UUID.randomUUID();
        when(user.id()).thenReturn(userId.toString());
        when(user.role()).thenReturn("USER");
        var state = new AuthSessionState(user, "s".repeat(43), now.plusSeconds(3600), now);
        when(authentication.current(session, requestId)).thenReturn(Mono.just(state));
        assertEquals(
                userId.toString(),
                access.identity(exchange, requestId).block().user().id());
        assertEquals(
                "SESSION_CHANGED",
                assertThrows(
                                ApiProblemException.class,
                                () -> access.identity(exchange, requestId, java.util.UUID.randomUUID())
                                        .block())
                        .code());
        assertEquals(
                userId.toString(),
                access.identity(exchange, requestId, userId).block().user().id());
        when(user.passwordChangeRequired()).thenReturn(true);
        assertEquals(
                "PASSWORD_CHANGE_REQUIRED",
                assertThrows(
                                ApiProblemException.class,
                                () -> access.identity(exchange, requestId).block())
                        .code());
    }
}
