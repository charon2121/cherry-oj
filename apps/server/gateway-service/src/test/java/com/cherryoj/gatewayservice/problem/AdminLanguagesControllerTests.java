package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;
import com.cherryoj.gatewayservice.auth.DelegatedIdentity;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

class AdminLanguagesControllerTests {

    /** problem-service 的 404 LANGUAGE_NOT_FOUND 与 409 原样交给浏览器，不变成 502。 */
    @Test
    void languageErrorsReachTheBrowserWithTheirOwnCodes() {
        AdminGatewayAccess adminAccess = mock(AdminGatewayAccess.class);
        ProblemServiceClient problemService = mock(ProblemServiceClient.class);
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.put("/api/admin/languages/rust").build());
        String requestId = ApiRequestContext.requestId(exchange);
        DelegatedIdentity identity =
                new DelegatedIdentity("delegated-token", Instant.now().plusSeconds(300), requestId);
        var request = new AdminLanguagesController.UpdateLanguageSettingRequest(
                "use std::io;\n", "2026-10-17T17:22:51.534471");
        when(adminAccess.delegatedIdentity(exchange, requestId)).thenReturn(Mono.just(identity));
        var controller = new AdminLanguagesController(adminAccess, problemService);

        for (var upstream : new ProblemServiceClientException[] {
            new ProblemServiceClientException(HttpStatus.NOT_FOUND, "LANGUAGE_NOT_FOUND"),
            new ProblemServiceClientException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT")
        }) {
            when(problemService.updateLanguageSetting(identity, "rust", request))
                    .thenReturn(Mono.error(upstream));
            assertThatThrownBy(
                            () -> controller.update("rust", request, exchange).block())
                    .isInstanceOfSatisfying(ApiProblemException.class, error -> {
                        assertThat(error.status()).isEqualTo(upstream.status());
                        assertThat(error.code()).isEqualTo(upstream.code());
                    });
        }
    }
}
