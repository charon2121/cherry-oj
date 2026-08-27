package com.cherryoj.userservice;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class UserServiceApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(UserServiceApplication.class).isNotNull();
    }
}
