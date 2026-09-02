package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.cherryoj.gatewayservice.auth.AdminGatewayAccess;

import reactor.core.publisher.Mono;

class AdminProblemsControllerTests {

	@Test
	void listUsesRecoverableReadAndClassifiesOnlyProblemServiceUnauthorized() {
		AdminGatewayAccess adminAccess = mock(AdminGatewayAccess.class);
		ProblemServiceClient problemService = mock(ProblemServiceClient.class);
		MockServerWebExchange exchange = exchange();
		String requestId = requestId(exchange);
		when(problemService.listAdmin("delegated-token", null, null, 1, 20, requestId))
				.thenReturn(Mono.just(page()));
		when(adminAccess.readWithRecovery(
				eq(exchange), eq(requestId), any(), any(), any()))
				.thenAnswer(invocation -> {
					Function<String, Mono<?>> action = invocation.getArgument(2);
					return action.apply("delegated-token");
				});

		var response = new AdminProblemsController(adminAccess, problemService)
				.list(null, null, 1, 20, exchange).block();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Predicate<Throwable>> rejected =
				ArgumentCaptor.forClass(Predicate.class);
		@SuppressWarnings("unchecked")
		ArgumentCaptor<Function<Throwable, ApiProblemException>> mapper =
				ArgumentCaptor.forClass(Function.class);
		verify(adminAccess).readWithRecovery(
				eq(exchange), eq(requestId), any(), rejected.capture(), mapper.capture());
		assertThat(rejected.getValue().test(problemError(HttpStatus.UNAUTHORIZED))).isTrue();
		assertThat(rejected.getValue().test(problemError(HttpStatus.FORBIDDEN))).isFalse();
		assertThat(mapper.getValue().apply(problemError(HttpStatus.FORBIDDEN)).status())
				.isEqualTo(HttpStatus.BAD_GATEWAY);
	}

	@Test
	void createUsesSingleAccessTokenAndDoesNotEnterReadRecovery() {
		AdminGatewayAccess adminAccess = mock(AdminGatewayAccess.class);
		ProblemServiceClient problemService = mock(ProblemServiceClient.class);
		MockServerWebExchange exchange = exchange();
		String requestId = requestId(exchange);
		var request = new AdminProblemsController.CreateProblemRequest(
				"a-plus-b", "A+B", "EASY", "ACM", "cpp");
		when(adminAccess.accessToken(exchange, requestId)).thenReturn(Mono.just("delegated-token"));
		when(problemService.createProblem("delegated-token", request, requestId))
				.thenReturn(Mono.error(problemError(HttpStatus.UNAUTHORIZED)));

		assertThatThrownBy(() -> new AdminProblemsController(adminAccess, problemService)
				.create(request, exchange).block())
				.isInstanceOfSatisfying(ApiProblemException.class,
						error -> assertThat(error.status()).isEqualTo(HttpStatus.BAD_GATEWAY));
		verify(adminAccess, never()).readWithRecovery(
				any(), anyString(), any(), any(), any());
	}

	private static MockServerWebExchange exchange() {
		return MockServerWebExchange.from(MockServerHttpRequest
				.get("/api/admin/problems?page=1&size=20").build());
	}

	private static String requestId(MockServerWebExchange exchange) {
		return com.cherryoj.gatewayservice.api.ApiRequestContext.requestId(exchange);
	}

	private static ProblemDtos.AdminProblemPage page() {
		return new ProblemDtos.AdminProblemPage(List.of(), 1, 20, 0, 0);
	}

	private static ProblemServiceClientException problemError(HttpStatus status) {
		return new ProblemServiceClientException(status, "INVALID_ACCESS_TOKEN");
	}
}
