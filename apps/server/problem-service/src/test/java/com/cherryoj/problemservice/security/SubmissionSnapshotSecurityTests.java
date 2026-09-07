package com.cherryoj.problemservice.security;

import com.cherryoj.problemservice.api.SubmissionSnapshotController;
import com.cherryoj.problemservice.application.SubmissionSnapshotService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
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
        @Bean SubmissionSnapshotController controller() {
            var service = mock(SubmissionSnapshotService.class);
            when(service.resolve(anyString(), anyString())).thenReturn(new SubmissionSnapshotService.Snapshot(
                    "p", "v", 1, "title", "d", "a".repeat(64), "cpp", "ACM", 2));
            return new SubmissionSnapshotController(service);
        }
    }
    @Test void onlyTheConfiguredCallChainCanReadTheSnapshot() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            String token = "a".repeat(48);
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "cherry.service-calls.submission-problem-tokens=" + token);
            context.register(Config.class); context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            for (String authorization : new String[]{"Bearer eyJ.admin.jwt", "Bearer local-judge-control-token", "Bearer " + "b".repeat(48)}) {
                mvc.perform(post("/internal/submission/problem-snapshot").header("Authorization", authorization)
                        .contentType("application/json").content("{}")) .andExpect(status().isUnauthorized());
            }
            mvc.perform(post("/internal/submission/problem-snapshot").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content("{\"problemId\":\"0198cafe-0000-7000-8000-000000000002\",\"languageId\":\"cpp\"}"))
                    .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("totalCount").value(2)).andExpect(jsonPath("storageRef").doesNotExist());
        }
    }
}
