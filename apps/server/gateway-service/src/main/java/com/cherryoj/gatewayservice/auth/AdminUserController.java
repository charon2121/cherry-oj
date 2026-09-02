package com.cherryoj.gatewayservice.auth;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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
import org.springframework.web.server.WebSession;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.ApiSuccess;
import com.cherryoj.gatewayservice.api.PagePagination;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/admin/users")
@Validated
public class AdminUserController {

	private final GatewayAuthenticationService authentication;
	private final UserServiceClient userService;

	AdminUserController(GatewayAuthenticationService authentication, UserServiceClient userService) {
		this.authentication = authentication;
		this.userService = userService;
	}

	@GetMapping
	Mono<ResponseEntity<ApiSuccess<UserListData>>> list(
			@RequestParam(defaultValue = "1") @Min(1) int page,
			@RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return exchange.getSession().flatMap(session -> withAdmin(session, requestId).flatMap(state ->
				listUsersWithRecovery(session, state, page, size, requestId)
						.map(result -> ResponseEntity.ok()
								.cacheControl(CacheControl.noStore())
								.body(ApiSuccess.of(
										new UserListData(result.items().stream()
												.map(UserServiceClient.InternalUser::publicView).toList()),
										requestId,
										new PagePagination(result.page(), result.size(),
												result.totalElements(), result.totalPages()))))));
	}

	@PostMapping
	Mono<ResponseEntity<ApiSuccess<CreateUserData>>> create(
			@Valid @RequestBody CreateUserRequest request,
			ServerWebExchange exchange) {
		String requestId = ApiRequestContext.requestId(exchange);
		return withAdmin(exchange, requestId).flatMap(state ->
				userService.createUser(state.accessToken(), request.username(), requestId)
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
		return withAdmin(exchange, requestId).flatMap(state ->
				userService.updateStatus(
						state.accessToken(), userId, request.status(), request.rowVersion(), requestId)
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
		return withAdmin(exchange, requestId).flatMap(state ->
				userService.resetPassword(state.accessToken(), userId, request.rowVersion(), requestId)
						.onErrorMap(error -> GatewayAuthenticationService.mapUpstream(error, false))
						.map(created -> ResponseEntity.ok().cacheControl(CacheControl.noStore())
								.body(ApiSuccess.of(
										new ResetUserPasswordData(created.temporaryPassword()), requestId))));
	}

	private Mono<AuthSessionState> withAdmin(ServerWebExchange exchange, String requestId) {
		return exchange.getSession().flatMap(session -> withAdmin(session, requestId));
	}

	private Mono<AuthSessionState> withAdmin(WebSession session, String requestId) {
		return authentication.current(session, requestId, true)
				.flatMap(state -> {
					if (state.user().passwordChangeRequired()) {
						return Mono.error(new ApiProblemException(
								HttpStatus.FORBIDDEN,
								"PASSWORD_CHANGE_REQUIRED",
								"需要修改密码",
								"完成首次密码修改后才能访问管理功能。"));
					}
					return "ADMIN".equals(state.user().role())
							? Mono.just(state)
							: Mono.error(new ApiProblemException(
									HttpStatus.FORBIDDEN,
									"FORBIDDEN",
									"无权访问",
									"当前身份无权执行此操作。"));
				});
	}

	private Mono<UserServiceClient.UserPage> listUsersWithRecovery(
			WebSession session, AuthSessionState state, int page, int size, String requestId) {
		return userService.listUsers(state.accessToken(), page, size, requestId)
				.onErrorResume(error -> {
					if (!isUnauthorized(error)) {
						return Mono.error(GatewayAuthenticationService.mapUpstream(error, false));
					}
					return authentication.recoverRejectedAccessToken(session, state, requestId)
							.flatMap(refreshed -> userService.listUsers(
										refreshed.accessToken(), page, size, requestId)
									.onErrorMap(AdminUserController::mapRetriedListError));
				});
	}

	private static boolean isUnauthorized(Throwable error) {
		return error instanceof UserServiceClientException upstream
				&& upstream.status().value() == HttpStatus.UNAUTHORIZED.value();
	}

	private static ApiProblemException mapRetriedListError(Throwable error) {
		if (isUnauthorized(error)) {
			return new ApiProblemException(
					HttpStatus.SERVICE_UNAVAILABLE,
					"SERVICE_UNAVAILABLE",
					"服务暂不可用",
					"身份服务暂时无法确认当前权限，请稍后重试。");
		}
		return GatewayAuthenticationService.mapUpstream(error, false);
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
