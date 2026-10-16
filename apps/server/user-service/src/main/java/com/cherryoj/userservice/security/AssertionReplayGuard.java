package com.cherryoj.userservice.security;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 同一份客户端断言（按 jti）只能换一次令牌。记录保留到断言过期为止，所以内存占用以
 * "两分钟内的换取次数"为上限。单实例部署；多实例时需要换成共享存储。
 */
@Component
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class AssertionReplayGuard {

    private final Map<String, Instant> seen = new ConcurrentHashMap<>();
    private final Clock clock;

    public AssertionReplayGuard(Clock clock) {
        this.clock = clock;
    }

    /** 首次出现返回 true；重放返回 false。 */
    public boolean firstUse(String caller, String jti, Instant expiresAt) {
        Instant now = clock.instant();
        seen.values().removeIf(expiry -> expiry.isBefore(now));
        return seen.putIfAbsent(caller + ":" + jti, expiresAt) == null;
    }
}
