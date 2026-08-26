package com.cherryoj.gatewayservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

class RequestIdWebFilterTests {

    private final WebTestClient webTestClient = WebTestClient.bindToController(new HeaderController())
            .webFilter(new RequestIdWebFilter())
            .build();

    @Test
    void replacesUntrustedRequestIdAndMakesGeneratedValueAvailableToDownstream() {
        EntityExchangeResult<String> result = webTestClient.get()
                .uri("/request-id")
                .header(ApiRequestContext.REQUEST_ID_HEADER, "attacker-controlled")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult();

        String responseRequestId = result.getResponseHeaders().getFirst(ApiRequestContext.REQUEST_ID_HEADER);
        assertThat(responseRequestId).startsWith("req_").isEqualTo(result.getResponseBody());
    }

    @RestController
    private static final class HeaderController {

        @GetMapping("/request-id")
        String requestId(ServerWebExchange exchange) {
            return exchange.getRequest().getHeaders().getFirst(ApiRequestContext.REQUEST_ID_HEADER);
        }
    }
}
