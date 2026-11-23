package com.cherryoj.userservice.application;

public record IssuedTemporaryPassword(UserView user, String temporaryPassword) {}
