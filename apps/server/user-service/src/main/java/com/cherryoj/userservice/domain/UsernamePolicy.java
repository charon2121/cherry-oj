package com.cherryoj.userservice.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

@Component
public final class UsernamePolicy {

    private static final Pattern ALLOWED = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$");

    public String validateDisplay(String username) {
        String normalizedUnicode = Normalizer.normalize(username, Normalizer.Form.NFKC);
        if (!ALLOWED.matcher(normalizedUnicode).matches()) {
            throw new IdentityValidationException("USERNAME_INVALID", "用户名格式不符合要求");
        }
        return normalizedUnicode;
    }

    public String normalize(String username) {
        return validateDisplay(username).toLowerCase(Locale.ROOT);
    }
}
