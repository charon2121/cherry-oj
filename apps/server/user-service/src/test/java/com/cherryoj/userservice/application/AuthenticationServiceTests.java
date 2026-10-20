package com.cherryoj.userservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.domain.AuthenticationFailedException;
import com.cherryoj.userservice.domain.LoginSession;
import com.cherryoj.userservice.domain.SessionInvalidException;
import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserRole;
import com.cherryoj.userservice.domain.UserStatus;
import com.cherryoj.userservice.domain.UsernamePolicy;
import com.cherryoj.userservice.domain.UuidV7;
import com.cherryoj.userservice.persistence.LoginSessionMapper;
import com.cherryoj.userservice.persistence.UserAccountMapper;
import com.cherryoj.userservice.security.PasswordService;
import com.cherryoj.userservice.security.SessionIdCodec;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class AuthenticationServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void authenticationIssuesDatabaseRepresentableDeadlineWithoutExtendingLifetime() {
        // Include sub-microsecond values and the final nanosecond before the next second.
        Map<Integer, Integer> cases =
                Map.of(0, 0, 1, 0, 123456000, 123456000, 123456789, 123456000, 999999999, 999999000);
        cases.forEach((inputNs, expectedNs) -> {
            Instant instant = NOW.plusNanos(inputNs);
            LocalDateTime now = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
            UserAccountMapper accounts = mock(UserAccountMapper.class);
            LoginSessionMapper sessions = mock(LoginSessionMapper.class);
            PasswordService passwords = mock(PasswordService.class);
            SessionIdCodec sessionIds = mock(SessionIdCodec.class);
            UuidV7 ids = mock(UuidV7.class);
            UUID sessionId = UUID.fromString("019c8e42-7f70-7000-8000-000000000002");
            byte[] digest = {1, 2, 3};
            PlatformTransactionManager transactions = mock(PlatformTransactionManager.class);
            when(transactions.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
            when(accounts.findByNormalizedUsernameForUpdate("learner01")).thenReturn(account());
            when(passwords.matches("correct-password", "stored-hash")).thenReturn(true);
            when(sessionIds.generate()).thenReturn("session-value");
            when(sessionIds.digest("session-value")).thenReturn(digest);
            when(ids.next()).thenReturn(sessionId);
            AuthenticationService authentication = new AuthenticationService(
                    accounts,
                    sessions,
                    new UsernamePolicy(),
                    passwords,
                    sessionIds,
                    mock(AuditService.class),
                    ids,
                    properties(),
                    Clock.fixed(instant, ZoneOffset.UTC),
                    transactions);

            var result = authentication.authenticate("Learner01", "correct-password");

            LocalDateTime expected = localNow().plusDays(30).withNano(expectedNs);
            assertThat(result.sessionId()).isEqualTo("session-value");
            assertThat(LocalDateTime.ofInstant(result.sessionExpiresAt(), ZoneOffset.UTC))
                    .as("input fraction ns=%s", inputNs)
                    .isEqualTo(expected)
                    .isBeforeOrEqualTo(now.plusDays(30));
            verify(sessions)
                    .insert(
                            sessionId.toString(),
                            account().id(),
                            digest,
                            account().sessionVersion(),
                            now,
                            expected);
            verify(accounts).recordLoginSuccess(account().id(), now);
        });
    }

    @Test
    void failedPasswordCommitsBackoffBeforeReturningGenericFailure() {
        UserAccountMapper accounts = mock(UserAccountMapper.class);
        PasswordService passwords = mock(PasswordService.class);
        AuditService audit = mock(AuditService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        when(accounts.findByNormalizedUsernameForUpdate("learner01")).thenReturn(account());
        when(passwords.matches("wrong-password", "stored-hash")).thenReturn(false);
        AuthenticationService service = service(
                accounts,
                mock(LoginSessionMapper.class),
                passwords,
                mock(SessionIdCodec.class),
                audit,
                transactionManager);

        assertThatThrownBy(() -> service.authenticate("Learner01", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class);

        LocalDateTime now = localNow();
        verify(accounts).recordLoginFailure(account().id(), now, now.plusMinutes(15));
        verify(transactionManager).commit(transaction);
    }

    @Test
    void validateReturnsTheUserAndTheUnchangedAbsoluteDeadline() {
        LoginSessionMapper sessions = mock(LoginSessionMapper.class);
        SessionIdCodec sessionIds = mock(SessionIdCodec.class);
        byte[] digest = {1, 2, 3};
        LoginSession session = loginSession();
        when(sessionIds.digest("session-value")).thenReturn(digest);
        when(sessions.findActiveByHash(digest, localNow())).thenReturn(session);

        SessionValidation result = service(sessions, sessionIds).validate("session-value");

        assertThat(result.user().id()).isEqualTo(account().id());
        assertThat(result.sessionExpiresAt())
                .isEqualTo(session.absoluteExpiresAt().toInstant(ZoneOffset.UTC));
        verify(sessions).touch(session.sessionId(), localNow());
    }

    @Test
    void anUnknownSessionIsInvalidForValidateAndRevoke() {
        LoginSessionMapper sessions = mock(LoginSessionMapper.class);
        SessionIdCodec sessionIds = mock(SessionIdCodec.class);
        when(sessionIds.digest(any())).thenReturn(new byte[32]);
        var service = service(sessions, sessionIds);

        assertThatThrownBy(() -> service.validate("unknown")).isInstanceOf(SessionInvalidException.class);
        assertThatThrownBy(() -> service.revoke("unknown")).isInstanceOf(SessionInvalidException.class);
        verify(sessions, never()).revokeCurrent(any(), any(), any());
    }

    private static AuthenticationService service(LoginSessionMapper sessions, SessionIdCodec sessionIds) {
        return service(
                mock(UserAccountMapper.class),
                sessions,
                mock(PasswordService.class),
                sessionIds,
                mock(AuditService.class),
                mock(PlatformTransactionManager.class));
    }

    private static AuthenticationService service(
            UserAccountMapper accounts,
            LoginSessionMapper sessions,
            PasswordService passwords,
            SessionIdCodec sessionIds,
            AuditService audit,
            PlatformTransactionManager transactionManager) {
        return new AuthenticationService(
                accounts,
                sessions,
                new UsernamePolicy(),
                passwords,
                sessionIds,
                audit,
                mock(UuidV7.class),
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    private static LoginSession loginSession() {
        return new LoginSession(
                "019c8e42-7f70-7000-8000-000000000002",
                account().id(),
                account().username(),
                account().role(),
                account().status(),
                account().passwordChangeRequired(),
                account().sessionVersion(),
                account().createdAt(),
                account().updatedAt(),
                account().rowVersion(),
                localNow().plus(Duration.ofDays(30)),
                4);
    }

    private static UserAccount account() {
        LocalDateTime timestamp = localNow().minusSeconds(60);
        return new UserAccount(
                "019c8e42-7f70-7000-8000-000000000001",
                "Learner01",
                "learner01",
                "stored-hash",
                UserRole.USER,
                UserStatus.ACTIVE,
                false,
                0,
                null,
                null,
                0,
                timestamp,
                timestamp,
                0);
    }

    private static AuthProperties properties() {
        return new AuthProperties("server", 2_592_000);
    }

    private static LocalDateTime localNow() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
