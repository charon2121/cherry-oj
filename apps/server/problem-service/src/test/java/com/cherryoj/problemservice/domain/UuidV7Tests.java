package com.cherryoj.problemservice.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class UuidV7Tests {

    @Test
    void generatesRfc4122VariantVersionSevenIds() {
        UuidV7 ids = new UuidV7(Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC), new SecureRandom());

        assertThat(ids.next().version()).isEqualTo(7);
        assertThat(ids.next().variant()).isEqualTo(2);
    }
}
