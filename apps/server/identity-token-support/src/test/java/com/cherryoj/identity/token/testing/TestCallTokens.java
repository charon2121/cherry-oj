package com.cherryoj.identity.token.testing;

import com.cherryoj.identity.token.CallToken;
import com.cherryoj.identity.token.CallUser;
import com.cherryoj.identity.token.ServiceKeys;
import java.time.Instant;

/**
 * 各服务测试里冒充任一服务发起调用。密钥是 test-service-identity/ 下仅供测试的五对密钥，服务的测试配置写：
 * cherry.service-identity.private-key-location=classpath:test-service-identity/&lt;服务名&gt;-private.pem，
 * cherry.service-identity.public-keys-dir=classpath:test-service-identity。
 */
public final class TestCallTokens {
    public static final CallUser USER = new CallUser("019c8e42-7f70-7000-8000-000000000001", CallUser.USER);
    public static final CallUser ADMIN = new CallUser("019c8e42-7f70-7000-8000-0000000000ad", CallUser.ADMIN);

    private TestCallTokens() {}

    /** caller 调 audience 的 Authorization 头；user 为 null 时不代表用户。 */
    public static String authorization(String caller, String audience, CallUser user) {
        return "Bearer " + token(caller, audience, user);
    }

    public static String token(String caller, String audience, CallUser user) {
        return CallToken.sign(
                caller,
                ServiceKeys.privateKey("classpath:test-service-identity/" + caller + "-private.pem"),
                audience,
                user,
                Instant.now());
    }
}
