package com.cherryoj.userservice.application;

import com.cherryoj.userservice.domain.UuidV7;
import com.cherryoj.userservice.persistence.AuditEventMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditEventMapper mapper;
    private final UuidV7 uuidV7;
    private final Clock clock;

    public AuditService(AuditEventMapper mapper, UuidV7 uuidV7, Clock clock) {
        this.mapper = mapper;
        this.uuidV7 = uuidV7;
        this.clock = clock;
    }

    public void record(String actorUserId, String targetUserId, String action) {
        record(actorUserId, targetUserId, action, null);
    }

    public void record(String actorUserId, String targetUserId, String action, String unknownSubject) {
        mapper.insert(
                uuidV7.next().toString(),
                actorUserId,
                targetUserId,
                action,
                unknownSubject == null ? null : digest(unknownSubject),
                null,
                "{}",
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
