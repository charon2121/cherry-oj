package com.cherryoj.userservice.application;

public record CreatedUser(UserView user, String temporaryPassword) {
}
