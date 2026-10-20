package com.cherryoj.gatewayservice.auth;

import com.cherryoj.identity.token.CallTokenSigner;
import com.cherryoj.identity.token.ServiceNames;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Component
final class UserServiceClient {

    private final WebClient client;
    private final GatewayAuthProperties properties;
    private final InternalRequests requests;

    UserServiceClient(WebClient.Builder builder, GatewayAuthProperties properties, CallTokenSigner calls) {
        this.client =
                builder.baseUrl(properties.userServiceBaseUrl().toString()).build();
        this.properties = properties;
        this.requests = new InternalRequests(calls, ServiceNames.USER);
    }

    // 会话接口（docs/protocols/service-identity.md §5）不代表用户；user-service 只接受 gateway-service 调用。

    Mono<AuthenticationResult> authenticate(String username, String password, String requestId) {
        return response(
                requests.request(client.post().uri("/internal/auth/authenticate"), requestId)
                        .bodyValue(new LoginRequest(username, password)),
                AuthenticationResult.class);
    }

    Mono<SessionValidationResult> validate(String sessionId, String requestId) {
        return response(
                requests.request(client.post().uri("/internal/auth/validate"), requestId)
                        .bodyValue(new SessionRequest(sessionId)),
                SessionValidationResult.class);
    }

    Mono<Void> revoke(String sessionId, String requestId) {
        return noContent(requests.request(client.post().uri("/internal/auth/revoke"), requestId)
                .bodyValue(new SessionRequest(sessionId)));
    }

    Mono<Void> changePassword(ActingUser identity, String currentPassword, String newPassword) {
        return noContent(requests.authenticated(client.post().uri("/internal/users/me/password"), identity)
                .bodyValue(new ChangePasswordRequest(currentPassword, newPassword)));
    }

    Mono<UserPage> listUsers(ActingUser identity, int page, int size) {
        return response(
                requests.authenticated(
                        client.get()
                                .uri(builder -> builder.path("/internal/admin/users")
                                        .queryParam("page", page)
                                        .queryParam("size", size)
                                        .build()),
                        identity),
                UserPage.class);
    }

    Mono<CreatedUser> createUser(ActingUser identity, String username) {
        return response(
                requests.authenticated(client.post().uri("/internal/admin/users"), identity)
                        .bodyValue(new CreateUserRequest(username)),
                CreatedUser.class);
    }

    Mono<InternalUser> updateStatus(ActingUser identity, String userId, String status, long rowVersion) {
        return response(
                requests.authenticated(client.patch().uri("/internal/admin/users/{userId}/status", userId), identity)
                        .bodyValue(new UpdateStatusRequest(status, rowVersion)),
                InternalUser.class);
    }

    Mono<CreatedUser> resetPassword(ActingUser identity, String userId, long rowVersion) {
        return response(
                requests.authenticated(
                                client.post().uri("/internal/admin/users/{userId}/password-reset", userId), identity)
                        .bodyValue(new ResetPasswordRequest(rowVersion)),
                CreatedUser.class);
    }

    private <T> Mono<T> response(WebClient.RequestHeadersSpec<?> request, Class<T> type) {
        return request.exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(type);
                    }
                    return error(response.statusCode(), response.bodyToMono(InternalError.class));
                })
                .timeout(properties.userServiceTimeout());
    }

    private Mono<Void> noContent(WebClient.RequestHeadersSpec<?> request) {
        return request.exchangeToMono(response -> {
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.releaseBody();
                    }
                    return error(response.statusCode(), response.bodyToMono(InternalError.class));
                })
                .timeout(properties.userServiceTimeout());
    }

    private static <T> Mono<T> error(HttpStatusCode status, Mono<InternalError> body) {
        return body.defaultIfEmpty(new InternalError("UPSTREAM_ERROR", ""))
                .flatMap(error -> Mono.error(new UserServiceClientException(status, safeCode(error.code()))));
    }

    private static String safeCode(String code) {
        return code != null && code.matches("^[A-Z][A-Z0-9_]{0,63}$") ? code : "UPSTREAM_ERROR";
    }

    record LoginRequest(String username, String password) {}

    record SessionRequest(String sessionId) {}

    record ChangePasswordRequest(String currentPassword, String newPassword) {}

    record CreateUserRequest(String username) {}

    record UpdateStatusRequest(String status, long rowVersion) {}

    record ResetPasswordRequest(long rowVersion) {}

    record InternalError(String code, String message) {}

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
            return new UserAccountData(
                    id,
                    username,
                    role,
                    status,
                    passwordChangeRequired,
                    createdAt.toInstant(java.time.ZoneOffset.UTC),
                    updatedAt.toInstant(java.time.ZoneOffset.UTC),
                    rowVersion);
        }
    }

    record AuthenticationResult(String sessionId, InternalUser user, Instant sessionExpiresAt) {}

    record SessionValidationResult(InternalUser user, Instant sessionExpiresAt) {}

    record CreatedUser(InternalUser user, String temporaryPassword) {}

    record UserPage(List<InternalUser> items, int page, int size, long totalElements, int totalPages) {}
}
