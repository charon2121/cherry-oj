package com.cherryoj.gatewayservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

class ApiProblemHandlerTests {

	private final WebTestClient webTestClient = WebTestClient.bindToController(new FailureController())
			.controllerAdvice(new ApiProblemHandler())
			.webFilter(new RequestIdWebFilter())
			.build();

	@Test
	void mapsKnownDomainProblemWithoutChangingItsStatusOrCode() {
		webTestClient.get()
				.uri("/test/conflict")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.CONFLICT)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.type").isEqualTo("urn:cherry-oj:problem:submission-state-conflict")
				.jsonPath("$.title").isEqualTo("提交状态冲突")
				.jsonPath("$.status").isEqualTo(409)
				.jsonPath("$.code").isEqualTo("SUBMISSION_STATE_CONFLICT")
				.jsonPath("$.detail").isEqualTo("该提交已经进入终态。")
				.jsonPath("$.meta.requestId").exists()
				.jsonPath("$.violations").doesNotExist();
	}

	@Test
	void mapsBeanValidationToUnprocessableProblem() {
		webTestClient.post()
				.uri("/test/input")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{\"title\":\"\"}")
				.exchange()
				.expectStatus().isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(422)
				.jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
				.jsonPath("$.violations[0].path").isEqualTo("title")
				.jsonPath("$.violations[0].code").isEqualTo("NOT_BLANK")
				.jsonPath("$.violations[0].message").isEqualTo("字段不能为空。");
	}

	@Test
	void mapsMalformedJsonToBadRequestProblem() {
		webTestClient.post()
				.uri("/test/input")
				.contentType(MediaType.APPLICATION_JSON)
				.bodyValue("{not-json")
				.exchange()
				.expectStatus().isBadRequest()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(400)
				.jsonPath("$.code").isEqualTo("MALFORMED_REQUEST")
				.jsonPath("$.detail").isEqualTo("请求格式无效。");
	}

	@ParameterizedTest
	@CsvSource({
		"401, UNAUTHENTICATED",
		"403, FORBIDDEN",
		"404, NOT_FOUND",
		"413, PAYLOAD_TOO_LARGE",
		"415, UNSUPPORTED_MEDIA_TYPE",
		"429, RATE_LIMITED",
		"502, BAD_GATEWAY",
		"503, SERVICE_UNAVAILABLE",
		"504, GATEWAY_TIMEOUT"
	})
	void mapsFrameworkHttpFailuresToStableProblemCodes(int status, String code) {
		webTestClient.get()
				.uri("/test/status/{status}", status)
				.exchange()
				.expectStatus().isEqualTo(status)
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(status)
				.jsonPath("$.code").isEqualTo(code)
				.jsonPath("$.meta.requestId").exists();
	}

	@Test
	void redactsUnexpectedFailureAndKeepsHeaderAndBodyRequestIdsEqual() {
		EntityExchangeResult<byte[]> result = webTestClient.get()
				.uri("/test/unexpected")
				.exchange()
				.expectStatus().is5xxServerError()
				.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
				.expectBody()
				.jsonPath("$.status").isEqualTo(500)
				.jsonPath("$.code").isEqualTo("INTERNAL_ERROR")
				.jsonPath("$.detail").isEqualTo("服务暂时不可用，请稍后重试。")
				.returnResult();

		String requestId = result.getResponseHeaders().getFirst(ApiRequestContext.REQUEST_ID_HEADER);
		String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
		assertThat(body)
				.contains("\"requestId\":\"" + requestId + "\"")
				.doesNotContain("database-password")
				.doesNotContain(IllegalStateException.class.getName());
	}

	@RestController
	@RequestMapping("/test")
	private static final class FailureController {

		@GetMapping("/conflict")
		void conflict() {
			throw new ApiProblemException(
					HttpStatus.CONFLICT,
					"SUBMISSION_STATE_CONFLICT",
					"提交状态冲突",
					"该提交已经进入终态。");
		}

		@PostMapping("/input")
		void input(@Valid @RequestBody Input input) {
		}

		@GetMapping("/unexpected")
		void unexpected() {
			throw new IllegalStateException("database-password must never reach the browser");
		}

		@GetMapping("/status/{status}")
		void status(@PathVariable int status) {
			throw new ResponseStatusException(HttpStatus.valueOf(status));
		}
	}

	private record Input(@NotBlank String title) {
	}

}
