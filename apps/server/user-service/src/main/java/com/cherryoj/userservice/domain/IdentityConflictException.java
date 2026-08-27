package com.cherryoj.userservice.domain;

public final class IdentityConflictException extends RuntimeException {

    private final String code;

    public IdentityConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
