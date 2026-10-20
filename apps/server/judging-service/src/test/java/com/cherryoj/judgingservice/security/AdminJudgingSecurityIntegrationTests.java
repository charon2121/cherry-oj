package com.cherryoj.judgingservice.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.identity.token.CallUser;
import com.cherryoj.identity.token.ServiceNames;
import com.cherryoj.identity.token.testing.TestCallTokens;
import com.cherryoj.judgingservice.api.JudgingDtos.Readiness;
import com.cherryoj.judgingservice.application.JudgingReadinessService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:mysql://127.0.0.1:1/not-used",
            "spring.datasource.username=none"
        })
class AdminJudgingSecurityIntegrationTests {

    private final MockMvc mvc;

    @MockitoBean
    JudgingReadinessService service;

    @MockitoBean
    com.cherryoj.judgingservice.application.JudgeNodeRegistry nodes;

    @MockitoBean
    com.cherryoj.judgingservice.api.SubmissionExecutionProfileController executionProfiles;

    @Autowired
    AdminJudgingSecurityIntegrationTests(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void readinessRejectsAnonymousAndUserButAllowsAdminWithoutCaching() throws Exception {
        String path = "/internal/admin/readiness?problemId=019c8e42-7f70-7000-8000-000000000010"
                + "&languageId=cpp&testDataDigest=" + "0".repeat(64);
        when(service.readiness(anyString(), anyString(), anyString()))
                .thenReturn(new Readiness(false, List.of(), null));

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", gateway(TestCallTokens.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", problemService(TestCallTokens.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", problemService(TestCallTokens.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void judgeNodeStatusRejectsAnonymousAndUserButAllowsAdminWithoutCaching() throws Exception {
        String path = "/internal/admin/judge-nodes";
        when(nodes.status())
                .thenReturn(new com.cherryoj.judgingservice.api.JudgeNodeDtos.NodeStatusList(
                        List.of(new com.cherryoj.judgingservice.api.JudgeNodeDtos.NodeStatus(
                                "judge-local-1",
                                List.of("cpp"),
                                true,
                                java.time.Instant.parse("2026-10-16T04:00:00Z")))));

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", gateway(TestCallTokens.USER)))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", gateway(TestCallTokens.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.items[0].endpoint")
                        .doesNotExist())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath(
                                "$.items[0].lastSeenAt")
                        .value("2026-10-16T04:00:00Z"));
    }

    @Test
    void calibrationRejectsZeroMissingAndOverflowLimitsBeforeCallingService() throws Exception {
        String prefix = """
                {"problemId":"019c8e42-7f70-7000-8000-000000000010",
                 "languageId":"cpp","testDataLocation":"/data/problems/p",
                 "testDataDigest":"%s",
                """.formatted("0".repeat(64));
        String suffix = "\"memoryBytes\":268435456,\"referenceSource\":\"int main(){}\"}";
        String authorization = problemService(TestCallTokens.ADMIN);

        mvc.perform(post("/internal/admin/calibrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization)
                        .content(prefix + "\"cpuNs\":0," + suffix))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/admin/calibrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization)
                        .content(prefix + suffix))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/admin/calibrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization)
                        .content(prefix + "\"cpuNs\":1,\"clockNs\":0," + suffix))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/admin/calibrations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization)
                        .content(prefix + "\"cpuNs\":9223372036854775808," + suffix))
                .andExpect(status().isBadRequest());
    }

    @Test
    void trialRouteIsForSubmissionServiceOnly() throws Exception {
        String path = "/internal/submission/trials";
        String body = """
                {"problemId":"019c8e42-7f70-7000-8000-000000000010",
                 "languageId":"cpp","source":"int main(){}",
                 "inputText":"","deadlineEpochMs":%d}
                """.formatted(System.currentTimeMillis() + 45000);
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        // 管理员经 gateway 来也不行；令牌发往别的服务（aud 不是 judging-service）→ 401；调用方不对 → 403。
        mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", gateway(TestCallTokens.ADMIN)))
                .andExpect(status().isForbidden());
        mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(
                                "Authorization",
                                TestCallTokens.authorization(ServiceNames.SUBMISSION, ServiceNames.PROBLEM, null)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header("Authorization", problemService(null)))
                .andExpect(status().isForbidden());
        // 下游无需功能开关；正确凭据进入执行配置检查，错误凭据不能触及执行逻辑。
        org.mockito.Mockito.verifyNoInteractions(executionProfiles);
        when(executionProfiles.resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new com.cherryoj.judgingservice.api.JudgingApiException(
                        org.springframework.http.HttpStatus.UNPROCESSABLE_ENTITY,
                        "RUN_LIMIT_UNSUPPORTED",
                        "测试执行配置不支持自测。"));
        mvc.perform(post(path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body)
                        .header(
                                "Authorization",
                                TestCallTokens.authorization(ServiceNames.SUBMISSION, ServiceNames.JUDGING, null)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.code")
                        .value("RUN_LIMIT_UNSUPPORTED"));
        org.mockito.Mockito.verify(executionProfiles)
                .resolve(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    private static String problemService(CallUser user) {
        return TestCallTokens.authorization(ServiceNames.PROBLEM, ServiceNames.JUDGING, user);
    }

    private static String gateway(CallUser user) {
        return TestCallTokens.authorization(ServiceNames.GATEWAY, ServiceNames.JUDGING, user);
    }
}
