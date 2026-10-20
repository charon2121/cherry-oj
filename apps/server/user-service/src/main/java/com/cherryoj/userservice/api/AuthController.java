package com.cherryoj.userservice.api;

import com.cherryoj.identity.resource.CurrentCall;
import com.cherryoj.userservice.application.AuthenticationResult;
import com.cherryoj.userservice.application.AuthenticationService;
import com.cherryoj.userservice.application.SessionValidation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class AuthController {

    private final AuthenticationService authentication;

    public AuthController(AuthenticationService authentication) {
        this.authentication = authentication;
    }

    @PostMapping("/auth/authenticate")
    AuthenticationResult authenticate(@Valid @RequestBody LoginRequest request) {
        return authentication.authenticate(request.username(), request.password());
    }

    @PostMapping("/auth/validate")
    SessionValidation validate(@Valid @RequestBody SessionRequest request) {
        return authentication.validate(request.sessionId());
    }

    @PostMapping("/auth/revoke")
    ResponseEntity<Void> revoke(@Valid @RequestBody SessionRequest request) {
        authentication.revoke(request.sessionId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/me/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        authentication.changePassword(CurrentCall.requireUser().id(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {}

    public record SessionRequest(
            @NotNull @Pattern(regexp = "^[\\x20-\\x7E]{40,128}$")
            String sessionId) {}

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {}
}
