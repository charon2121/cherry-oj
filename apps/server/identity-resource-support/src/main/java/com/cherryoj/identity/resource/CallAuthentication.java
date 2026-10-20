package com.cherryoj.identity.resource;

import com.cherryoj.identity.token.CallUser;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** 通过校验的一次调用：谁在调，代表哪个用户（端点不需要用户时为空）。 */
public final class CallAuthentication extends AbstractAuthenticationToken {
    private final String caller;
    private final CallUser user;

    public CallAuthentication(String caller, CallUser user) {
        super(authorities(caller, user));
        this.caller = caller;
        this.user = user;
        setAuthenticated(true);
    }

    public String caller() {
        return caller;
    }

    public Optional<CallUser> user() {
        return Optional.ofNullable(user);
    }

    @Override
    public Object getCredentials() {
        return "";
    }

    @Override
    public Object getPrincipal() {
        return user == null ? caller : user.id();
    }

    private static List<GrantedAuthority> authorities(String caller, CallUser user) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("CALLER_" + caller));
        if (user != null) authorities.add(new SimpleGrantedAuthority("ROLE_" + user.role()));
        return authorities;
    }
}
