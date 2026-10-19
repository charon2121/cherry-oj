package com.cherryoj.problemservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ProblemServiceApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(ProblemServiceApplication.class).isNotNull();
    }
}
