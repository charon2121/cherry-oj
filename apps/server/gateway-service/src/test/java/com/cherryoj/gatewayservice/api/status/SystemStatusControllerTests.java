package com.cherryoj.gatewayservice.api.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.cherryoj.gatewayservice.api.ApiRequestContext;
import com.cherryoj.gatewayservice.api.RequestIdWebFilter;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

class SystemStatusControllerTests {

	private final WebTestClient webTestClient =
			WebTestClient.bindToController(new SystemStatusController())
					.webFilter(new RequestIdWebFilter())
					.build();

	@Test
	void returnsBrowserFacingGatewayStatus() {
		EntityExchangeResult<byte[]> result = webTestClient.get()
				.uri("/api/status")
				.exchange()
				.expectStatus().isOk()
				.expectHeader().contentType(MediaType.APPLICATION_JSON)
				.expectHeader().valueMatches(ApiRequestContext.REQUEST_ID_HEADER, "^req_[A-Za-z0-9_-]{16,64}$")
				.expectBody()
				.jsonPath("$.data.service").isEqualTo("gateway-service")
				.jsonPath("$.data.status").isEqualTo("ready")
				.jsonPath("$.meta.length()").isEqualTo(1)
				.jsonPath("$.length()").isEqualTo(2)
				.returnResult();

		String requestId = result.getResponseHeaders().getFirst(ApiRequestContext.REQUEST_ID_HEADER);
		String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
		assertThat(body).contains("\"requestId\":\"" + requestId + "\"");
	}

	@Test
	void replacesUntrustedInboundRequestId() {
		EntityExchangeResult<byte[]> result = webTestClient.get()
				.uri("/api/status")
				.header(ApiRequestContext.REQUEST_ID_HEADER, "attacker-controlled")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.returnResult();

		String requestId = result.getResponseHeaders().getFirst(ApiRequestContext.REQUEST_ID_HEADER);
		assertThat(requestId).startsWith("req_").isNotEqualTo("attacker-controlled");
	}

}
