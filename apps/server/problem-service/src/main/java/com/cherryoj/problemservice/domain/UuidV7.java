package com.cherryoj.problemservice.domain;

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
        long randomA = random.nextInt(1 << 12);
        long mostSignificant = (timestamp << 16) | 0x7000L | randomA;
        long leastSignificant = random.nextLong();
        leastSignificant = (leastSignificant & 0x3FFFFFFFFFFFFFFFL) | 0x8000000000000000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
