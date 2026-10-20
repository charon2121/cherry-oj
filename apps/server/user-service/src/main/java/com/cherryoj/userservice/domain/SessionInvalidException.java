package com.cherryoj.userservice.domain;

/** sessionId 不存在、已吊销或已过期。 */
public final class SessionInvalidException extends RuntimeException {
    public SessionInvalidException() {
        super("登录状态已失效，请重新登录");
    }
}
