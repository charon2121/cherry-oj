package com.cherryoj.submissionservice.security;

import static com.cherryoj.identity.token.ServiceNames.GATEWAY;
import static com.cherryoj.identity.token.ServiceNames.JUDGING;
import static com.cherryoj.identity.token.ServiceNames.SUBMISSION;
import static com.cherryoj.identity.token.testing.TestCallTokens.USER;
import static com.cherryoj.identity.token.testing.TestCallTokens.authorization;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.identity.resource.CurrentCall;
import com.cherryoj.identity.token.CallUser;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/** 本服务声明的端点策略（docs/protocols/service-identity.md §3.3、§4）；端点由探针控制器代替。 */
class EndpointPolicyTests {
    private AnnotationConfigWebApplicationContext context;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment()
                .getPropertySources()
                .addFirst(new MapPropertySource(
                        "identity",
                        Map.of(
                                "cherry.service-identity.name", SUBMISSION,
                                "cherry.service-identity.private-key-location",
                                        "classpath:test-service-identity/submission-service-private.pem",
                                "cherry.service-identity.public-keys-dir", "classpath:test-service-identity")));
        context.register(Config.class);
        context.refresh();
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @AfterEach
    void tearDown() {
        context.close();
    }

    @Test
    void submissionsAndCustomRunsActForTheUserTheGatewayForwards() throws Exception {
        expect(post("/api/submissions"), GATEWAY, USER, status().isOk());
        expect(get("/api/submissions/7/source"), GATEWAY, USER, status().isOk());
        expect(get("/api/submission-requests/k"), GATEWAY, USER, status().isOk());
        expect(post("/api/custom-runs"), GATEWAY, USER, status().isOk());
        mvc.perform(post("/api/submissions").header("Authorization", authorization(GATEWAY, SUBMISSION, USER)))
                .andExpect(content().string(USER.id()));

        expect(post("/api/submissions"), GATEWAY, null, rejected(401, "USER_REQUIRED"));
        expect(post("/api/custom-runs"), JUDGING, USER, rejected(403, "CALLER_FORBIDDEN"));
    }

    @Test
    void judgeInputsAreForJudgingOnly() throws Exception {
        expect(get("/internal/judging/judge-inputs/7"), JUDGING, null, status().isOk());
        expect(get("/internal/judging/judge-inputs/7"), GATEWAY, USER, rejected(403, "CALLER_FORBIDDEN"));
        mvc.perform(get("/internal/judging/judge-inputs/7")).andExpect(rejected(401, "CALL_UNAUTHENTICATED"));
    }

    private void expect(MockHttpServletRequestBuilder request, String caller, CallUser user, ResultMatcher result)
            throws Exception {
        mvc.perform(request.header("Authorization", authorization(caller, SUBMISSION, user)))
                .andExpect(result);
    }

    private static ResultMatcher rejected(int status, String code) {
        return result -> {
            status().is(status).match(result);
            jsonPath("$.code").value(code).match(result);
        };
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({SecurityConfig.class, Probe.class})
    static class Config {}

    @RestController
    static class Probe {
        @PostMapping({"/api/submissions", "/api/custom-runs"})
        String userId() {
            return CurrentCall.user().map(CallUser::id).orElse("");
        }

        @GetMapping({
            "/api/submissions/{id}/source",
            "/api/submission-requests/{key}",
            "/internal/judging/judge-inputs/{id}"
        })
        String ok() {
            return "ok";
        }
    }
}
