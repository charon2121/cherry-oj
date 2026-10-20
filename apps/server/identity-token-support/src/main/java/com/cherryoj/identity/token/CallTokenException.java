package com.cherryoj.identity.token;

/** 调用令牌不能通过校验。reason 是稳定的原因码，只进日志；对外一律是 CALL_UNAUTHENTICATED。 */
public final class CallTokenException extends RuntimeException {
    private final String reason;

    public CallTokenException(String reason, String message) {
        super(message);
        this.reason = reason;
    }

    public String reason() {
        return reason;
    }
}
