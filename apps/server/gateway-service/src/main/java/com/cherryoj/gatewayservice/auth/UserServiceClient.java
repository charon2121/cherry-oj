package com.cherryoj.gatewayservice.auth;

import com.cherryoj.serviceidentity.ServiceIdentityException;
import com.cherryoj.serviceidentity.ServiceNames;
import com.cherryoj.serviceidentity.ServiceTokenClient;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
final class UserServiceClient {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(UserServiceClient.class);

    private final WebClient client;
    private final GatewayAuthProperties properties;
    private final InternalRequestFactory requests;
    private final ServiceTokenClient serviceTokens;

    UserServiceClient(
            WebClient.Builder builder,
            GatewayAuthProperties properties,
            InternalRequestFactory requests,
            ServiceTokenClient serviceTokens) {
        this.client =
                builder.baseUrl(properties.userServiceBaseUrl().toString()).build();
        this.properties = properties;
        this.requests = requests;
        this.serviceTokens = serviceTokens;
    }

    // 登录、换取委托令牌、校验与吊销会话没有用户在场，gateway 用自己的服务身份调用；
    // user-service 只对 gateway-service 开放这四个端点。

    Mono<AuthenticationResult> authenticate(String username, String password, String requestId) {
        return asGateway(token -> response(
                requests.asService(client.post().uri("/internal/auth/authenticate"), requestId, token)
                        .bodyValue(new LoginRequest(username, password)),
                AuthenticationResult.class));
    }

    Mono<TokenExchangeResult> exchange(String loginGrant, String requestId) {
        return asGateway(token -> response(
                requests.asService(client.post().uri("/internal/auth/token"), requestId, token)
                        .bodyValue(new LoginGrantRequest(loginGrant)),
                TokenExchangeResult.class));
    }

    Mono<SessionValidationResult> validate(String loginGrant, String requestId) {
        return asGateway(token -> response(
                requests.asService(client.post().uri("/internal/auth/validate"), requestId, token)
                        .bodyValue(new LoginGrantRequest(loginGrant)),
                SessionValidationResult.class));
    }

    Mono<Void> revoke(String loginGrant, String requestId) {
        return asGateway(
                token -> noContent(requests.asService(client.post().uri("/internal/auth/revoke"), requestId, token)
                        .bodyValue(new LoginGrantRequest(loginGrant))));
    }

    /**
     * 取服务令牌（命中缓存时不发请求；换取是阻塞调用，放到 boundedElastic）。user-service 拒绝了缓存的
     * 令牌时丢弃它，下一次重新换取。
     */
    private <T> Mono<T> asGateway(java.util.function.Function<String, Mono<T>> call) {
        return Mono.fromCallable(() -> serviceTokens.tokenFor(ServiceNames.USER))
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorMap(ServiceIdentityException.class, error -> {
                    // 对浏览器仍是"服务暂不可用"；运维需要知道是 user-service 不可达、gateway 未登记还是被拒。
                    LOGGER.atWarn()
                            .addKeyValue("event", "gateway_service_token_unavailable")
                            .addKeyValue("reason", error.reason())
                            .log(error.getMessage());
                    return new UserServiceClientException(
                            HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_IDENTITY_UNAVAILABLE");
                })
                .flatMap(call)
                .doOnError(UserServiceClientException.class, error -> {
                    if (error.status().value() == 401 && "SERVICE_UNAUTHORIZED".equals(error.code())) {
                        serviceTokens.invalidate(ServiceNames.USER);
                    }
                });
    }

    Mono<Void> changePassword(DelegatedIdentity identity, String currentPassword, String newPassword) {
        return noContent(requests.authenticated(client.post().uri("/internal/users/me/password"), identity)
                .bodyValue(new ChangePasswordRequest(currentPassword, newPassword)));
    }

    Mono<UserPage> listUsers(DelegatedIdentity identity, int page, int size) {
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

    Mono<CreatedUser> createUser(DelegatedIdentity identity, String username) {
        return response(
                requests.authenticated(client.post().uri("/internal/admin/users"), identity)
                        .bodyValue(new CreateUserRequest(username)),
                CreatedUser.class);
    }

    Mono<InternalUser> updateStatus(DelegatedIdentity identity, String userId, String status, long rowVersion) {
        return response(
                requests.authenticated(client.patch().uri("/internal/admin/users/{userId}/status", userId), identity)
                        .bodyValue(new UpdateStatusRequest(status, rowVersion)),
                InternalUser.class);
    }

    Mono<CreatedUser> resetPassword(DelegatedIdentity identity, String userId, long rowVersion) {
        return response(
                requests.authenticated(
                                client.post().uri("/internal/admin/users/{userId}/password-reset", userId), identity)
                        .bodyValue(new ResetPasswordRequest(rowVersion)),
                CreatedUser.class);
    }

    Mono<IdentityMetadata> identityMetadata(String requestId) {
        return response(
                requests.request(client.get().uri("/internal/identity/metadata"), requestId), IdentityMetadata.class);
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

    record LoginGrantRequest(String loginGrant) {}

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

    record AuthenticationResult(
            InternalUser user,
            String loginGrant,
            String accessToken,
            Instant accessTokenExpiresAt,
            LocalDateTime sessionAbsoluteExpiresAt,
            long sessionAbsoluteTimeoutSeconds,
            String sessionLifetimePolicy) {}

    record TokenExchangeResult(
            InternalUser user,
            String accessToken,
            Instant accessTokenExpiresAt,
            LocalDateTime sessionAbsoluteExpiresAt,
            long sessionAbsoluteTimeoutSeconds,
            String sessionLifetimePolicy) {}

    record SessionValidationResult(
            LocalDateTime sessionAbsoluteExpiresAt, long sessionAbsoluteTimeoutSeconds, String sessionLifetimePolicy) {}

    record CreatedUser(InternalUser user, String temporaryPassword) {}

    record UserPage(List<InternalUser> items, int page, int size, long totalElements, int totalPages) {}

    record IdentityMetadata(
            String activeKid,
            List<String> publishedKids,
            String algorithm,
            long accessTokenTtlSeconds,
            String generation) {}
}
