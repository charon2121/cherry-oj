package com.cherryoj.userservice.domain;

import java.nio.ByteBuffer;
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
        long timestampMillis = clock.millis();
        byte[] bytes = new byte[16];
        for (int index = 5; index >= 0; index--) {
            bytes[index] = (byte) timestampMillis;
            timestampMillis >>>= 8;
        }
        byte[] entropy = new byte[10];
        random.nextBytes(entropy);
        bytes[6] = (byte) (0x70 | (entropy[0] & 0x0f));
        bytes[7] = entropy[1];
        bytes[8] = (byte) (0x80 | (entropy[2] & 0x3f));
        System.arraycopy(entropy, 3, bytes, 9, 7);
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }
}
