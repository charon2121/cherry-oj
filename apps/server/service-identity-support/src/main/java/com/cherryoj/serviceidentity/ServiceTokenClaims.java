package com.cherryoj.serviceidentity;

import java.time.Duration;

/** 服务令牌与客户端断言共用的声明名和时长上限。 */
public final class ServiceTokenClaims {

    /** 服务令牌带 token_use=service；用户委托 JWT 没有它，两类令牌因此不能互相冒用。 */
    public static final String TOKEN_USE = "token_use";

    public static final String SERVICE = "service";

    /** 调用方自签断言的寿命：只用来换一次服务令牌。 */
    public static final Duration ASSERTION_LIFETIME = Duration.ofSeconds(60);
    /** user-service 接受的断言寿命上限（exp - iat），防止调用方签发长期有效的断言。 */
    public static final Duration MAX_ASSERTION_LIFETIME = Duration.ofSeconds(120);
    /** 服务令牌寿命。调用方缓存复用，剩余不足 REFRESH_AHEAD 时换新。 */
    public static final Duration SERVICE_TOKEN_LIFETIME = Duration.ofMinutes(15);

    public static final Duration REFRESH_AHEAD = Duration.ofMinutes(5);

    private ServiceTokenClaims() {}
}
