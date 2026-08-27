package com.cherryoj.userservice.application;

import com.cherryoj.userservice.domain.LoginGrant;
import com.cherryoj.userservice.domain.UserAccount;
import com.cherryoj.userservice.domain.UserRole;
import com.cherryoj.userservice.domain.UserStatus;
import java.time.LocalDateTime;

public record UserView(
        String id,
        String username,
        UserRole role,
        UserStatus status,
        boolean passwordChangeRequired,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long rowVersion) {

    public static UserView from(UserAccount account) {
        return new UserView(
                account.id(),
                account.username(),
                account.role(),
                account.status(),
                account.passwordChangeRequired(),
                account.createdAt(),
                account.updatedAt(),
                account.rowVersion());
    }

    public static UserView from(LoginGrant grant) {
        return new UserView(
                grant.userId(),
                grant.username(),
                grant.role(),
                grant.status(),
                grant.passwordChangeRequired(),
                grant.userCreatedAt(),
                grant.userUpdatedAt(),
                grant.userRowVersion());
    }
}
