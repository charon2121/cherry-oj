package com.cherryoj.userservice;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UserServiceApplicationTests {

    @Test
    void applicationEntryPointExists() {
        assertThat(UserServiceApplication.class).isNotNull();
    }
}
