package com.cherryoj.userservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class LoginGrantCodecTests {

    @Test
    void grantsAreOpaqueAndOnlyDigestIsStable() {
        LoginGrantCodec codec = new LoginGrantCodec(new SecureRandom());

        String first = codec.generate();
        String second = codec.generate();

        assertThat(first).hasSize(43).isNotEqualTo(second);
        assertThat(codec.digest(first)).hasSize(32).isEqualTo(codec.digest(first));
        assertThat(codec.digest(first)).isNotEqualTo(codec.digest(second));
    }
}
