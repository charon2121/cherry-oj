package com.cherryoj.userservice.api;

import com.cherryoj.serviceidentity.ClientAssertion;
import com.cherryoj.serviceidentity.ServiceIdentityException;
import com.cherryoj.serviceidentity.ServiceNames;
import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.security.AssertionReplayGuard;
import com.cherryoj.userservice.security.ServiceClientRegistry;
import com.cherryoj.userservice.security.TokenService;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 服务令牌签发：调用方在 Authorization 里带自己私钥签的客户端断言，换取发往某个目标服务的短期令牌。
 * 这个端点本身不要求令牌（它就是换令牌的入口），身份完全由断言的签名证明。
 */
@RestController
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class ServiceTokenController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceTokenController.class);

    private final ServiceClientRegistry clients;
    private final AssertionReplayGuard replay;
    private final TokenService tokens;
    private final AuthProperties properties;
    private final Clock clock;

    public ServiceTokenController(
            ServiceClientRegistry clients,
            AssertionReplayGuard replay,
            TokenService tokens,
            AuthProperties properties,
            Clock clock) {
        this.clients = clients;
        this.replay = replay;
        this.tokens = tokens;
        this.properties = properties;
        this.clock = clock;
    }

    public record Request(String audience) {}

    public record Response(String accessToken, Instant expiresAt) {}

    @PostMapping("/internal/service-tokens")
    ResponseEntity<?> issue(
            @RequestHeader(name = "Authorization", required = false) String authorization,
            @RequestBody(required = false) Request request) {
        if (authorization == null || !authorization.startsWith("Bearer ") || authorization.length() > 8192) {
            return reject(HttpStatus.UNAUTHORIZED, "SERVICE_UNAUTHORIZED", "MISSING_ASSERTION", null);
        }
        ClientAssertion.Verified caller;
        try {
            caller = ClientAssertion.verify(
                    authorization.substring("Bearer ".length()),
                    clients::publicKey,
                    properties.issuer(),
                    clock.instant(),
                    properties.clockSkew());
        } catch (ServiceIdentityException error) {
            return reject(HttpStatus.UNAUTHORIZED, "SERVICE_UNAUTHORIZED", error.reason(), null);
        }
        if (!replay.firstUse(caller.caller(), caller.jti(), caller.expiresAt())) {
            return reject(HttpStatus.UNAUTHORIZED, "SERVICE_UNAUTHORIZED", "ASSERTION_REPLAYED", caller.caller());
        }
        String audience = request == null ? null : request.audience();
        if (!ServiceNames.valid(audience) || !clients.knownAudience(audience) || audience.equals(caller.caller())) {
            return reject(HttpStatus.BAD_REQUEST, "INVALID_AUDIENCE", "UNKNOWN_AUDIENCE", caller.caller());
        }
        var token = tokens.issueService(caller.caller(), audience);
        LOGGER.atInfo()
                .addKeyValue("event", "service_token_issued")
                .addKeyValue("caller", caller.caller())
                .addKeyValue("audience", audience)
                .log("service_token_issued");
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(new Response(token.value(), token.expiresAt()));
    }

    private static ResponseEntity<IdentityApiError> reject(
            HttpStatus status, String code, String reason, String caller) {
        LOGGER.atWarn()
                .addKeyValue("event", "service_token_rejected")
                .addKeyValue("reason", reason)
                .addKeyValue("caller", caller == null ? "" : caller)
                .log("service_token_rejected");
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new IdentityApiError(code, status == HttpStatus.BAD_REQUEST ? "目标服务未登记。" : "服务身份校验失败。"));
    }
}
