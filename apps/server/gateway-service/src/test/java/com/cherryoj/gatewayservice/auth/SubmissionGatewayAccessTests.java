package com.cherryoj.gatewayservice.auth;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class SubmissionGatewayAccessTests {
    @Test void ordinaryUserCanSubmitButMustChangePasswordCannot() {
        var authentication=mock(GatewayAuthenticationService.class);
        var exchange=MockServerWebExchange.from(MockServerHttpRequest.get("/api/submissions/x").build());
        var session=exchange.getSession().block();
        var access=new SubmissionGatewayAccess(authentication);
        String requestId="req_"+"a".repeat(32);
        var now=Instant.now();
        var user=mock(UserAccountData.class);
        when(user.passwordChangeRequired()).thenReturn(false);
        var userId=java.util.UUID.randomUUID();
        when(user.id()).thenReturn(userId.toString());
        var state=new AuthSessionState(user,"grant","delegated",now.plusSeconds(60),now.plusSeconds(3600));
        when(authentication.current(session,requestId,true)).thenReturn(Mono.just(state));
        assertEquals("delegated",access.identity(exchange,requestId).block().accessToken());
        assertEquals("SESSION_CHANGED",assertThrows(ApiProblemException.class,
                () -> access.identity(exchange,requestId,java.util.UUID.randomUUID()).block()).code());
        assertEquals("delegated",access.identity(exchange,requestId,userId).block().accessToken());
        when(user.passwordChangeRequired()).thenReturn(true);
        assertEquals("PASSWORD_CHANGE_REQUIRED",assertThrows(ApiProblemException.class,
                () -> access.identity(exchange,requestId).block()).code());
    }
}
