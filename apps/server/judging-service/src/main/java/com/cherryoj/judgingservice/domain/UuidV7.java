package com.cherryoj.judgingservice.domain;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;

public final class UuidV7 {
    private final Clock clock;
    private final SecureRandom random;

    public UuidV7(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    public UUID next() {
        long timestamp = clock.millis() & 0xFFFFFFFFFFFFL;
        long most = (timestamp << 16) | 0x7000L | random.nextInt(1 << 12);
        long least = (random.nextLong() & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(most, least);
    }
}
