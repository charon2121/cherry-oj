package com.cherryoj.identity.resource;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** 协议 §3.3 的四种失败响应。原因码只进日志，不返回。 */
enum CallRejection {
    CALL_UNAUTHENTICATED(401, "调用令牌无效"),
    CALLER_FORBIDDEN(403, "调用方无权调用此端点"),
    USER_REQUIRED(401, "此端点需要用户"),
    FORBIDDEN(403, "当前身份无权执行此操作");

    private static final Logger LOGGER = LoggerFactory.getLogger(CallRejection.class);
    private final int status;
    private final String title;

    CallRejection(int status, String title) {
        this.status = status;
        this.title = title;
    }

    void write(HttpServletRequest request, HttpServletResponse response, String caller, String reason)
            throws IOException {
        LOGGER.atWarn()
                .addKeyValue("event", "call_rejected")
                .addKeyValue("caller", caller == null ? "" : caller)
                .addKeyValue("path", request.getRequestURI())
                .addKeyValue("code", name())
                .addKeyValue("reason", reason)
                .log("call_rejected");
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/problem+json");
        response.setHeader("Cache-Control", "no-store");
        response.getWriter()
                .write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status + ",\"code\":\""
                        + name() + "\"}");
    }
}
