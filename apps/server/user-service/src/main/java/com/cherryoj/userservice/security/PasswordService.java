package com.cherryoj.userservice.security;

import com.cherryoj.userservice.domain.IdentityValidationException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public final class PasswordService {

    private static final String PREFIX = "{argon2}";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%*-_";
    private final Argon2PasswordEncoder encoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    private final SecureRandom random;
    private final String dummyHash;

    public PasswordService(SecureRandom random) {
        this.random = random;
        this.dummyHash = encode("Dummy-Password-9e7d!NeverUsed");
    }

    public String encode(String rawPassword) {
        validateLength(rawPassword);
        return PREFIX + encoder.encode(rawPassword);
    }

    public boolean matches(String rawPassword, String encodedPassword) {
        if (rawPassword == null || rawPassword.length() > 128 || encodedPassword == null
                || !encodedPassword.startsWith(PREFIX)) {
            return false;
        }
        return encoder.matches(rawPassword, encodedPassword.substring(PREFIX.length()));
    }

    public void burnUnknownPassword(String rawPassword) {
        matches(rawPassword == null ? "" : rawPassword, dummyHash);
    }

    public void validateNewPassword(String rawPassword, String usernameNormalized) {
        validateLength(rawPassword);
        if (rawPassword.toLowerCase(Locale.ROOT).contains(usernameNormalized)) {
            throw new IdentityValidationException("PASSWORD_CONTAINS_USERNAME", "新密码不能包含用户名");
        }
        int classes = 0;
        classes += rawPassword.chars().anyMatch(Character::isUpperCase) ? 1 : 0;
        classes += rawPassword.chars().anyMatch(Character::isLowerCase) ? 1 : 0;
        classes += rawPassword.chars().anyMatch(Character::isDigit) ? 1 : 0;
        classes += rawPassword.chars().anyMatch(character -> !Character.isLetterOrDigit(character)) ? 1 : 0;
        if (classes < 3) {
            throw new IdentityValidationException("PASSWORD_TOO_WEAK", "新密码至少包含三类字符");
        }
    }

    public String temporaryPassword() {
        List<Character> characters = new ArrayList<>(24);
        characters.add('A');
        characters.add('a');
        characters.add('7');
        characters.add('!');
        while (characters.size() < 24) {
            characters.add(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        Collections.shuffle(characters, random);
        StringBuilder result = new StringBuilder(characters.size());
        characters.forEach(result::append);
        return result.toString();
    }

    private static void validateLength(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < 12 || rawPassword.length() > 128) {
            throw new IdentityValidationException("PASSWORD_LENGTH", "密码长度必须在 12 到 128 个字符之间");
        }
    }
}
