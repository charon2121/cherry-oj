package com.cherryoj.gatewayservice.api;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

/**
 * 下游拒绝了 gateway 签发的调用令牌（docs/protocols/service-identity.md §3.3 的前三种）：gateway 已经核对过会话和角色，
 * 出现这种拒绝说明服务间的信任配置不一致（密钥、端点策略），不是浏览器能处理的问题。记日志，对浏览器统一为暂不可用。
 * 角色不够（FORBIDDEN）不在此列，照常作为 403 返回。
 */
public final class DownstreamCallRejection {
    private static final Logger LOGGER = LoggerFactory.getLogger(DownstreamCallRejection.class);
    private static final Set<String> TRUST_FAILURES =
            Set.of("CALL_UNAUTHENTICATED", "CALLER_FORBIDDEN", "USER_REQUIRED");

    private DownstreamCallRejection() {}

    public static boolean isTrustFailure(int status, String code) {
        return (status == 401 || status == 403) && TRUST_FAILURES.contains(code);
    }

    public static void record(String service, String requestId, int status, String code) {
        LOGGER.atWarn()
                .addKeyValue("event", "identity_trust_violation")
                .addKeyValue("upstream_service", service)
                .addKeyValue("request_id", requestId == null ? "unavailable" : requestId)
                .addKeyValue("upstream_code", code)
                .log("Downstream service rejected a gateway call token");
    }

    public static ApiProblemException unavailable() {
        return new ApiProblemException(
                HttpStatus.SERVICE_UNAVAILABLE, "SERVICE_UNAVAILABLE", "服务暂不可用", "身份信任状态暂时不一致，请稍后重试。");
    }
}
