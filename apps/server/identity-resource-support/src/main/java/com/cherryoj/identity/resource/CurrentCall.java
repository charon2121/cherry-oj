package com.cherryoj.identity.resource;

import com.cherryoj.identity.token.CallUser;
import java.util.Optional;
import org.springframework.security.core.context.SecurityContextHolder;

/** 当前请求的调用方与用户。代表用户调用下游时，用 user() 原样转发（协议 §4）。 */
public final class CurrentCall {
    private CurrentCall() {}

    public static CallAuthentication authentication() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof CallAuthentication call) {
            return call;
        }
        throw new IllegalStateException("no verified call on this thread");
    }

    public static String caller() {
        return authentication().caller();
    }

    public static Optional<CallUser> user() {
        return authentication().user();
    }

    /** 端点策略要求用户时使用；策略保证此时一定有用户。 */
    public static CallUser requireUser() {
        return user().orElseThrow(() -> new IllegalStateException("this endpoint is declared without a user"));
    }
}
