package com.cherryoj.gatewayservice;

import com.cherryoj.identity.token.CallToken;
import com.cherryoj.identity.token.CallTokenSigner;
import com.cherryoj.identity.token.PublicKeyDirectory;
import com.cherryoj.identity.token.ServiceKeys;
import com.cherryoj.identity.token.ServiceNames;
import java.time.Clock;
import java.time.Instant;

/** gateway 测试里签发与核对调用令牌，密钥来自 identity-token-support 的测试密钥。 */
public final class TestCalls {
    public static final CallTokenSigner SIGNER = new CallTokenSigner(
            ServiceNames.GATEWAY,
            ServiceKeys.privateKey("classpath:test-service-identity/gateway-service-private.pem"),
            Clock.systemUTC());
    private static final PublicKeyDirectory KEYS = PublicKeyDirectory.load("classpath:test-service-identity");

    private TestCalls() {}

    /** 下游收到的 Authorization 头 → 校验通过的调用；audience 是下游服务名。 */
    public static CallToken.Verified verify(String authorization, String audience) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new AssertionError("expected a bearer call token, got: " + authorization);
        }
        return CallToken.verify(authorization.substring("Bearer ".length()), KEYS, audience, Instant.now());
    }
}
