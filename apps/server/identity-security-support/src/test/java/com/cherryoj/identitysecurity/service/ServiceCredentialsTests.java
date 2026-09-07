package com.cherryoj.identitysecurity.service;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.junit.jupiter.api.Assertions.*;

class ServiceCredentialsTests {
    private static final String CURRENT = "submission_problem_" + "a".repeat(32);
    private static final String OLD = "submission_problem_" + "b".repeat(32);

    @Test void credentialsAreScopedAndRotateWithoutAcceptingUserOrNodeTokens() {
        var keys = new ServiceCredentials(List.of(CURRENT, OLD));
        assertTrue(keys.accepts("Bearer " + CURRENT));
        assertTrue(keys.accepts("Bearer " + OLD));
        for (String invalid : List.of("", "Bearer local-judge-control-token", "Bearer eyJ.user.signature",
                "Bearer submission_judging_" + "a".repeat(32), "Bearer " + CURRENT + " ", "Basic " + CURRENT)) {
            assertFalse(keys.accepts(invalid));
        }
        assertFalse(keys.accepts(null));
        assertFalse(new ServiceCredentials(List.of(CURRENT)).accepts("Bearer " + OLD));
        assertThrows(IllegalArgumentException.class, () -> new ServiceCredentials(List.of()));
        assertThrows(IllegalArgumentException.class, () -> new ServiceCredentials(List.of("")));
        assertThrows(IllegalArgumentException.class, () -> new ServiceCredentials(List.of("x".repeat(513))));
    }

    @Test void filterDoesNotTrustCallerHeadersAndNeverRetainsCredentials() throws Exception {
        var filter = new ServiceTokenFilter("submission-service", new ServiceCredentials(List.of(CURRENT)));
        var request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + CURRENT);
        request.addHeader("X-Caller", "administrator");
        var response = new MockHttpServletResponse();
        var reached = new AtomicBoolean();
        filter.doFilter(request, response, (req, res) -> {
            reached.set(true);
            var identity = SecurityContextHolder.getContext().getAuthentication();
            assertEquals("submission-service", identity.getName());
            assertNull(identity.getCredentials());
            assertFalse(identity.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        });
        assertTrue(reached.get());
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("no-store", response.getHeader("Cache-Control"));
    }

    @Test void duplicateOrMissingAuthorizationIsRejectedWithoutEcho() throws Exception {
        for (boolean duplicate : List.of(false, true)) {
            var filter = new ServiceTokenFilter("judging-service", new ServiceCredentials(List.of(CURRENT)));
            var request = new MockHttpServletRequest();
            if (duplicate) {
                request.addHeader("Authorization", "Bearer " + CURRENT);
                request.addHeader("Authorization", "Bearer " + CURRENT);
            }
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> fail("must not reach protected input"));
            assertEquals(401, response.getStatus());
            assertFalse(response.getContentAsString().contains(CURRENT));
        }
    }
}
