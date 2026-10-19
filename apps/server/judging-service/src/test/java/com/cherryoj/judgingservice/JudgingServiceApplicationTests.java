package com.cherryoj.judgingservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class JudgingServiceApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(JudgingServiceApplication.class).isNotNull();
    }
}
