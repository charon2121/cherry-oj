package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.server.MockWebSession;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

class GatewayAuthenticationServiceTests {
    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");
    private static final Instant EXPIRES = NOW.plus(Duration.ofDays(30));
    private static final String SESSION_ID = "s".repeat(43);

    @Test
    void withinThirtySecondsOfTheLastCheckTheSessionIsUsedAsIs() {
        UserServiceClient client = mock(UserServiceClient.class);
        AuthSessionState state = state(NOW.minusSeconds(29), "ADMIN");

        assertThat(service(client).current(session(state), "req_test").block()).isSameAs(state);
        verifyNoInteractions(client);
    }

    @Test
    void afterThirtySecondsTheSessionIsValidatedAndTheUserRefreshed() {
        UserServiceClient client = mock(UserServiceClient.class);
        MockWebSession session = session(state(NOW.minusSeconds(30), "ADMIN"));
        when(client.validate(SESSION_ID, "req_test")).thenReturn(Mono.just(validation("USER")));

        AuthSessionState current = service(client).current(session, "req_test").block();

        assertThat(current.user().role()).as("角色变化在核对后生效").isEqualTo("USER");
        assertThat(current.validatedAt()).isEqualTo(NOW);
        assertThat((AuthSessionState) session.getAttribute(GatewayAuthenticationService.SESSION_STATE))
                .isEqualTo(current);
        assertThat(session.getMaxIdleTime()).isEqualTo(Duration.ofDays(30));
    }

    @Test
    void concurrentRequestsOfOneSessionShareOneValidation() {
        UserServiceClient client = mock(UserServiceClient.class);
        MockWebSession session = session(state(NOW.minusSeconds(60), "ADMIN"));
        Sinks.One<UserServiceClient.SessionValidationResult> upstream = Sinks.one();
        when(client.validate(SESSION_ID, "req_test")).thenReturn(upstream.asMono());
        GatewayAuthenticationService service = service(client);

        Mono<List<AuthSessionState>> both =
                Mono.zip(service.current(session, "req_test"), service.current(session, "req_test"), List::of);
        var result = both.toFuture();
        upstream.tryEmitValue(validation("ADMIN"));

        assertThat(result.join()).hasSize(2);
        verify(client).validate(SESSION_ID, "req_test");
    }

    @Test
    void anInvalidSessionLogsTheBrowserOut() {
        UserServiceClient client = mock(UserServiceClient.class);
        MockWebSession session = session(state(NOW.minusSeconds(60), "ADMIN"));
        when(client.validate(SESSION_ID, "req_test"))
                .thenReturn(Mono.error(new UserServiceClientException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID")));

        assertUnauthenticated(() -> service(client).current(session, "req_test").block());
        assertThat(session.getAttributes()).isEmpty();
    }

    @Test
    void anExpiredSessionIsRejectedWithoutAskingUserService() {
        UserServiceClient client = mock(UserServiceClient.class);
        MockWebSession session =
                session(new AuthSessionState(internalUser("ADMIN").publicView(), SESSION_ID, NOW, NOW.minusSeconds(1)));

        assertUnauthenticated(() -> service(client).current(session, "req_test").block());
        assertThat(session.getAttributes()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void aSessionSavedBeforeTheUpgradeIsTreatedAsLoggedOut() {
        UserServiceClient client = mock(UserServiceClient.class);
        MockWebSession session =
                session(new AuthSessionState(internalUser("ADMIN").publicView(), null, EXPIRES, null));

        assertUnauthenticated(() -> service(client).current(session, "req_test").block());
        assertThat(session.getAttributes()).isEmpty();
        verifyNoInteractions(client);
    }

    @Test
    void anUnreachableUserServiceKeepsTheSessionButFailsTheRequest() {
        UserServiceClient client = mock(UserServiceClient.class);
        AuthSessionState initial = state(NOW.minusSeconds(60), "ADMIN");
        MockWebSession session = session(initial);
        when(client.validate(SESSION_ID, "req_test")).thenReturn(Mono.error(new TimeoutException()));

        assertThatThrownBy(() -> service(client).current(session, "req_test").block())
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        error -> assertThat(error.status()).isEqualTo(HttpStatus.GATEWAY_TIMEOUT));
        assertThat((AuthSessionState) session.getAttribute(GatewayAuthenticationService.SESSION_STATE))
                .isSameAs(initial);
    }

    @Test
    void loginStoresTheSessionUnderANewBrowserSessionId() {
        UserServiceClient client = mock(UserServiceClient.class);
        MockWebSession session = new MockWebSession();
        String before = session.getId();
        when(client.authenticate("admin01", "secret", "req_test"))
                .thenReturn(Mono.just(
                        new UserServiceClient.AuthenticationResult(SESSION_ID, internalUser("ADMIN"), EXPIRES)));

        AuthSessionState state =
                service(client).login(session, "admin01", "secret", "req_test").block();

        assertThat(state.sessionId()).isEqualTo(SESSION_ID);
        assertThat(state.validatedAt()).isEqualTo(NOW);
        assertThat(session.getId()).isNotEqualTo(before);
        assertThat((AuthSessionState) session.getAttribute(GatewayAuthenticationService.SESSION_STATE))
                .isEqualTo(state);
    }

    @Test
    void logoutSucceedsEvenWhenUserServiceAlreadyForgotTheSession() {
        UserServiceClient client = mock(UserServiceClient.class);
        MockWebSession session = session(state(NOW, "ADMIN"));
        when(client.revoke(SESSION_ID, "req_test"))
                .thenReturn(Mono.error(new UserServiceClientException(HttpStatus.UNAUTHORIZED, "SESSION_INVALID")));

        service(client).logout(session, "req_test").block();

        assertThat(session.isExpired()).isTrue();
        verify(client, never()).validate(SESSION_ID, "req_test");
    }

    private static void assertUnauthenticated(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOfSatisfying(
                        ApiProblemException.class,
                        error -> assertThat(error.code()).isEqualTo("UNAUTHENTICATED"));
    }

    private static GatewayAuthenticationService service(UserServiceClient client) {
        return new GatewayAuthenticationService(client, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MockWebSession session(AuthSessionState state) {
        MockWebSession session = new MockWebSession();
        session.getAttributes().put(GatewayAuthenticationService.SESSION_STATE, state);
        return session;
    }

    private static AuthSessionState state(Instant validatedAt, String role) {
        return new AuthSessionState(internalUser(role).publicView(), SESSION_ID, EXPIRES, validatedAt);
    }

    private static UserServiceClient.SessionValidationResult validation(String role) {
        return new UserServiceClient.SessionValidationResult(internalUser(role), EXPIRES);
    }

    private static UserServiceClient.InternalUser internalUser(String role) {
        LocalDateTime created = LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC);
        return new UserServiceClient.InternalUser(
                "019c8e42-7f70-7000-8000-000000000001", "admin01", role, "ACTIVE", false, created, created, 0);
    }
}
