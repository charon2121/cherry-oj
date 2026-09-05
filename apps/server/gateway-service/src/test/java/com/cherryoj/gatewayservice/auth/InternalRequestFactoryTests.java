package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

class InternalRequestFactoryTests {

	private static final String REQUEST_ID = "req_0123456789abcdef0123456789abcdef";

	@Test
	void addsIdentityAndRequestIdWhileLeavingTracePropagationToTheManagedClient() {
		AtomicReference<ClientRequest> captured = new AtomicReference<>();
		WebClient client = WebClient.builder().exchangeFunction(request -> {
			captured.set(request);
			return Mono.just(ClientResponse.create(HttpStatus.NO_CONTENT).build());
		}).build();
		DelegatedIdentity identity = new DelegatedIdentity(
				"secret-delegated-token", Instant.now().plusSeconds(300), REQUEST_ID);

		new InternalRequestFactory().authenticated(
				client.get().uri("http://service.test/internal/resource")
						.header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01"),
				identity)
				.exchangeToMono(response -> response.releaseBody()).block();

		assertThat(captured.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
				.isEqualTo("Bearer secret-delegated-token");
		assertThat(captured.get().headers().getFirst("X-Request-Id")).isEqualTo(REQUEST_ID);
		assertThat(captured.get().headers().getFirst("traceparent"))
				.isEqualTo("00-0123456789abcdef0123456789abcdef-0123456789abcdef-01");
		assertThat(identity.toString()).doesNotContain("secret-delegated-token");
	}

	@Test
	void internalClientsCannotHandCraftIdentityHeadersOrRouteRecovery() throws IOException {
		String userClient = Files.readString(Path.of(
				"src/main/java/com/cherryoj/gatewayservice/auth/UserServiceClient.java"));
		String problemClient = Files.readString(Path.of(
				"src/main/java/com/cherryoj/gatewayservice/problem/ProblemServiceClient.java"));
		String adminAccess = Files.readString(Path.of(
				"src/main/java/com/cherryoj/gatewayservice/auth/AdminGatewayAccess.java"));
		String authentication = Files.readString(Path.of(
				"src/main/java/com/cherryoj/gatewayservice/auth/GatewayAuthenticationService.java"));
		String problemController = Files.readString(Path.of(
				"src/main/java/com/cherryoj/gatewayservice/problem/AdminProblemsController.java"));
		String configuration = Files.readString(Path.of("src/main/resources/application.yaml"));

		assertThat(userClient).contains("InternalRequestFactory requests")
				.doesNotContain("HttpHeaders.AUTHORIZATION", ".setBearerAuth(", "readWithRecovery");
		assertThat(problemClient).contains("InternalRequestFactory requests")
				.doesNotContain("HttpHeaders.AUTHORIZATION", ".setBearerAuth(", "readWithRecovery");
		assertThat(adminAccess).doesNotContain("readWithRecovery", "resource_token_rejected");
		assertThat(authentication).doesNotContain("recoverRejectedAccessToken");
		assertThat(problemController).contains("@RequestBody Flux<PartEvent>")
				.doesNotContain("@RequestPart", "DataBufferUtils.join", "transferTo(");
		assertThat(configuration).doesNotContain("max-disk-usage-per-part");
	}
}
