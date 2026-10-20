package com.cherryoj.userservice.application;

import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.domain.AuthenticationFailedException;
import com.cherryoj.userservice.domain.IdentityConflictException;
import com.cherryoj.userservice.domain.IdentityValidationException;
import com.cherryoj.userservice.domain.LoginSession;
import com.cherryoj.userservice.domain.SessionInvalidException;
import com.cherryoj.userservice.domain.UserAccount;
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
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class AuthenticationService {

    private static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    private final UserAccountMapper accounts;
    private final LoginSessionMapper sessions;
    private final UsernamePolicy usernames;
    private final PasswordService passwords;
    private final SessionIdCodec sessionIds;
    private final AuditService audit;
    private final UuidV7 uuidV7;
    private final AuthProperties properties;
    private final Clock clock;
    private final TransactionTemplate transactions;

    public AuthenticationService(
            UserAccountMapper accounts,
            LoginSessionMapper sessions,
            UsernamePolicy usernames,
            PasswordService passwords,
            SessionIdCodec sessionIds,
            AuditService audit,
            UuidV7 uuidV7,
            AuthProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.usernames = usernames;
        this.passwords = passwords;
        this.sessionIds = sessionIds;
        this.audit = audit;
        this.uuidV7 = uuidV7;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AuthenticationResult authenticate(String username, String password) {
        AuthenticationResult result = transactions.execute(ignored -> authenticateInTransaction(username, password));
        if (result == null) {
            throw new AuthenticationFailedException();
        }
        return result;
    }

    private AuthenticationResult authenticateInTransaction(String username, String password) {
        String normalized;
        try {
            normalized = usernames.normalize(username);
        } catch (IdentityValidationException error) {
            passwords.burnUnknownPassword(password);
            audit.record(null, null, "LOGIN_FAILED", username);
            return null;
        }
        UserAccount account = accounts.findByNormalizedUsernameForUpdate(normalized);
        if (account == null) {
            passwords.burnUnknownPassword(password);
            audit.record(null, null, "LOGIN_FAILED", normalized);
            return null;
        }

        LocalDateTime now = now();
        boolean passwordMatches = passwords.matches(password, account.passwordHash());
        if (!passwordMatches || account.status() != UserStatus.ACTIVE || account.isLockedAt(now)) {
            if (!passwordMatches && account.status() == UserStatus.ACTIVE && !account.isLockedAt(now)) {
                accounts.recordLoginFailure(account.id(), now, now.plus(LOCK_DURATION));
            }
            audit.record(null, account.id(), "LOGIN_FAILED");
            return null;
        }

        accounts.recordLoginSuccess(account.id(), now);
        String sessionId = sessionIds.generate();
        // MySQL DATETIME(6) must preserve the exact deadline returned to the gateway.
        // Truncate before both persistence and the response so rounding cannot extend the session.
        LocalDateTime absoluteExpiresAt =
                now.plus(properties.sessionAbsoluteTimeout()).truncatedTo(ChronoUnit.MICROS);
        sessions.insert(
                uuidV7.next().toString(),
                account.id(),
                sessionIds.digest(sessionId),
                account.sessionVersion(),
                now,
                absoluteExpiresAt);
        audit.record(account.id(), account.id(), "LOGIN_SUCCEEDED");
        return new AuthenticationResult(sessionId, UserView.from(account), instant(absoluteExpiresAt));
    }

    @Transactional
    public SessionValidation validate(String sessionId) {
        LocalDateTime now = now();
        LoginSession session = sessions.findActiveByHash(sessionIds.digest(sessionId), now);
        if (session == null) {
            throw new SessionInvalidException();
        }
        sessions.touch(session.sessionId(), now);
        return new SessionValidation(UserView.from(session), instant(session.absoluteExpiresAt()));
    }

    @Transactional
    public void revoke(String sessionId) {
        LocalDateTime now = now();
        if (sessions.findActiveByHash(sessionIds.digest(sessionId), now) == null) {
            throw new SessionInvalidException();
        }
        sessions.revokeCurrent(sessionIds.digest(sessionId), now, "LOGOUT");
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findByIdForUpdate(userId);
        if (account == null
                || account.status() != UserStatus.ACTIVE
                || !passwords.matches(currentPassword, account.passwordHash())) {
            throw new AuthenticationFailedException();
        }
        passwords.validateNewPassword(newPassword, account.usernameNormalized());
        String passwordHash = passwords.encode(newPassword);
        LocalDateTime now = now();
        if (accounts.updatePassword(account.id(), passwordHash, false, now, account.rowVersion()) != 1) {
            throw new IdentityConflictException("USER_VERSION_CONFLICT", "账号状态已变化，请重试");
        }
        sessions.revokeAll(account.id(), now, "PASSWORD_CHANGED");
        audit.record(account.id(), account.id(), "PASSWORD_CHANGED");
    }

    private static Instant instant(LocalDateTime utc) {
        return utc.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
