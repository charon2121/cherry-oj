package com.cherryoj.userservice.api;

import static com.cherryoj.identity.token.ServiceNames.GATEWAY;
import static com.cherryoj.identity.token.ServiceNames.PROBLEM;
import static com.cherryoj.identity.token.ServiceNames.USER;
import static com.cherryoj.identity.token.testing.TestCallTokens.ADMIN;
import static com.cherryoj.identity.token.testing.TestCallTokens.authorization;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.identity.token.CallUser;
import com.cherryoj.identity.token.testing.TestCallTokens;
import com.cherryoj.userservice.application.AuthenticationService;
import com.cherryoj.userservice.application.UserAdministrationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

/** 会话接口只给 gateway；改密码需要用户，用户管理需要管理员（docs/protocols/service-identity.md §4、§5）。 */
@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:mysql://127.0.0.1:1/unused",
            "spring.datasource.password=unused",
            "management.endpoint.health.validate-group-membership=false"
        })
class EndpointPolicyIntegrationTests {
    private static final String SESSION = "s".repeat(43);

    @MockitoBean
    AuthenticationService authentication;

    @MockitoBean
    UserAdministrationService users;

    private final MockMvc mvc;

    @Autowired
    EndpointPolicyIntegrationTests(WebApplicationContext context) {
        this.mvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void sessionEndpointsAreForTheGatewayOnly() throws Exception {
        perform(json("/internal/auth/validate", "{\"sessionId\":\"" + SESSION + "\"}"), GATEWAY, null)
                .andExpect(status().isOk());
        perform(json("/internal/auth/revoke", "{\"sessionId\":\"" + SESSION + "\"}"), GATEWAY, null)
                .andExpect(status().isNoContent());
        perform(json("/internal/auth/authenticate", "{\"username\":\"a\",\"password\":\"b\"}"), PROBLEM, null)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CALLER_FORBIDDEN"));
        perform(json("/internal/auth/validate", "{\"sessionId\":\"short\"}"), GATEWAY, null)
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void passwordChangeActsForTheForwardedUserAndAdministrationNeedsAnAdmin() throws Exception {
        String body = "{\"currentPassword\":\"old-password\",\"newPassword\":\"New-Password-42!\"}";
        perform(json("/internal/users/me/password", body), GATEWAY, null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("USER_REQUIRED"));
        perform(json("/internal/users/me/password", body), GATEWAY, TestCallTokens.USER)
                .andExpect(status().isNoContent());
        verify(authentication).changePassword(TestCallTokens.USER.id(), "old-password", "New-Password-42!");

        perform(get("/internal/admin/users"), GATEWAY, TestCallTokens.USER)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        perform(get("/internal/admin/users"), GATEWAY, ADMIN).andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions perform(
            MockHttpServletRequestBuilder request, String caller, CallUser user) throws Exception {
        return mvc.perform(request.header("Authorization", authorization(caller, USER, user)));
    }

    private static MockHttpServletRequestBuilder json(String path, String body) {
        return post(path).contentType("application/json").content(body);
    }
}
