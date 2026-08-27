package com.cherryoj.userservice.application;

import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.domain.AuthenticationFailedException;
import com.cherryoj.userservice.domain.IdentityConflictException;
import com.cherryoj.userservice.domain.IdentityValidationException;
import com.cherryoj.userservice.domain.LoginGrant;
import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserStatus;
import com.cherryoj.userservice.domain.UsernamePolicy;
import com.cherryoj.userservice.domain.UuidV7;
import com.cherryoj.userservice.persistence.LoginSessionMapper;
import com.cherryoj.userservice.persistence.UserAccountMapper;
import com.cherryoj.userservice.security.LoginGrantCodec;
import com.cherryoj.userservice.security.PasswordService;
import com.cherryoj.userservice.security.TokenService;
import com.cherryoj.userservice.security.TokenValue;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
    private final LoginGrantCodec grants;
    private final TokenService tokens;
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
            LoginGrantCodec grants,
            TokenService tokens,
            AuditService audit,
            UuidV7 uuidV7,
            AuthProperties properties,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.usernames = usernames;
        this.passwords = passwords;
        this.grants = grants;
        this.tokens = tokens;
        this.audit = audit;
        this.uuidV7 = uuidV7;
        this.properties = properties;
        this.clock = clock;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public AuthenticationResult authenticate(String username, String password) {
        AuthenticationResult result = transactions.execute(
                ignored -> authenticateInTransaction(username, password));
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
        String grant = grants.generate();
        LocalDateTime idleExpiresAt = now.plus(properties.sessionIdleTimeout());
        LocalDateTime absoluteExpiresAt = now.plus(properties.sessionAbsoluteTimeout());
        sessions.insert(
                uuidV7.next().toString(),
                account.id(),
                grants.digest(grant),
                account.sessionVersion(),
                now,
                idleExpiresAt,
                absoluteExpiresAt);
        TokenValue token = tokens.issue(account);
        audit.record(account.id(), account.id(), "LOGIN_SUCCEEDED");
        return new AuthenticationResult(
                UserView.from(account), grant, token.value(), token.expiresAt(), idleExpiresAt, absoluteExpiresAt);
    }

    @Transactional
    public TokenExchangeResult exchange(String loginGrant) {
        LocalDateTime now = now();
        LoginGrant grant = sessions.findActiveByGrantHashForUpdate(grants.digest(loginGrant), now);
        if (grant == null) {
            throw new AuthenticationFailedException();
        }
        LocalDateTime idleExpiresAt = now.plus(properties.sessionIdleTimeout());
        if (sessions.touch(grant.sessionId(), now, idleExpiresAt, grant.rowVersion()) != 1) {
            throw new IdentityConflictException("SESSION_CONFLICT", "登录状态发生并发变化，请重试");
        }
        TokenValue token = tokens.issue(grant);
        LocalDateTime effectiveIdleExpiry = idleExpiresAt.isBefore(grant.absoluteExpiresAt())
                ? idleExpiresAt
                : grant.absoluteExpiresAt();
        return new TokenExchangeResult(
                UserView.from(grant),
                token.value(),
                token.expiresAt(),
                effectiveIdleExpiry,
                grant.absoluteExpiresAt());
    }

    @Transactional
    public void revoke(String loginGrant) {
        sessions.revokeCurrent(grants.digest(loginGrant), now(), "LOGOUT");
    }

    @Transactional
    public void changePassword(String userId, String currentPassword, String newPassword) {
        UserAccount account = accounts.findByIdForUpdate(userId);
        if (account == null || account.status() != UserStatus.ACTIVE
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

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
