package com.cherryoj.userservice.domain;

import java.time.LocalDateTime;

public record UserAccount(
        String id,
        String username,
        String usernameNormalized,
        String passwordHash,
        UserRole role,
        UserStatus status,
        boolean passwordChangeRequired,
        int failedLoginCount,
        LocalDateTime lastFailedLoginAt,
        LocalDateTime lockedUntil,
        long sessionVersion,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long rowVersion) {

    public boolean isLockedAt(LocalDateTime now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
