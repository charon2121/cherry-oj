package com.cherryoj.gatewayservice.api;

public record FieldViolation(String path, String code, String message) {
}
