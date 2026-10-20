package com.cherryoj.userservice.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** mode：server 提供接口，bootstrap 只创建初始管理员后退出。会话自登录起按固定时长过期。 */
@ConfigurationProperties("cherry.auth")
public record AuthProperties(String mode, long sessionAbsoluteTimeoutSeconds) {
    public AuthProperties {
        mode = mode == null ? "server" : mode;
        if (sessionAbsoluteTimeoutSeconds < 86_400 || sessionAbsoluteTimeoutSeconds > 7_776_000) {
            throw new IllegalArgumentException(
                    "cherry.auth.session-absolute-timeout-seconds must be within [86400, 7776000]");
        }
    }

    public Duration sessionAbsoluteTimeout() {
        return Duration.ofSeconds(sessionAbsoluteTimeoutSeconds);
    }
}
