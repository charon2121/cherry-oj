package com.cherryoj.userservice.api;

import com.cherryoj.userservice.application.CreatedUser;
import com.cherryoj.userservice.application.UserAdministrationService;
import com.cherryoj.userservice.application.UserPage;
import com.cherryoj.userservice.application.UserView;
import com.cherryoj.userservice.domain.UserStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/users")
@PreAuthorize("hasRole('ADMIN')")
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
@Validated
public class AdminUserController {

    private final UserAdministrationService users;

    public AdminUserController(UserAdministrationService users) {
        this.users = users;
    }

    @GetMapping
    UserPage list(
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return users.listUsers(page, size);
    }

    @PostMapping
    CreatedUser create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CreateUserRequest request) {
        return users.createUser(jwt.getSubject(), request.username());
    }

    @PatchMapping("/{userId}/status")
    UserView updateStatus(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String userId,
            @Valid @RequestBody UpdateStatusRequest request) {
        return users.updateStatus(jwt.getSubject(), userId, request.status(), request.rowVersion());
    }

    @PostMapping("/{userId}/password-reset")
    CreatedUser resetPassword(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String userId,
            @Valid @RequestBody ResetPasswordRequest request) {
        return users.resetPassword(jwt.getSubject(), userId, request.rowVersion());
    }

    public record CreateUserRequest(
            @NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$") String username) {
    }

    public record UpdateStatusRequest(@NotNull UserStatus status, @Min(0) long rowVersion) {
    }

    public record ResetPasswordRequest(@Min(0) long rowVersion) {
    }
}
