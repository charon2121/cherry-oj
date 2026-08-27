package com.cherryoj.userservice.domain;

public final class IdentityNotFoundException extends RuntimeException {

    public IdentityNotFoundException() {
        super("用户不存在");
    }
}
