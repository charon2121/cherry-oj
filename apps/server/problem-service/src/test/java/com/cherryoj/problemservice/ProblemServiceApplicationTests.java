package com.cherryoj.problemservice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class ProblemServiceApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(ProblemServiceApplication.class).isNotNull();
    }

}
