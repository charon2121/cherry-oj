package com.cherryoj.identity.token;

import java.security.interfaces.RSAPrivateKey;
import java.time.Clock;

/** 本服务签发调用令牌：每次调用现签，不缓存。 */
public final class CallTokenSigner {
    private final String self;
    private final RSAPrivateKey key;
    private final Clock clock;

    public CallTokenSigner(String self, RSAPrivateKey key, Clock clock) {
        this.self = ServiceNames.require(self, "service name");
        this.key = java.util.Objects.requireNonNull(key);
        this.clock = java.util.Objects.requireNonNull(clock);
    }

    public String self() {
        return self;
    }

    /** 不代表用户的调用。 */
    public String sign(String audience) {
        return sign(audience, null);
    }

    /** user 为 null 时不代表用户。 */
    public String sign(String audience, CallUser user) {
        return CallToken.sign(self, key, audience, user, clock.instant());
    }

    /** Authorization 头的完整值。 */
    public String authorization(String audience, CallUser user) {
        return "Bearer " + sign(audience, user);
    }
}
