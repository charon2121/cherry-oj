package com.cherryoj.judgingservice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class JudgingServiceApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(JudgingServiceApplication.class).isNotNull();
    }

}
