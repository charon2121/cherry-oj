package com.cherryoj.userservice.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.userservice.domain.IdentityValidationException;
import java.security.SecureRandom;
import org.junit.jupiter.api.Test;

class PasswordServiceTests {

    private final PasswordService passwords = new PasswordService(new SecureRandom());

    @Test
    void hashesWithAlgorithmPrefixAndMatches() {
        String encoded = passwords.encode("Correct-Horse-7!Battery");

        assertThat(encoded).startsWith("{argon2}");
        assertThat(passwords.matches("Correct-Horse-7!Battery", encoded)).isTrue();
        assertThat(passwords.matches("wrong-password", encoded)).isFalse();
    }

    @Test
    void generatedPasswordPassesPolicy() {
        String temporary = passwords.temporaryPassword();

        assertThat(temporary).hasSize(24);
        passwords.validateNewPassword(temporary, "learner01");
    }

    @Test
    void rejectsPasswordContainingUsername() {
        assertThatThrownBy(() -> passwords.validateNewPassword("Learner01-Secret!", "learner01"))
                .isInstanceOf(IdentityValidationException.class)
                .extracting("code")
                .isEqualTo("PASSWORD_CONTAINS_USERNAME");
    }
}
