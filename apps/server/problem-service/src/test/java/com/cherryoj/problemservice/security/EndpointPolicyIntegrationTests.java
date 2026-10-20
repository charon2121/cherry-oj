package com.cherryoj.problemservice.security;

import static com.cherryoj.identity.token.ServiceNames.GATEWAY;
import static com.cherryoj.identity.token.ServiceNames.JUDGING;
import static com.cherryoj.identity.token.ServiceNames.PROBLEM;
import static com.cherryoj.identity.token.ServiceNames.SUBMISSION;
import static com.cherryoj.identity.token.testing.TestCallTokens.ADMIN;
import static com.cherryoj.identity.token.testing.TestCallTokens.USER;
import static com.cherryoj.identity.token.testing.TestCallTokens.authorization;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.identity.token.CallUser;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.LanguageSettingService;
import com.cherryoj.problemservice.application.ProblemPublicationService;
import com.cherryoj.problemservice.application.PublicProblemService;
import com.cherryoj.problemservice.application.SubmissionSnapshotService;
import com.cherryoj.problemservice.application.TestDataService;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 本服务声明的端点策略在完整的安全链上生效（docs/protocols/service-identity.md §3.3、§4）。 */
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "cherry.problem.test-data.recovery-enabled=false",
            "cherry.problem.test-data.root=${java.io.tmpdir}/cherry-oj-security-testdata"
        })
class EndpointPolicyIntegrationTests {
    private static final String PROBLEM_ID = "019c8e42-7f70-7000-8000-000000000010";

    @MockitoBean
    PublicProblemService publicProblems;

    @MockitoBean
    AdminProblemService adminProblems;

    @MockitoBean
    TestDataService testData;

    @MockitoBean
    ProblemPublicationService publication;

    @MockitoBean
    LanguageSettingService languages;

    @MockitoBean
    SubmissionSnapshotService snapshots;

    private final MockMvc mvc;

    @Autowired
    EndpointPolicyIntegrationTests(WebApplicationContext context) {
        this.mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void publicProblemsAreForTheGatewayWithoutAUser() throws Exception {
        expect(get("/internal/public/problems"), GATEWAY, null, status().isOk());
        expect(get("/internal/public/problems/two-sum"), GATEWAY, USER, status().isOk());
        expect(get("/internal/public/problems"), JUDGING, null, rejected(403, "CALLER_FORBIDDEN"));
        mvc.perform(get("/internal/public/problems")).andExpect(rejected(401, "CALL_UNAUTHENTICATED"));
    }

    @Test
    void administrationNeedsAnAdminForwardedByTheGateway() throws Exception {
        for (String path : new String[] {"/internal/admin/problems", "/internal/admin/languages"}) {
            expect(get(path), GATEWAY, ADMIN, status().isOk());
            expect(get(path), GATEWAY, USER, rejected(403, "FORBIDDEN"));
            expect(get(path), GATEWAY, null, rejected(401, "USER_REQUIRED"));
            expect(get(path), SUBMISSION, ADMIN, rejected(403, "CALLER_FORBIDDEN"));
        }
    }

    @Test
    void internalReadsBelongToTheirOneCaller() throws Exception {
        Supplier<MockHttpServletRequestBuilder> snapshot = () -> post("/internal/submission/problem-snapshot")
                .contentType("application/json")
                .content("{\"problemId\":\"" + PROBLEM_ID + "\",\"languageId\":\"cpp\"}");
        expect(snapshot.get(), SUBMISSION, null, status().isOk());
        expect(snapshot.get(), JUDGING, null, rejected(403, "CALLER_FORBIDDEN"));
        expect(snapshot.get(), GATEWAY, ADMIN, rejected(403, "CALLER_FORBIDDEN"));

        String testData = "/internal/judging/problems/" + PROBLEM_ID + "/test-data";
        expect(get(testData), JUDGING, null, status().isOk());
        expect(get(testData), SUBMISSION, null, rejected(403, "CALLER_FORBIDDEN"));
    }

    @Test
    void tokensForAnotherServiceAndUndeclaredEndpointsAreRejected() throws Exception {
        mvc.perform(get("/internal/public/problems").header("Authorization", authorization(GATEWAY, JUDGING, null)))
                .andExpect(rejected(401, "CALL_UNAUTHENTICATED"));
        expect(get("/internal/undeclared"), GATEWAY, ADMIN, rejected(403, "CALLER_FORBIDDEN"));
        // 健康检查不需要调用令牌（测试里没有数据库，状态可能是 503，但不是 401）
        mvc.perform(get("/actuator/health"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(
                                result.getResponse().getStatus())
                        .isIn(200, 503));
    }

    private void expect(MockHttpServletRequestBuilder request, String caller, CallUser user, ResultMatcher result)
            throws Exception {
        mvc.perform(request.header("Authorization", authorization(caller, PROBLEM, user)))
                .andExpect(result);
    }

    private static ResultMatcher rejected(int status, String code) {
        return result -> {
            status().is(status).match(result);
            content().contentTypeCompatibleWith("application/problem+json").match(result);
            jsonPath("$.code").value(code).match(result);
        };
    }
}
