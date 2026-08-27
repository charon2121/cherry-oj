package com.cherryoj.userservice.domain;

public final class AuthenticationFailedException extends RuntimeException {

    public AuthenticationFailedException() {
        super("用户名或密码错误，或账号暂不可用");
    }
}
