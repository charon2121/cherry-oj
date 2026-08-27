package com.cherryoj.userservice.api;

import com.cherryoj.userservice.application.AuthenticationResult;
import com.cherryoj.userservice.application.AuthenticationService;
import com.cherryoj.userservice.application.SessionTouchResult;
import com.cherryoj.userservice.application.TokenExchangeResult;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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

    @PostMapping("/auth/token")
    TokenExchangeResult exchange(@Valid @RequestBody LoginGrantRequest request) {
        return authentication.exchange(request.loginGrant());
    }

    @PostMapping("/auth/touch")
    SessionTouchResult touch(@Valid @RequestBody LoginGrantRequest request) {
        return authentication.touch(request.loginGrant());
    }

    @PostMapping("/auth/revoke")
    ResponseEntity<Void> revoke(@Valid @RequestBody LoginGrantRequest request) {
        authentication.revoke(request.loginGrant());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/users/me/password")
    ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePasswordRequest request) {
        authentication.changePassword(jwt.getSubject(), request.currentPassword(), request.newPassword());
        return ResponseEntity.noContent().build();
    }

    public record LoginRequest(
            @NotBlank @Size(max = 64) String username,
            @NotBlank @Size(max = 128) String password) {
    }

    public record LoginGrantRequest(@NotBlank @Size(min = 40, max = 128) String loginGrant) {
    }

    public record ChangePasswordRequest(
            @NotBlank @Size(max = 128) String currentPassword,
            @NotBlank @Size(min = 12, max = 128) String newPassword) {
    }
}
