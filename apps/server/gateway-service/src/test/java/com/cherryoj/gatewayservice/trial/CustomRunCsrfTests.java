package com.cherryoj.gatewayservice.trial;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.reactive.server.WebTestClient;

@org.springframework.test.context.ActiveProfiles("test")
@SpringBootTest
class CustomRunCsrfTests {

	@Autowired
	ApplicationContext context;

	@Test
	void realRouteRejectsPostWithoutCsrfBeforeExecution() {
		WebTestClient.bindToApplicationContext(context)
			.build()
			.post()
			.uri("/api/custom-runs")
			.header("Content-Type", "application/json")
			.bodyValue("{}")
			.exchange()
			.expectStatus()
			.isForbidden()
			.expectBody()
			.jsonPath("$.code")
			.isEqualTo("CSRF_REJECTED");
	}

}
