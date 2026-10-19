package com.cherryoj.problemservice.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.cherryoj.problemservice.api.JudgingTestDataController;
import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.application.TestDataService;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class JudgingTestDataSecurityTests {
    private static final String PATH = "/internal/judging/problems/0198cafe-0000-7000-8000-000000000002/test-data";

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import(JudgingTestDataSecurity.class)
    static class Config {
        @Bean
        ServiceCallVerifier serviceCallVerifier() {
            return ServiceTokenFixture.verifier("problem-service");
        }

        @Bean
        JudgingTestDataController controller() {
            var service = mock(TestDataService.class);
            when(service.forJudging(anyString()))
                    .thenReturn(new TestDataDtos.ProblemTestData("/srv/problem/p", "a".repeat(64), 2, 16));
            return new JudgingTestDataController(service);
        }
    }

    @Test
    void onlyJudgingServiceTokensAddressedToThisServiceCanReadTheTestDataAddress() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();

            // 令牌无效：匿名、伪造、发往别的服务、缺 token_use、已过期 → 401
            mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
            for (String token : new String[] {
                "eyJ.admin.jwt",
                ServiceTokenFixture.token("judging-service", "submission-service"),
                ServiceTokenFixture.withoutServiceUse("judging-service", "problem-service"),
                ServiceTokenFixture.expired("judging-service", "problem-service")
            }) {
                mvc.perform(get(PATH).header("Authorization", "Bearer " + token))
                        .andExpect(status().isUnauthorized())
                        .andExpect(jsonPath("code").value("SERVICE_UNAUTHORIZED"));
            }
            // 令牌有效但调用方不在名单：submission-service 不能读测试数据地址 → 403
            mvc.perform(get(PATH)
                            .header(
                                    "Authorization",
                                    "Bearer " + ServiceTokenFixture.token("submission-service", "problem-service")))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("code").value("SERVICE_FORBIDDEN"));

            mvc.perform(get(PATH)
                            .header(
                                    "Authorization",
                                    "Bearer " + ServiceTokenFixture.token("judging-service", "problem-service")))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")))
                    .andExpect(jsonPath("location").value("/srv/problem/p"))
                    .andExpect(jsonPath("testcaseCount").value(2));
        }
    }
}
