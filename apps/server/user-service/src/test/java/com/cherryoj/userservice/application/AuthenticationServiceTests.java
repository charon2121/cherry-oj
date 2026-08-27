package com.cherryoj.userservice.application;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

class AuthenticationServiceTests {

    private static final Instant NOW = Instant.parse("2026-08-26T12:00:00Z");

    @Test
    void failedPasswordCommitsBackoffBeforeReturningTheGenericFailure() {
        UserAccountMapper accounts = mock(UserAccountMapper.class);
        PasswordService passwords = mock(PasswordService.class);
        AuditService audit = mock(AuditService.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transaction = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(transaction);
        when(accounts.findByNormalizedUsernameForUpdate("learner01")).thenReturn(account());
        when(passwords.matches("wrong-password", "stored-hash")).thenReturn(false);
        AuthenticationService service = new AuthenticationService(
                accounts,
                mock(LoginSessionMapper.class),
                new UsernamePolicy(),
                passwords,
                mock(LoginGrantCodec.class),
                mock(TokenService.class),
                audit,
                mock(UuidV7.class),
                properties(),
                Clock.fixed(NOW, ZoneOffset.UTC),
                transactionManager);

        assertThatThrownBy(() -> service.authenticate("Learner01", "wrong-password"))
                .isInstanceOf(AuthenticationFailedException.class);

        LocalDateTime now = LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
        verify(accounts).recordLoginFailure(account().id(), now, now.plusMinutes(15));
        verify(audit).record(null, account().id(), "LOGIN_FAILED");
        verify(transactionManager).commit(transaction);
    }

    private static UserAccount account() {
        LocalDateTime timestamp = LocalDateTime.ofInstant(NOW.minusSeconds(60), ZoneOffset.UTC);
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
        return new AuthProperties(
                "server",
                "cherry-oj-user-service",
                "cherry-oj-internal",
                "test-key",
                "unused",
                "unused",
                Map.of(),
                Duration.ofSeconds(120),
                Duration.ofSeconds(30),
                Duration.ofMinutes(30),
                Duration.ofHours(12));
    }
}
