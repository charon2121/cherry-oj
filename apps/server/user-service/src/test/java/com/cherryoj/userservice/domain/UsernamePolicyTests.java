package com.cherryoj.userservice.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class UsernamePolicyTests {

    private final UsernamePolicy policy = new UsernamePolicy();

    @Test
    void normalizesCaseWithoutChangingDisplayValue() {
        assertThat(policy.validateDisplay("Learner.01")).isEqualTo("Learner.01");
        assertThat(policy.normalize("Learner.01")).isEqualTo("learner.01");
    }

    @Test
    void rejectsWhitespaceAndNonAsciiNames() {
        assertThatThrownBy(() -> policy.normalize(" learner"))
                .isInstanceOf(IdentityValidationException.class);
        assertThatThrownBy(() -> policy.normalize("学习者"))
                .isInstanceOf(IdentityValidationException.class);
    }
}
