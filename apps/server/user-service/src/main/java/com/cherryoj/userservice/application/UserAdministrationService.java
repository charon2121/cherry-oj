package com.cherryoj.userservice.application;

import com.cherryoj.userservice.domain.IdentityConflictException;
import com.cherryoj.userservice.domain.IdentityNotFoundException;
import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserRole;
import com.cherryoj.userservice.domain.UserStatus;
import com.cherryoj.userservice.domain.UsernamePolicy;
import com.cherryoj.userservice.domain.UuidV7;
import com.cherryoj.userservice.persistence.LoginSessionMapper;
import com.cherryoj.userservice.persistence.UserAccountMapper;
import com.cherryoj.userservice.security.PasswordService;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAdministrationService {

    private final UserAccountMapper accounts;
    private final LoginSessionMapper sessions;
    private final UsernamePolicy usernames;
    private final PasswordService passwords;
    private final AuditService audit;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public UserAdministrationService(
            UserAccountMapper accounts,
            LoginSessionMapper sessions,
            UsernamePolicy usernames,
            PasswordService passwords,
            AuditService audit,
            UuidV7 uuidV7,
            Clock clock) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.usernames = usernames;
        this.passwords = passwords;
        this.audit = audit;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    @Transactional
    public CreatedUser createUser(String actorUserId, String username) {
        String temporaryPassword = temporaryPasswordFor(usernames.normalize(username));
        UserAccount account = newAccount(username, temporaryPassword, UserRole.USER, true);
        insert(account);
        audit.record(actorUserId, account.id(), "USER_CREATED");
        return new CreatedUser(UserView.from(account), temporaryPassword);
    }

    @Transactional
    public UserView bootstrapAdmin(String username, String password) {
        if (accounts.countAdmins() != 0) {
            throw new IdentityConflictException("ADMIN_ALREADY_EXISTS", "管理员已经存在");
        }
        String normalized = usernames.normalize(username);
        passwords.validateNewPassword(password, normalized);
        UserAccount account = newAccount(username, password, UserRole.ADMIN, false);
        insert(account);
        audit.record(account.id(), account.id(), "ADMIN_BOOTSTRAPPED");
        return UserView.from(account);
    }

    @Transactional(readOnly = true)
    public UserPage listUsers(int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, Math.min(100, size));
        long total = accounts.countUsers();
        long offset = (long) (safePage - 1) * safeSize;
        List<UserView> items = accounts.listUsers(offset, safeSize).stream().map(UserView::from).toList();
        int totalPages = total == 0 ? 0 : (int) ((total + safeSize - 1) / safeSize);
        return new UserPage(items, safePage, safeSize, total, totalPages);
    }

    @Transactional
    public UserView updateStatus(
            String actorUserId, String userId, UserStatus status, long expectedRowVersion) {
        UserAccount account = requireUserForUpdate(userId);
        if (account.rowVersion() != expectedRowVersion) {
            throw versionConflict();
        }
        if (account.status() == status) {
            return UserView.from(account);
        }
        LocalDateTime now = now();
        if (accounts.updateStatus(userId, status, now, expectedRowVersion) != 1) {
            throw versionConflict();
        }
        if (status == UserStatus.DISABLED) {
            sessions.revokeAll(userId, now, "ACCOUNT_DISABLED");
        }
        audit.record(actorUserId, userId, status == UserStatus.DISABLED ? "USER_DISABLED" : "USER_ENABLED");
        return UserView.from(accounts.findById(userId));
    }

    @Transactional
    public CreatedUser resetPassword(String actorUserId, String userId, long expectedRowVersion) {
        UserAccount account = requireUserForUpdate(userId);
        if (account.rowVersion() != expectedRowVersion) {
            throw versionConflict();
        }
        String temporaryPassword = temporaryPasswordFor(account.usernameNormalized());
        String passwordHash = passwords.encode(temporaryPassword);
        LocalDateTime now = now();
        if (accounts.updatePassword(userId, passwordHash, true, now, expectedRowVersion) != 1) {
            throw versionConflict();
        }
        sessions.revokeAll(userId, now, "PASSWORD_RESET");
        audit.record(actorUserId, userId, "PASSWORD_RESET");
        return new CreatedUser(UserView.from(accounts.findById(userId)), temporaryPassword);
    }

    private UserAccount requireUserForUpdate(String userId) {
        UserAccount account = accounts.findByIdForUpdate(userId);
        if (account == null) {
            throw new IdentityNotFoundException();
        }
        if (account.role() != UserRole.USER) {
            throw new IdentityConflictException("ADMIN_MUTATION_FORBIDDEN", "普通管理接口不能修改管理员");
        }
        return account;
    }

    private UserAccount newAccount(String username, String password, UserRole role, boolean passwordChangeRequired) {
        String display = usernames.validateDisplay(username);
        String normalized = usernames.normalize(display);
        passwords.validateNewPassword(password, normalized);
        LocalDateTime now = now();
        return new UserAccount(
                uuidV7.next().toString(),
                display,
                normalized,
                passwords.encode(password),
                role,
                UserStatus.ACTIVE,
                passwordChangeRequired,
                0,
                null,
                null,
                0,
                now,
                now,
                0);
    }

    private void insert(UserAccount account) {
        try {
            accounts.insert(account);
        } catch (DuplicateKeyException error) {
            throw new IdentityConflictException("USERNAME_ALREADY_EXISTS", "用户名已经存在");
        }
    }

    private static IdentityConflictException versionConflict() {
        return new IdentityConflictException("USER_VERSION_CONFLICT", "账号状态已变化，请刷新后重试");
    }

    private String temporaryPasswordFor(String usernameNormalized) {
        String temporaryPassword;
        do {
            temporaryPassword = passwords.temporaryPassword();
        } while (temporaryPassword.toLowerCase(java.util.Locale.ROOT).contains(usernameNormalized));
        return temporaryPassword;
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
    }
}
