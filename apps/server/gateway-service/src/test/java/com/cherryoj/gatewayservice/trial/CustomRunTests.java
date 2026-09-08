package com.cherryoj.gatewayservice.trial;

import com.cherryoj.gatewayservice.auth.*;
import com.cherryoj.gatewayservice.api.ApiProblemException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.reactive.function.client.WebClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers
class CustomRunTests {

	@Container
	static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
		.withExposedPorts(6379);

	@Test
	void sequentialRunsHaveNoRateLimitAndBusyRequestsAreNotForwarded() throws Exception {
		var connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
		connection.afterPropertiesSet();
		connection.start();
		var redis = new ReactiveStringRedisTemplate(connection);
		var access = mock(SubmissionGatewayAccess.class);
		var calls = new AtomicInteger();
		var downstreamStatus = new AtomicInteger(200);
		when(access.identity(any(), any(), any())).thenReturn(
				Mono.just(new DelegatedIdentity("delegated", Instant.now().plusSeconds(300), "req_" + "a".repeat(32))));
		var problem = UUID.randomUUID();
		var version = UUID.randomUUID();
		var owner = UUID.randomUUID();
		var request = new CustomRunController.Request(problem, version, "cpp", "int main(){}", "");
		var result = new CustomRunController.Result(problem, version, 1, "cpp", "COMPLETED", 0L, 0L,
				new CustomRunController.Output("", 0, false), new CustomRunController.Output("", 0, false), null,
				new CustomRunController.Limits(1, 1, 1));
		var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		byte[] body = new ObjectMapper().writeValueAsBytes(result);
		server.createContext("/api/custom-runs", exchange -> {
			calls.incrementAndGet();
			assertEquals("Bearer delegated", exchange.getRequestHeaders().getFirst("Authorization"));
			exchange.getRequestBody().readAllBytes();
			exchange.getResponseHeaders().add("Content-Type", "application/json");
			exchange.sendResponseHeaders(downstreamStatus.get(), body.length);
			try (var out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		try {
			var one = new CustomRunController(access, new InternalRequestFactory(), WebClient.builder(), redis,
					"http://127.0.0.1:" + server.getAddress().getPort(), true);
			var two = new CustomRunController(access, new InternalRequestFactory(), WebClient.builder(), redis,
					"http://127.0.0.1:" + server.getAddress().getPort(), true);
			downstreamStatus.set(503);
			var failed = assertThrows(ApiProblemException.class,
					() -> one
						.run(request, owner, MockServerWebExchange.from(MockServerHttpRequest.post("/api/custom-runs")))
						.block());
			assertEquals(503, failed.status().value());
			assertFalse(redis.hasKey("cherry:custom-run:{" + owner + "}:active").block());
			downstreamStatus.set(200);

			for (int i = 0; i < 12; i++)
				assertEquals(200, (i % 2 == 0 ? one : two)
					.run(request, owner, MockServerWebExchange.from(MockServerHttpRequest.post("/api/custom-runs")))
					.block()
					.getStatusCode()
					.value());
			assertEquals(13, calls.get());
			var browser = org.springframework.test.web.reactive.server.WebTestClient.bindToController(one)
				.httpMessageCodecs(new RunCodecConfig()::configureHttpMessageCodecs)
				.webFilter(new RunBodyLimit())
				.build();
			var large = new CustomRunController.Request(problem, version, "cpp", "\n".repeat(250000) + "x", "");
			browser.post()
				.uri("/api/custom-runs")
				.header("X-Expected-User-Id", UUID.randomUUID().toString())
				.contentType(org.springframework.http.MediaType.APPLICATION_JSON)
				.bodyValue(large)
				.exchange()
				.expectStatus()
				.isOk();
			assertEquals(14, calls.get());
			var leaseOwner = UUID.randomUUID();
			redis.opsForValue()
				.set("cherry:custom-run:{" + leaseOwner + "}:active", "other", java.time.Duration.ofSeconds(65))
				.block();
			assertThrows(ApiProblemException.class,
					() -> one
						.run(request, leaseOwner,
								MockServerWebExchange.from(MockServerHttpRequest.post("/api/custom-runs")))
						.block());
			assertEquals(14, calls.get());
		}
		finally {
			server.stop(0);
			connection.destroy();
		}
	}

	@Test
	void terminalErrorsAndCancellationReleaseOnlyTheirOwnLease() throws Exception {
		var connection = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
		connection.afterPropertiesSet();
		connection.start();
		try {
			var redis = new ReactiveStringRedisTemplate(connection);
			var controller = new CustomRunController(mock(SubmissionGatewayAccess.class), new InternalRequestFactory(),
					WebClient.builder(), redis, "http://unused.invalid", true);
			var keys = List.of("cherry:custom-run:{" + UUID.randomUUID() + "}:active");
			for (int status : List.of(503, 504, 400)) {
				assertThrows(ApiProblemException.class,
						() -> controller
							.withLease(keys, "failed",
									() -> Mono.error(CustomRunController
										.error(org.springframework.http.HttpStatus.valueOf(status), "TEST", "test")))
							.block());
				assertEquals("ok", controller.withLease(keys, "retry", () -> Mono.just("ok")).block());
				assertFalse(redis.hasKey(keys.get(0)).block());
			}
			var entered = new java.util.concurrent.CountDownLatch(1);
			var pending = controller.withLease(keys, "cancel", () -> {
				entered.countDown();
				return Mono.never();
			}).subscribe();
			try {
				assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
				var busy = assertThrows(ApiProblemException.class,
						() -> controller.withLease(keys, "other", () -> Mono.just("bad")).block());
				assertEquals(429, busy.status().value());
				assertEquals("cancel", redis.opsForValue().get(keys.get(0)).block());
				pending.dispose();
				org.awaitility.Awaitility.await()
					.atMost(java.time.Duration.ofSeconds(5))
					.until(() -> !Boolean.TRUE.equals(redis.hasKey(keys.get(0)).block()));
				assertEquals("ok", controller.withLease(keys, "retry", () -> Mono.just("ok")).block());
				assertEquals("ok",
						controller
							.withLease(keys, "old",
									() -> redis.opsForValue()
										.set(keys.get(0), "new", java.time.Duration.ofSeconds(65))
										.thenReturn("ok"))
							.block());
				assertEquals("new", redis.opsForValue().get(keys.get(0)).block());
			}
			finally {
				pending.dispose();
			}
		}
		finally {
			connection.destroy();
		}
	}

	@Test
	void cancellationBeforeRedisReplyCleansUpAfterAcquisition() {
		var redis = mock(ReactiveStringRedisTemplate.class);
		var acquired = reactor.core.publisher.Sinks.<Long>one();
		when(redis.<Long>execute(any(), anyList(), anyList())).thenReturn(acquired.asMono().flux(),
				reactor.core.publisher.Flux.just(1L));
		var controller = new CustomRunController(mock(SubmissionGatewayAccess.class), new InternalRequestFactory(),
				WebClient.builder(), redis, "http://unused.invalid", true);
		var action = new AtomicInteger();
		var subscription = controller.withLease(List.of("lease"), "owner", () -> {
			action.incrementAndGet();
			return Mono.just("bad");
		}).subscribe();
		subscription.dispose();
		acquired.tryEmitValue(0L);
		verify(redis, times(2)).<Long>execute(any(), anyList(), anyList());
		assertEquals(0, action.get());
	}

	@Test
	void rejectsResultsForDifferentVersions() {
		var id = UUID.randomUUID();
		var r = new CustomRunController.Request(id, id, "cpp", "int main(){}", "");
		var result = new CustomRunController.Result(id, UUID.randomUUID(), 1, "cpp", "COMPLETED", 0L, 0L,
				new CustomRunController.Output("", 0, false), new CustomRunController.Output("", 0, false), null,
				new CustomRunController.Limits(1, 1, 1));
		assertThrows(ApiProblemException.class, () -> CustomRunController.validate(result, r));
	}

	@Test
	void missingOutputFactsAreRejectedInsteadOfBecomingZero() {
		var mapper = new ObjectMapper();
		assertThrows(RuntimeException.class, () -> mapper.readValue("{}", CustomRunController.Limits.class));
		assertThrows(RuntimeException.class,
				() -> mapper.readValue("{\"text\":\"\",\"truncated\":false}", CustomRunController.Output.class));
	}

	@Test
	void disabledGatewayRejectsBeforeAdmissionOrForwarding() {
		var access = mock(SubmissionGatewayAccess.class);
		var redis = mock(ReactiveStringRedisTemplate.class);
		var downstreamCalls = new AtomicInteger();
		var client = WebClient.builder().exchangeFunction(request -> {
			downstreamCalls.incrementAndGet();
			return Mono.error(new AssertionError("disabled gateway must not forward"));
		});
		var controller = new CustomRunController(access, new InternalRequestFactory(), client, redis,
				"http://submission.invalid", false);
		var id = UUID.randomUUID();
		var request = new CustomRunController.Request(id, id, "cpp", "int main(){}", "");
		var error = assertThrows(ApiProblemException.class,
				() -> controller
					.run(request, id, MockServerWebExchange.from(MockServerHttpRequest.post("/api/custom-runs")))
					.block());
		assertEquals(503, error.status().value());
		assertEquals("CUSTOM_RUN_DISABLED", error.code());
		verifyNoInteractions(access, redis);
		assertEquals(0, downstreamCalls.get());
	}

}
