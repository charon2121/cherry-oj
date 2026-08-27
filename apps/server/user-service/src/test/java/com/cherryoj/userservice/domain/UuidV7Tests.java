package com.cherryoj.userservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UuidV7Tests {

    @Test
    void encodesTimestampVersionAndVariant() throws Exception {
        Instant instant = Instant.parse("2026-08-26T01:02:03.456Z");
        SecureRandom random = SecureRandom.getInstance("SHA1PRNG");
        random.setSeed(new byte[] {1, 2, 3, 4});

        var value = new UuidV7(Clock.fixed(instant, ZoneOffset.UTC), random).next();

        assertThat(value.version()).isEqualTo(7);
        assertThat(value.variant()).isEqualTo(2);
        assertThat(value.getMostSignificantBits() >>> 16).isEqualTo(instant.toEpochMilli());
    }
}
