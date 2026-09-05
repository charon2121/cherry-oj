package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;
import com.cherryoj.gatewayservice.auth.DelegatedIdentity;

import reactor.core.publisher.Mono;

class AdminProblemsControllerTests {

	@Test
	void readUsesExactlyOneDelegatedIdentityAndNeverOwnsRecovery() {
		AdminGatewayAccess adminAccess = mock(AdminGatewayAccess.class);
		ProblemServiceClient problemService = mock(ProblemServiceClient.class);
		MockServerWebExchange exchange = exchange();
		String requestId = requestId(exchange);
		DelegatedIdentity identity = identity(requestId);
		when(adminAccess.delegatedIdentity(exchange, requestId)).thenReturn(Mono.just(identity));
		when(problemService.listAdmin(identity, null, null, 1, 20)).thenReturn(Mono.just(page()));

		var response = new AdminProblemsController(adminAccess, problemService)
				.list(null, null, 1, 20, exchange).block();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		verify(adminAccess, times(1)).delegatedIdentity(exchange, requestId);
		verify(problemService, times(1)).listAdmin(identity, null, null, 1, 20);
	}

	@Test
	void resource401Becomes503AndActionIsNotReplayed() {
		AdminGatewayAccess adminAccess = mock(AdminGatewayAccess.class);
		ProblemServiceClient problemService = mock(ProblemServiceClient.class);
		MockServerWebExchange exchange = exchange();
		String requestId = requestId(exchange);
		DelegatedIdentity identity = identity(requestId);
		var request = new AdminProblemsController.CreateProblemRequest(
				"a-plus-b", "A+B", "EASY", "ACM", "cpp");
		when(adminAccess.delegatedIdentity(exchange, requestId)).thenReturn(Mono.just(identity));
		when(problemService.createProblem(identity, request))
				.thenReturn(Mono.error(problemError(HttpStatus.UNAUTHORIZED)));

		assertThatThrownBy(() -> new AdminProblemsController(adminAccess, problemService)
				.create(request, exchange).block())
				.isInstanceOfSatisfying(ApiProblemException.class, error -> {
					assertThat(error.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
					assertThat(error.code()).isEqualTo("SERVICE_UNAVAILABLE");
				});
		verify(problemService, times(1)).createProblem(identity, request);
	}

	private static MockServerWebExchange exchange() {
		return MockServerWebExchange.from(MockServerHttpRequest
				.get("/api/admin/problems?page=1&size=20").build());
	}

	private static String requestId(MockServerWebExchange exchange) {
		return com.cherryoj.gatewayservice.api.ApiRequestContext.requestId(exchange);
	}

	private static DelegatedIdentity identity(String requestId) {
		return new DelegatedIdentity("delegated-token", Instant.now().plusSeconds(300), requestId);
	}

	private static ProblemDtos.AdminProblemPage page() {
		return new ProblemDtos.AdminProblemPage(List.of(), 1, 20, 0, 0);
	}

	private static ProblemServiceClientException problemError(HttpStatus status) {
		return new ProblemServiceClientException(status, "INVALID_ACCESS_TOKEN");
	}
}
