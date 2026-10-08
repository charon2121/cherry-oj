package com.cherryoj.problemservice.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.problemservice.api.JudgingTestDataController;
import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.application.TestDataService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.support.TestPropertySourceUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class JudgingTestDataSecurityTests {
    private static final String PATH = "/internal/judging/problems/0198cafe-0000-7000-8000-000000000002/test-data";

    @Configuration @EnableWebSecurity @EnableWebMvc @Import(JudgingTestDataSecurity.class)
    static class Config {
        @Bean JudgingTestDataController controller() {
            var service = mock(TestDataService.class);
            when(service.forJudging(anyString())).thenReturn(
                    new TestDataDtos.ProblemTestData("/srv/problem/p", "a".repeat(64), 2, 16));
            return new JudgingTestDataController(service);
        }
    }

    @Test
    void onlyTheJudgingServiceTokenCanReadTheTestDataAddress() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            String token = "j".repeat(48);
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(context,
                    "cherry.service-calls.judging-problem-tokens=" + token);
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

            // 管理员的 JWT、提交服务的令牌、别的随机令牌和匿名请求都不行
            for (String authorization : new String[] {"Bearer eyJ.admin.jwt", "Bearer " + "s".repeat(48), "Bearer " + "b".repeat(48)}) {
                mvc.perform(get(PATH).header("Authorization", authorization)).andExpect(status().isUnauthorized());
            }
            mvc.perform(get(PATH)).andExpect(status().isUnauthorized());

            mvc.perform(get(PATH).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("location").value("/srv/problem/p"))
                    .andExpect(jsonPath("testcaseCount").value(2));
        }
    }

    @Test
    void theEndpointIsClosedWhenNoTokenIsConfigured() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
            mvc.perform(get(PATH).header("Authorization", "Bearer " + "j".repeat(48)))
                    .andExpect(status().isUnauthorized());
        }
    }
}
