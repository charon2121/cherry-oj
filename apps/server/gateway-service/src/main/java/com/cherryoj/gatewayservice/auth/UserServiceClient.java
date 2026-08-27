package com.cherryoj.gatewayservice.auth;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

@Component
final class UserServiceClient {

	private final WebClient client;
	private final GatewayAuthProperties properties;

	UserServiceClient(WebClient.Builder builder, GatewayAuthProperties properties) {
		this.client = builder.baseUrl(properties.userServiceBaseUrl().toString()).build();
		this.properties = properties;
	}

	Mono<AuthenticationResult> authenticate(String username, String password, String requestId) {
		return response(client.post()
				.uri("/internal/auth/authenticate")
				.header("X-Request-Id", requestId)
				.bodyValue(new LoginRequest(username, password)), AuthenticationResult.class);
	}

	Mono<TokenExchangeResult> exchange(String loginGrant, String requestId) {
		return response(client.post()
				.uri("/internal/auth/token")
				.header("X-Request-Id", requestId)
				.bodyValue(new LoginGrantRequest(loginGrant)), TokenExchangeResult.class);
	}

	Mono<SessionTouchResult> touch(String loginGrant, String requestId) {
		return response(client.post()
				.uri("/internal/auth/touch")
				.header("X-Request-Id", requestId)
				.bodyValue(new LoginGrantRequest(loginGrant)), SessionTouchResult.class);
	}

	Mono<Void> revoke(String loginGrant, String requestId) {
		return noContent(client.post()
				.uri("/internal/auth/revoke")
				.header("X-Request-Id", requestId)
				.bodyValue(new LoginGrantRequest(loginGrant)));
	}

	Mono<Void> changePassword(
			String accessToken, String currentPassword, String newPassword, String requestId) {
		return noContent(client.post()
				.uri("/internal/users/me/password")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header("X-Request-Id", requestId)
				.bodyValue(new ChangePasswordRequest(currentPassword, newPassword)));
	}

	Mono<UserPage> listUsers(String accessToken, int page, int size, String requestId) {
		return response(client.get()
				.uri(builder -> builder.path("/internal/admin/users")
						.queryParam("page", page).queryParam("size", size).build())
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header("X-Request-Id", requestId), UserPage.class);
	}

	Mono<CreatedUser> createUser(String accessToken, String username, String requestId) {
		return response(client.post()
				.uri("/internal/admin/users")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header("X-Request-Id", requestId)
				.bodyValue(new CreateUserRequest(username)), CreatedUser.class);
	}

	Mono<InternalUser> updateStatus(
			String accessToken, String userId, String status, long rowVersion, String requestId) {
		return response(client.patch()
				.uri("/internal/admin/users/{userId}/status", userId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header("X-Request-Id", requestId)
				.bodyValue(new UpdateStatusRequest(status, rowVersion)), InternalUser.class);
	}

	Mono<CreatedUser> resetPassword(
			String accessToken, String userId, long rowVersion, String requestId) {
		return response(client.post()
				.uri("/internal/admin/users/{userId}/password-reset", userId)
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
				.header("X-Request-Id", requestId)
				.bodyValue(new ResetPasswordRequest(rowVersion)), CreatedUser.class);
	}

	private <T> Mono<T> response(WebClient.RequestHeadersSpec<?> request, Class<T> type) {
		return request.exchangeToMono(response -> {
			if (response.statusCode().is2xxSuccessful()) {
				return response.bodyToMono(type);
			}
			return error(response.statusCode(), response.bodyToMono(InternalError.class));
		}).timeout(properties.userServiceTimeout());
	}

	private Mono<Void> noContent(WebClient.RequestHeadersSpec<?> request) {
		return request.exchangeToMono(response -> {
			if (response.statusCode().is2xxSuccessful()) {
				return response.releaseBody();
			}
			return error(response.statusCode(), response.bodyToMono(InternalError.class));
		}).timeout(properties.userServiceTimeout());
	}

	private static <T> Mono<T> error(HttpStatusCode status, Mono<InternalError> body) {
		return body.defaultIfEmpty(new InternalError("UPSTREAM_ERROR", ""))
				.flatMap(error -> Mono.error(new UserServiceClientException(status, safeCode(error.code()))));
	}

	private static String safeCode(String code) {
		return code != null && code.matches("^[A-Z][A-Z0-9_]{0,63}$") ? code : "UPSTREAM_ERROR";
	}

	record LoginRequest(String username, String password) {
	}

	record LoginGrantRequest(String loginGrant) {
	}

	record ChangePasswordRequest(String currentPassword, String newPassword) {
	}

	record CreateUserRequest(String username) {
	}

	record UpdateStatusRequest(String status, long rowVersion) {
	}

	record ResetPasswordRequest(long rowVersion) {
	}

	record InternalError(String code, String message) {
	}

	record InternalUser(
			String id,
			String username,
			String role,
			String status,
			boolean passwordChangeRequired,
			LocalDateTime createdAt,
			LocalDateTime updatedAt,
			long rowVersion) {
		UserAccountData publicView() {
			return new UserAccountData(id, username, role, status, passwordChangeRequired,
					createdAt.toInstant(java.time.ZoneOffset.UTC),
					updatedAt.toInstant(java.time.ZoneOffset.UTC), rowVersion);
		}
	}

	record AuthenticationResult(
			InternalUser user,
			String loginGrant,
			String accessToken,
			Instant accessTokenExpiresAt,
			LocalDateTime sessionIdleExpiresAt,
			LocalDateTime sessionAbsoluteExpiresAt,
			long sessionIdleTimeoutSeconds,
			long sessionAbsoluteTimeoutSeconds,
			boolean sessionRefreshIdleOnActivity) {
	}

	record TokenExchangeResult(
			InternalUser user,
			String accessToken,
			Instant accessTokenExpiresAt,
			LocalDateTime sessionIdleExpiresAt,
			LocalDateTime sessionAbsoluteExpiresAt,
			long sessionIdleTimeoutSeconds,
			long sessionAbsoluteTimeoutSeconds,
			boolean sessionRefreshIdleOnActivity) {
	}

	record SessionTouchResult(
			LocalDateTime sessionIdleExpiresAt,
			LocalDateTime sessionAbsoluteExpiresAt,
			long sessionIdleTimeoutSeconds,
			long sessionAbsoluteTimeoutSeconds,
			boolean sessionRefreshIdleOnActivity) {
	}

	record CreatedUser(InternalUser user, String temporaryPassword) {
	}

	record UserPage(List<InternalUser> items, int page, int size, long totalElements, int totalPages) {
	}
}
