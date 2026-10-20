package com.cherryoj.identity.resource;

import com.cherryoj.identity.token.ServiceNames;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpMethod;
import org.springframework.util.AntPathMatcher;

/**
 * 本服务每个端点的策略：允许的调用方、是否需要用户、所需角色（协议 §4）。按声明顺序匹配，第一条命中的生效；
 * 没有命中的端点不接受调用令牌。这里就是"谁能调本服务的哪个端点"的唯一出处。
 */
public final class EndpointPolicies {
    public enum UserRequirement {
        NONE,
        ANY,
        ADMIN
    }

    public record Policy(HttpMethod method, List<String> patterns, Set<String> callers, UserRequirement user) {}

    private static final AntPathMatcher PATHS = new AntPathMatcher();
    private final List<Policy> policies;

    private EndpointPolicies(List<Policy> policies) {
        this.policies = List.copyOf(policies);
    }

    public static Builder builder() {
        return new Builder();
    }

    public Optional<Policy> match(String method, String path) {
        return policies.stream()
                .filter(policy -> policy.method() == null || policy.method().matches(method))
                .filter(policy -> policy.patterns().stream().anyMatch(pattern -> PATHS.match(pattern, path)))
                .findFirst();
    }

    public List<Policy> policies() {
        return policies;
    }

    public static final class Builder {
        private final List<Policy> policies = new ArrayList<>();

        /** method 为 null 表示任意方法。 */
        public Endpoint endpoint(HttpMethod method, String... patterns) {
            return new Endpoint(this, method, List.of(patterns));
        }

        public Endpoint endpoint(String... patterns) {
            return endpoint(null, patterns);
        }

        public EndpointPolicies build() {
            return new EndpointPolicies(policies);
        }
    }

    public static final class Endpoint {
        private final Builder builder;
        private final HttpMethod method;
        private final List<String> patterns;
        private Set<String> callers;

        private Endpoint(Builder builder, HttpMethod method, List<String> patterns) {
            if (patterns.isEmpty()) throw new IllegalArgumentException("an endpoint needs at least one path pattern");
            this.builder = builder;
            this.method = method;
            this.patterns = patterns;
        }

        public Endpoint callers(String... names) {
            if (names.length == 0) throw new IllegalArgumentException("an endpoint needs at least one caller");
            for (String name : names) {
                if (!ServiceNames.ALL.contains(name)) throw new IllegalArgumentException("unknown service: " + name);
            }
            this.callers = Set.of(names);
            return this;
        }

        /** 不需要用户：被调方不读取 user。 */
        public Builder withoutUser() {
            return add(UserRequirement.NONE);
        }

        /** 需要用户，任意角色。 */
        public Builder anyUser() {
            return add(UserRequirement.ANY);
        }

        /** 需要用户，ADMIN。 */
        public Builder admin() {
            return add(UserRequirement.ADMIN);
        }

        private Builder add(UserRequirement user) {
            if (callers == null) throw new IllegalStateException("declare callers(...) before the user requirement");
            builder.policies.add(new Policy(method, patterns, callers, user));
            return builder;
        }
    }
}
