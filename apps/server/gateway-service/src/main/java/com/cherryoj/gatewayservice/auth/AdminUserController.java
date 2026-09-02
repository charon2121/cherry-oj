package com.cherryoj.gatewayservice.auth;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.api.PagePagination;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/users")
@Validated
public class AdminUserController {

	private final AdminGatewayAccess adminAccess;
	private final UserServiceClient userService;

	@Autowired
	AdminUserController(AdminGatewayAccess adminAccess, UserServiceClient userService) {
		this.adminAccess = adminAccess;
		this.userService = userService;
	}

	AdminUserController(GatewayAuthenticationService authentication, UserServiceClient userService) {
		this(new AdminGatewayAccess(authentication), userService);
	}

	@GetMapping
	Mono<ResponseEntity<ApiSuccess<UserListData>>> list(
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return adminAccess.readWithRecovery(
				exchange,
				requestId,
				token -> userService.listUsers(token, page, size, requestId),
				AdminUserController::isUnauthorized,
				error -> GatewayAuthenticationService.mapUpstream(error, false))
				.map(result -> ResponseEntity.ok()
								.cacheControl(CacheControl.noStore())
								.body(ApiSuccess.of(
										new UserListData(result.items().stream()
												.map(UserServiceClient.InternalUser::publicView).toList()),
										requestId,
										new PagePagination(result.page(), result.size(),
												result.totalElements(), result.totalPages()))));
	}

	@PostMapping
	Mono<ResponseEntity<ApiSuccess<CreateUserData>>> create(
			@Valid @RequestBody CreateUserRequest request,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return adminAccess.accessToken(exchange, requestId).flatMap(token ->
				userService.createUser(token, request.username(), requestId)
						.onErrorMap(error -> GatewayAuthenticationService.mapUpstream(error, false))
						.map(created -> ResponseEntity
								.created(URI.create("/api/admin/users/" + created.user().id()))
								.cacheControl(CacheControl.noStore())
								.body(ApiSuccess.of(new CreateUserData(
										created.user().publicView(), created.temporaryPassword()), requestId))));
	}

	@PatchMapping("/{userId}/status")
	Mono<ResponseEntity<ApiSuccess<UserAccountData>>> updateStatus(
			@PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String userId,
			@Valid @RequestBody UpdateUserStatusRequest request,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return adminAccess.accessToken(exchange, requestId).flatMap(token ->
				userService.updateStatus(
						token, userId, request.status(), request.rowVersion(), requestId)
						.onErrorMap(error -> GatewayAuthenticationService.mapUpstream(error, false))
						.map(user -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
								.body(ApiSuccess.of(user.publicView(), requestId))));
	}

	@PostMapping("/{userId}/password-reset")
	Mono<ResponseEntity<ApiSuccess<ResetUserPasswordData>>> resetPassword(
			@PathVariable @Pattern(regexp = "^[0-9a-fA-F-]{36}$") String userId,
			@Valid @RequestBody ResetUserPasswordRequest request,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return adminAccess.accessToken(exchange, requestId).flatMap(token ->
				userService.resetPassword(token, userId, request.rowVersion(), requestId)
						.onErrorMap(error -> GatewayAuthenticationService.mapUpstream(error, false))
						.map(created -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
								.body(ApiSuccess.of(
										new ResetUserPasswordData(created.temporaryPassword()), requestId))));
	}

	private static boolean isUnauthorized(Throwable error) {
		return error instanceof UserServiceClientException upstream
				&& upstream.status().value() == HttpStatus.UNAUTHORIZED.value();
	}

	record UserListData(List<UserAccountData> items) {
	}

	record CreateUserData(UserAccountData user, String temporaryPassword) {
	}

	record ResetUserPasswordData(String temporaryPassword) {
	}

	record CreateUserRequest(
			@NotBlank @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9._-]{2,63}$") String username) {
	}

	record UpdateUserStatusRequest(
			@NotBlank @Pattern(regexp = "^(ACTIVE|DISABLED)$") String status,
			@Min(0) long rowVersion) {
	}

	record ResetUserPasswordRequest(@Min(0) long rowVersion) {
	}
}
