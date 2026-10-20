package com.cherryoj.gatewayservice.auth;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.api.DownstreamCallRejection;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.WebSession;
import reactor.core.publisher.Mono;

@Service
final class GatewayAuthenticationService {
    /** 距上次向 user-service 核对超过这么久，下一个请求先核对再放行（被禁用、改密码、改资料都在此后生效）。 */
    static final Duration VALIDATE_AFTER = Duration.ofSeconds(30);

    static final String SESSION_STATE = GatewayAuthenticationService.class.getName() + ".state";

    private final UserServiceClient userService;
    private final ConcurrentHashMap<String, Mono<AuthSessionState>> validations = new ConcurrentHashMap<>();
    private final Clock clock;

    @Autowired
    GatewayAuthenticationService(UserServiceClient userService) {
        this(userService, Clock.systemUTC());
    }

    GatewayAuthenticationService(UserServiceClient userService, Clock clock) {
        this.userService = userService;
        this.clock = clock;
    }

    Mono<AuthSessionState> login(WebSession session, String username, String password, String requestId) {
        return userService
                .authenticate(username, password, requestId)
                .onErrorMap(error -> mapUpstream(error, true))
                .flatMap(result -> {
                    AuthSessionState state = new AuthSessionState(
                            result.user().publicView(), result.sessionId(), result.sessionExpiresAt(), clock.instant());
                    // 登录后换一个浏览器会话编号，登录前拿到的编号不能沿用到登录后。
                    return session.changeSessionId()
                            .then(Mono.fromRunnable(() -> store(session, state)))
                            .thenReturn(state);
                });
    }

    /** 当前登录状态；超过 VALIDATE_AFTER 先向 user-service 核对。同一浏览器会话的并发请求只核对一次。 */
    Mono<AuthSessionState> current(WebSession session, String requestId) {
        AuthSessionState state = session.getAttribute(SESSION_STATE);
        if (state == null) {
            return Mono.error(unauthenticated());
        }
        // 升级前保存的会话缺少这些字段：按未登录处理，让用户重新登录。
        if (state.user() == null
                || state.sessionId() == null
                || state.sessionExpiresAt() == null
                || state.validatedAt() == null) {
            return invalidateThenUnauthenticated(session);
        }
        Instant now = clock.instant();
        if (!now.isBefore(state.sessionExpiresAt())) {
            return invalidateThenUnauthenticated(session);
        }
        if (now.isBefore(state.validatedAt().plus(VALIDATE_AFTER))) {
            return Mono.just(state);
        }
        return validations
                .computeIfAbsent(state.sessionId(), ignored -> validate(state, requestId))
                .map(validated -> storeIfCurrent(session, validated))
                .onErrorResume(
                        GatewayAuthenticationService::isUnauthenticated,
                        error -> invalidateThenUnauthenticated(session));
    }

    Mono<Void> logout(WebSession session, String requestId) {
        AuthSessionState state = session.getAttribute(SESSION_STATE);
        if (state == null) {
            return session.invalidate();
        }
        return userService
                .revoke(state.sessionId(), requestId)
                // 会话在 user-service 那边已经失效，退出的目的已经达到。
                .onErrorResume(GatewayAuthenticationService::isSessionInvalid, error -> Mono.empty())
                .onErrorMap(error -> mapUpstream(error, false))
                .then(session.invalidate())
                .onErrorResume(error -> session.invalidate().then(Mono.error(error)));
    }

    Mono<Void> invalidate(WebSession session) {
        return session.invalidate();
    }

    private Mono<AuthSessionState> validate(AuthSessionState previous, String requestId) {
        return userService
                .validate(previous.sessionId(), requestId)
                .map(result -> new AuthSessionState(
                        result.user().publicView(), previous.sessionId(), result.sessionExpiresAt(), clock.instant()))
                .onErrorMap(GatewayAuthenticationService::isSessionInvalid, error -> unauthenticated())
                .onErrorMap(error -> !isUnauthenticated(error), error -> mapUpstream(error, false))
                .doFinally(signal -> validations.remove(previous.sessionId()))
                .cache();
    }

