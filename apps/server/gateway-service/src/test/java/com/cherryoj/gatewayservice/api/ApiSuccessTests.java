package com.cherryoj.gatewayservice.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;

class ApiSuccessTests {

	private final WebTestClient webTestClient = WebTestClient.bindToController(new PaginationController())
			.webFilter(new RequestIdWebFilter())
			.build();

	@Test
	void serializesCursorPaginationAsThePublicDiscriminatedShape() {
		webTestClient.get()
				.uri("/test/cursor")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.data.items.length()").isEqualTo(0)
				.jsonPath("$.meta.pagination.kind").isEqualTo("cursor")
				.jsonPath("$.meta.pagination.nextCursor").isEmpty()
				.jsonPath("$.meta.pagination.hasMore").isEqualTo(false);
	}

	@Test
	void serializesPagePaginationAsThePublicDiscriminatedShape() {
		webTestClient.get()
				.uri("/test/page")
				.exchange()
				.expectStatus().isOk()
				.expectBody()
				.jsonPath("$.meta.pagination.kind").isEqualTo("page")
				.jsonPath("$.meta.pagination.page").isEqualTo(1)
				.jsonPath("$.meta.pagination.size").isEqualTo(20)
				.jsonPath("$.meta.pagination.totalElements").isEqualTo(0)
				.jsonPath("$.meta.pagination.totalPages").isEqualTo(0);
	}

	@RestController
	@RequestMapping("/test")
	private static final class PaginationController {

		@GetMapping("/cursor")
		ApiSuccess<Map<String, List<String>>> cursor(ServerWebExchange exchange) {
			return ApiSuccess.of(
					Map.of("items", List.of()),
					ApiRequestContext.requestId(exchange),
					new CursorPagination(null, false));
		}

		@GetMapping("/page")
		ApiSuccess<Map<String, List<String>>> page(ServerWebExchange exchange) {
			return ApiSuccess.of(
					Map.of("items", List.of()),
					ApiRequestContext.requestId(exchange),
					new PagePagination(1, 20, 0, 0));
		}
	}

}
