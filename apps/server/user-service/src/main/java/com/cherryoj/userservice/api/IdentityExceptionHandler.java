package com.cherryoj.userservice.api;

import com.cherryoj.userservice.domain.AuthenticationFailedException;
import com.cherryoj.userservice.domain.IdentityConflictException;
import com.cherryoj.userservice.domain.IdentityNotFoundException;
import com.cherryoj.userservice.domain.IdentityValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class IdentityExceptionHandler {

    @ExceptionHandler(AuthenticationFailedException.class)
    ResponseEntity<IdentityApiError> authenticationFailed(AuthenticationFailedException error) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(new IdentityApiError("AUTHENTICATION_FAILED", error.getMessage()));
    }

    @ExceptionHandler(IdentityValidationException.class)
    ResponseEntity<IdentityApiError> validation(IdentityValidationException error) {
        return ResponseEntity.unprocessableEntity().body(new IdentityApiError(error.code(), error.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<IdentityApiError> beanValidation() {
        return ResponseEntity.unprocessableEntity()
                .body(new IdentityApiError("VALIDATION_FAILED", "请求字段不符合要求"));
    }

    @ExceptionHandler(IdentityConflictException.class)
    ResponseEntity<IdentityApiError> conflict(IdentityConflictException error) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new IdentityApiError(error.code(), error.getMessage()));
    }

    @ExceptionHandler(IdentityNotFoundException.class)
    ResponseEntity<IdentityApiError> notFound(IdentityNotFoundException error) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new IdentityApiError("USER_NOT_FOUND", error.getMessage()));
    }
}
