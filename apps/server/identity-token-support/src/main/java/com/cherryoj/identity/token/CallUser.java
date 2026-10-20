package com.cherryoj.identity.token;

import java.util.Set;
import java.util.UUID;

/** 调用令牌里的 user：这次调用代表的用户。只有 id 与 role 两项。 */
public record CallUser(String id, String role) {
    public static final String USER = "USER";
    public static final String ADMIN = "ADMIN";
    private static final Set<String> ROLES = Set.of(USER, ADMIN);

    public CallUser {
        if (!isUuid(id) || !ROLES.contains(role)) {
            throw new IllegalArgumentException("user must have a UUID id and role USER or ADMIN");
        }
    }

    public boolean admin() {
        return ADMIN.equals(role);
    }

    private static boolean isUuid(String value) {
        if (value == null) return false;
        try {
            return UUID.fromString(value).toString().equals(value.toLowerCase());
        } catch (IllegalArgumentException error) {
            return false;
        }
    }
}
