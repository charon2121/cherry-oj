package com.cherryoj.identity.resource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.identity.token.ServiceNames;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class EndpointPoliciesTests {
    @Test
    void theFirstMatchingDeclarationWinsAndMethodsNarrowIt() {
        var policies = EndpointPolicies.builder()
                .endpoint(HttpMethod.GET, "/internal/public/problems", "/internal/public/problems/*")
                .callers(ServiceNames.GATEWAY)
                .withoutUser()
                .endpoint("/internal/**")
                .callers(ServiceNames.JUDGING)
                .admin()
                .build();
        assertThat(policies.match("GET", "/internal/public/problems/3")
                        .orElseThrow()
                        .user())
                .isEqualTo(EndpointPolicies.UserRequirement.NONE);
        assertThat(policies.match("POST", "/internal/public/problems/3")
                        .orElseThrow()
                        .user())
                .isEqualTo(EndpointPolicies.UserRequirement.ADMIN);
        assertThat(policies.match("GET", "/api/x")).isEmpty();
    }

    @Test
    void declarationsMustNameKnownCallers() {
        assertThatThrownBy(() -> EndpointPolicies.builder().endpoint("/x").callers("nobody-service"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EndpointPolicies.builder().endpoint("/x").withoutUser())
                .isInstanceOf(IllegalStateException.class);
    }
}
