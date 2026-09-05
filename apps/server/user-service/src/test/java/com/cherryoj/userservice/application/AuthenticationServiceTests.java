package com.cherryoj.userservice.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.domain.AuthenticationFailedException;
import com.cherryoj.userservice.domain.LoginGrant;
import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserRole;
import com.cherryoj.userservice.domain.UserStatus;
import com.cherryoj.userservice.domain.UsernamePolicy;
import com.cherryoj.userservice.domain.UuidV7;
import com.cherryoj.userservice.persistence.LoginSessionMapper;
import com.cherryoj.userservice.persistence.UserAccountMapper;
import com.cherryoj.userservice.security.LoginGrantCodec;
import com.cherryoj.userservice.security.PasswordService;
import com.cherryoj.userservice.security.TokenService;
import com.cherryoj.userservice.security.TokenValue;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class AuthenticationServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

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
                accounts, mock(LoginSessionMapper.class), passwords, mock(LoginGrantCodec.class),
                mock(TokenService.class), audit, transactionManager);

        assertThatThrownBy(() -> service.authenticate("Learner01", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class);

        LocalDateTime now = localNow();
        verify(accounts).recordLoginFailure(account().id(), now, now.plusMinutes(15));
        verify(transactionManager).commit(transaction);
    }

    @Test
    void validateIsReadOnlyAndReturnsUnchangedAbsoluteDeadline() {
        LoginSessionMapper sessions = mock(LoginSessionMapper.class);
        LoginGrantCodec grants = mock(LoginGrantCodec.class);
        byte[] digest = {1, 2, 3};
        LoginGrant grant = loginGrant();
        when(grants.digest("grant-value")).thenReturn(digest);
        when(sessions.findActiveByGrantHash(digest, localNow())).thenReturn(grant);

        SessionTouchResult result = service(sessions, grants, mock(TokenService.class)).validate("grant-value");

        assertThat(result.sessionAbsoluteExpiresAt()).isEqualTo(grant.absoluteExpiresAt());
        assertThat(result.sessionLifetimePolicy()).isEqualTo("fixed-absolute");
        verify(sessions, never()).markUsed(any(), any(), any(Long.class));
    }

    @Test
    void exchangeRecordsUsageButNeverExtendsAbsoluteDeadline() {
        LoginSessionMapper sessions = mock(LoginSessionMapper.class);
        LoginGrantCodec grants = mock(LoginGrantCodec.class);
        TokenService tokens = mock(TokenService.class);
        byte[] digest = {1, 2, 3};
        LoginGrant grant = loginGrant();
        when(grants.digest("grant-value")).thenReturn(digest);
        when(sessions.findActiveByGrantHashForUpdate(digest, localNow())).thenReturn(grant);
        when(sessions.markUsed(grant.sessionId(), localNow(), grant.rowVersion())).thenReturn(1);
        when(tokens.issue(grant)).thenReturn(new TokenValue("token", NOW.plus(Duration.ofHours(2))));

        TokenExchangeResult result = service(sessions, grants, tokens).exchange("grant-value");

        assertThat(result.sessionAbsoluteExpiresAt()).isEqualTo(grant.absoluteExpiresAt());
        verify(sessions).markUsed(grant.sessionId(), localNow(), grant.rowVersion());
    }

    private static AuthenticationService service(
            LoginSessionMapper sessions, LoginGrantCodec grants, TokenService tokens) {
        return service(mock(UserAccountMapper.class), sessions, mock(PasswordService.class), grants,
                tokens, mock(AuditService.class), mock(PlatformTransactionManager.class));
    }

    private static AuthenticationService service(
            UserAccountMapper accounts, LoginSessionMapper sessions, PasswordService passwords,
            LoginGrantCodec grants, TokenService tokens, AuditService audit,
            PlatformTransactionManager transactionManager) {
        return new AuthenticationService(accounts, sessions, new UsernamePolicy(), passwords, grants,
                tokens, audit, mock(UuidV7.class), properties(), Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);
    }

    private static LoginGrant loginGrant() {
        return new LoginGrant(
                "019c8e42-7f70-7000-8000-000000000002", account().id(), account().username(),
                account().role(), account().status(), account().passwordChangeRequired(),
                account().sessionVersion(), account().createdAt(), account().updatedAt(),
                account().rowVersion(), localNow().plus(Duration.ofDays(30)), 4);
    }

    private static UserAccount account() {
        LocalDateTime timestamp = localNow().minusSeconds(60);
        return new UserAccount(
                "019c8e42-7f70-7000-8000-000000000001", "Learner01", "learner01", "stored-hash",
                UserRole.USER, UserStatus.ACTIVE, false, 0, null, null, 0, timestamp, timestamp, 0);
    }

    private static AuthProperties properties() {
        return new AuthProperties(
                "server", "cherry-oj-user-service", "cherry-oj-internal", "test-key", "unused", "unused",
                Map.of(), Duration.ofHours(2), Duration.ofSeconds(30), "fixed-absolute", 2_592_000);
    }

    private static LocalDateTime localNow() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
