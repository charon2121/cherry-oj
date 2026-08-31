package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.gatewayservice.api.ApiProblemHandler;
import com.cherryoj.gatewayservice.api.RequestIdWebFilter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

class ProblemGatewayHttpTests {

	private WebTestClient browser;
	private final AtomicInteger status = new AtomicInteger(200);
	private final AtomicReference<String> body = new AtomicReference<>();
	private final AtomicReference<ClientRequest> upstreamRequest = new AtomicReference<>();

	@BeforeEach
	void start() {
		body.set(publicListJson());
		ProblemServiceProperties properties = new ProblemServiceProperties(
				URI.create("http://problem-service.test"),
				Duration.ofMillis(300), Duration.ofSeconds(2), 1_048_576);
		WebClient.Builder upstream = WebClient.builder().exchangeFunction(request -> {
			upstreamRequest.set(request);
			return Mono.just(ClientResponse.create(HttpStatusCode.valueOf(status.get()))
					.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
					.body(body.get()).build());
		});
		ProblemServiceClient client = new ProblemServiceClient(upstream, properties);
		browser = WebTestClient.bindToController(new ProblemsController(client))
				.controllerAdvice(new ApiProblemHandler())
				.webFilter(new RequestIdWebFilter())
				.build();
	}

	@Test
	void anonymousListForwardsOpaqueCursorAndWhitelistsResponse() {
		EntityExchangeResult<byte[]> result = browser.get().uri(builder -> builder
				.path("/api/problems")
				.queryParam("tag", "graph").queryParam("tag", "shortest-path")
				.queryParam("cursor", "opaque.cursor-token").queryParam("size", 7).build())
				.exchange().expectStatus().isOk()
				.expectHeader().valueMatches("X-Request-Id", "^req_[0-9a-f]{32}$")
				.expectBody().jsonPath("$.data.items[0].slug").isEqualTo("a-plus-b")
				.jsonPath("$.meta.pagination.kind").isEqualTo("cursor")
				.jsonPath("$.meta.pagination.nextCursor").isEqualTo("opaque.next-token")
				.jsonPath("$.secretCanary").doesNotExist()
				.jsonPath("$.data.items[0].secretCanary").doesNotExist()
				.returnResult();

		String requestId = result.getResponseHeaders().getFirst("X-Request-Id");
		assertThat(new String(result.getResponseBody(), StandardCharsets.UTF_8)).contains(requestId);
		assertThat(upstreamRequest.get().headers().getFirst("X-Request-Id")).isEqualTo(requestId);
		assertThat(upstreamRequest.get().url().getRawQuery()).contains(
				"tag=graph", "tag=shortest-path", "cursor=opaque.cursor-token", "size=7");
	}

	@Test
	void onlyProblemNotFoundIsExposedAsPublic404() {
		status.set(404);
		body.set("{\"code\":\"TEST_DATA_NOT_FOUND\",\"message\":\"private path\"}");
		browser.get().uri("/api/problems/missing").exchange()
				.expectStatus().isEqualTo(502)
				.expectBody().jsonPath("$.code").isEqualTo("BAD_GATEWAY")
				.jsonPath("$.detail").value(value ->
						assertThat(value.toString()).doesNotContain("private path"));

		body.set("{\"code\":\"PROBLEM_NOT_FOUND\",\"message\":\"database canary\"}");
		browser.get().uri("/api/problems/missing").exchange()
				.expectStatus().isNotFound()
				.expectBody().jsonPath("$.code").isEqualTo("PROBLEM_NOT_FOUND")
				.jsonPath("$.detail").value(value ->
						assertThat(value.toString()).doesNotContain("database canary"));
	}

	@Test
	void malformedSuccessResponseIsBadGatewayRatherThanEmptyData() {
		body.set("{not-json");
		browser.get().uri("/api/problems").exchange()
				.expectStatus().isEqualTo(502)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody().jsonPath("$.code").isEqualTo("BAD_GATEWAY");
	}

	@Test
	void slowProblemServiceIsGatewayTimeoutRatherThanNotFound() {
		ProblemServiceProperties properties = new ProblemServiceProperties(
				URI.create("http://problem-service.test"),
				Duration.ofMillis(20), Duration.ofSeconds(1), 1_048_576);
		ProblemServiceClient slow = new ProblemServiceClient(
				WebClient.builder().exchangeFunction(request -> Mono.never()), properties);
		WebTestClient timeoutBrowser = WebTestClient.bindToController(new ProblemsController(slow))
				.controllerAdvice(new ApiProblemHandler()).webFilter(new RequestIdWebFilter()).build();

		timeoutBrowser.get().uri("/api/problems").exchange()
				.expectStatus().isEqualTo(504)
				.expectBody().jsonPath("$.code").isEqualTo("GATEWAY_TIMEOUT");
	}

	private static String publicListJson() {
		return """
				{
				  "items":[{"problemId":"019c8e42-7f70-7000-8000-000000000101",
				    "slug":"a-plus-b","currentVersionId":"019c8e42-7f70-7000-8000-000000000102",
				    "versionNo":1,"title":"A+B","difficulty":"EASY","tags":["intro"],
				    "codeMode":"ACM","allowedLanguages":[{"id":"cpp","displayName":"C++"}],
				    "secretCanary":"must-not-leak"}],
				  "nextCursor":"opaque.next-token","hasMore":true,"secretCanary":"must-not-leak"
				}
				""";
	}
}
