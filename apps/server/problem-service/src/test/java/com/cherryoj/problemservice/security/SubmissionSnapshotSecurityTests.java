package com.cherryoj.problemservice.security;

import com.cherryoj.problemservice.api.SubmissionSnapshotController;
import com.cherryoj.problemservice.application.SubmissionSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class SubmissionSnapshotSecurityTests {
    @Configuration @EnableWebSecurity @EnableWebMvc @Import(SubmissionProblemSnapshotSecurity.class)
    static class Config {
        @Bean ServiceCallVerifier serviceCallVerifier() {
            return ServiceTokenFixture.verifier("problem-service");
        }

        @Bean SubmissionSnapshotController controller() {
            var service = mock(SubmissionSnapshotService.class);
            when(service.resolve(anyString(), anyString())).thenReturn(new SubmissionSnapshotService.Snapshot(
                    "p", "title", "cpp", "ACM"));
            return new SubmissionSnapshotController(service);
        }
    }

    @Test void onlySubmissionServiceCanReadTheSnapshot() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class); context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            String body = "{\"problemId\":\"0198cafe-0000-7000-8000-000000000002\",\"languageId\":\"cpp\"}";
            for (String token : new String[]{"eyJ.admin.jwt", "local-judge-control-token",
                    ServiceTokenFixture.token("submission-service", "judging-service")}) {
                mvc.perform(post("/internal/submission/problem-snapshot").header("Authorization", "Bearer " + token)
                        .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/internal/submission/problem-snapshot").header("Authorization", "Bearer "
                            + ServiceTokenFixture.token("judging-service", "problem-service"))
                    .contentType("application/json").content(body)).andExpect(status().isForbidden());
            mvc.perform(post("/internal/submission/problem-snapshot").header("Authorization", "Bearer "
                            + ServiceTokenFixture.token("submission-service", "problem-service"))
                    .contentType("application/json").content(body))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(jsonPath("problemTitle").value("title"))
                    // 契约里没有版本、数据地址或存储位置
                    .andExpect(jsonPath("problemVersionId").doesNotExist())
                    .andExpect(jsonPath("testDataLocation").doesNotExist())
                    .andExpect(jsonPath("storageRef").doesNotExist());
        }
    }
}