    /** 核对期间会话可能已经退出或换了账号：只写回仍是同一个 sessionId 的会话。 */
    private AuthSessionState storeIfCurrent(WebSession session, AuthSessionState validated) {
        AuthSessionState current = session.getAttribute(SESSION_STATE);
        if (current == null || !current.sessionId().equals(validated.sessionId())) {
            throw unauthenticated();
        }
        if (!current.validatedAt().isAfter(validated.validatedAt())) {
            store(session, validated);
            return validated;
        }
        return current;
    }

    private static boolean isSessionInvalid(Throwable error) {
        return error instanceof UserServiceClientException upstream
                && upstream.status().value() == 401
                && "SESSION_INVALID".equals(upstream.code());
    }

    private static boolean isUnauthenticated(Throwable error) {
        return error instanceof ApiProblemException problem
                && problem.status().value() == HttpStatus.UNAUTHORIZED.value()
                && "UNAUTHENTICATED".equals(problem.code());
    }

    private void store(WebSession session, AuthSessionState state) {
        session.getAttributes().put(SESSION_STATE, state);
        Duration remaining = Duration.between(clock.instant(), state.sessionExpiresAt());
        session.setMaxIdleTime(remaining.isNegative() ? Duration.ZERO : remaining);
    }

    private <T> Mono<T> invalidateThenUnauthenticated(WebSession session) {
        return session.invalidate().then(Mono.error(unauthenticated()));
    }

    static ApiProblemException mapUpstream(Throwable error, boolean authentication) {
        if (error instanceof ApiProblemException apiProblem) {
            return apiProblem;
        }
        if (error instanceof TimeoutException) {
            return gatewayTimeout();
        }
        if (error instanceof UserServiceClientException upstream) {
            if (authentication && upstream.status().value() == 401) {
                return new ApiProblemException(
                        HttpStatus.UNAUTHORIZED, "AUTHENTICATION_FAILED", "登录失败", "用户名或密码错误，或账号当前不可登录。");
            }
            if (!authentication
                    && DownstreamCallRejection.isTrustFailure(upstream.status().value(), upstream.code())) {
                return DownstreamCallRejection.unavailable();
            }
            if (upstream.status().is4xxClientError()) {
                HttpStatus status = HttpStatus.resolve(upstream.status().value());
                return new ApiProblemException(
                        status == null ? HttpStatus.BAD_GATEWAY : status,
                        upstream.code(),
                        clientTitle(upstream.status().value()),
                        clientDetail(upstream.status().value()));
            }
        }
        return new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "服务暂不可用", "服务暂时不可用，请稍后重试。");
    }

    /** 代表用户调用 user-service 失败：调用令牌被拒说明信任配置不一致，记日志后对浏览器统一为暂不可用。 */
    static ApiProblemException mapUserCallUpstream(Throwable error, String requestId) {
        if (error instanceof UserServiceClientException upstream) {
            DownstreamCallRejection.record(
                    "user-service", requestId, upstream.status().value(), upstream.code());
        }
        return mapUpstream(error, false);
    }

    private static String clientTitle(int status) {
        return switch (status) {
            case 401 -> "未认证";
            case 403 -> "无权访问";
            case 404 -> "资源不存在";
            case 409 -> "资源状态冲突";
            case 422 -> "请求参数校验失败";
            case 429 -> "请求过于频繁";
            default -> "请求失败";
        };
    }

    private static String clientDetail(int status) {
        return switch (status) {
            case 401 -> "请先完成登录认证。";
            case 403 -> "当前身份无权执行此操作。";
            case 404 -> "请求的资源不存在。";
            case 409 -> "资源当前状态不允许此操作。";
            case 422 -> "请检查请求参数。";
            case 429 -> "请稍后重试。";
            default -> "请求无法完成。";
        };
    }

    private static ApiProblemException unauthenticated() {
        return new ApiProblemException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "未认证", "请先完成登录认证。");
    }

    private static ApiProblemException gatewayTimeout() {
        return new ApiProblemException(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT", "上游响应超时", "服务暂时不可用，请稍后重试。");
    }
}
