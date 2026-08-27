package com.cherryoj.userservice.domain;

import java.time.LocalDateTime;

public record LoginGrant(
        String sessionId,
        String userId,
        String username,
        UserRole role,
        UserStatus status,
        boolean passwordChangeRequired,
        long sessionVersion,
        LocalDateTime userCreatedAt,
        LocalDateTime userUpdatedAt,
        long userRowVersion,
        LocalDateTime idleExpiresAt,
        LocalDateTime absoluteExpiresAt,
        long rowVersion) {
}
