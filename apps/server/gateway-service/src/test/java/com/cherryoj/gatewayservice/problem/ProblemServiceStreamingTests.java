package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import com.cherryoj.gatewayservice.auth.DelegatedIdentity;
import com.cherryoj.gatewayservice.auth.InternalRequestFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

class ProblemServiceStreamingTests {

	private static final DefaultDataBufferFactory BUFFERS = new DefaultDataBufferFactory();

	@Test
	void multipartUploadConsumesFilePublisherWithoutJoiningItInGatewayCode() throws IOException {
		AtomicInteger subscriptions = new AtomicInteger();
		AtomicReference<String> outgoingBody = new AtomicReference<>();
		AtomicReference<ClientRequest> outgoingRequest = new AtomicReference<>();
		ExchangeStrategies strategies = ExchangeStrategies.withDefaults();
		WebClient.Builder webClient = WebClient.builder().exchangeStrategies(strategies)
				.exchangeFunction(request -> {
					outgoingRequest.set(request);
					MockClientHttpRequest sink = new MockClientHttpRequest(HttpMethod.PUT, request.url());
					return request.writeTo(sink, strategies).then(Mono.defer(sink::getBodyAsString))
							.doOnNext(outgoingBody::set)
							.thenReturn(ClientResponse.create(HttpStatus.OK)
									.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
									.body(testDataJson()).build());
				});
		ProblemServiceClient client = new ProblemServiceClient(
				webClient, properties(), new InternalRequestFactory());
		byte[] zip = zipBytes();
		Flux<DataBuffer> content = Flux.defer(() -> {
			subscriptions.incrementAndGet();
			return Flux.just(
					BUFFERS.wrap(java.util.Arrays.copyOfRange(zip, 0, zip.length / 2)),
					BUFFERS.wrap(java.util.Arrays.copyOfRange(zip, zip.length / 2, zip.length)));
		});

		ProblemDtos.TestData result = client.replaceTestData(identity("delegated-jwt-secret"),
				"019c8e42-7f70-7000-8000-000000000101", content).block();

		assertThat(result).isNotNull();
		assertThat(subscriptions).hasValue(1);
		assertThat(outgoingRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
				.isEqualTo("Bearer delegated-jwt-secret");
		assertThat(outgoingBody.get()).contains("1.in", "1.out", "name=\"file\"")
				.doesNotContain("delegated-jwt-secret");
	}

	@Test
	void upstreamClientErrorKeepsOnlyBoundedControlFreeDetail() {
		WebClient.Builder safeUpstream = WebClient.builder().exchangeFunction(request -> Mono.just(
				ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
						.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
						.body("{\"code\":\"INVALID_TEST_DATA_ARCHIVE\","
								+ "\"detail\":\"每个测试点都必须同时包含同名的 .in 和 .out 文件。\"}")
						.build()));
		ProblemServiceClient safe = new ProblemServiceClient(
				safeUpstream, properties(), new InternalRequestFactory());

		assertThatThrownBy(() -> safe.getTestData(
				identity("delegated-jwt"), "019c8e42-7f70-7000-8000-000000000101").block())
				.isInstanceOfSatisfying(ProblemServiceClientException.class,
						error -> assertThat(error.detail())
								.isEqualTo("每个测试点都必须同时包含同名的 .in 和 .out 文件。"));

		WebClient.Builder unsafeUpstream = WebClient.builder().exchangeFunction(request -> Mono.just(
				ClientResponse.create(HttpStatus.UNPROCESSABLE_ENTITY)
						.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PROBLEM_JSON_VALUE)
						.body("{\"code\":\"INVALID_TEST_DATA_ARCHIVE\",\"detail\":\"line one\\nline two\"}")
						.build()));
		ProblemServiceClient unsafe = new ProblemServiceClient(
				unsafeUpstream, properties(), new InternalRequestFactory());

		assertThatThrownBy(() -> unsafe.getTestData(
				identity("delegated-jwt"), "019c8e42-7f70-7000-8000-000000000101").block())
				.isInstanceOfSatisfying(ProblemServiceClientException.class,
						error -> assertThat(error.detail()).isNull());
	}

	private static ProblemServiceProperties properties() {
		return new ProblemServiceProperties(URI.create("http://problem-service.test"),
				Duration.ofSeconds(2), Duration.ofSeconds(3), 1_048_576);
	}

	private static DelegatedIdentity identity(String token) {
		return new DelegatedIdentity(token, java.time.Instant.now().plusSeconds(300),
				"req_0123456789abcdef0123456789abcdef");
	}

	private static byte[] zipBytes() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			zip.putNextEntry(new ZipEntry("1.in"));
			zip.write("1 2\n".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
			zip.putNextEntry(new ZipEntry("1.out"));
			zip.write("3\n".getBytes(StandardCharsets.UTF_8));
			zip.closeEntry();
		}
		return bytes.toByteArray();
	}

	private static String testDataJson() {
		return """
				{"digest":"6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4",
				 "caseCount":1,"totalBytes":6,"updatedAt":"2026-08-30T00:00:00",
				 "manifest":{"caseCount":1,"totalBytes":6,"files":[
				   {"name":"1.in","sizeBytes":4,"sha256":"%s"},{"name":"1.out","sizeBytes":2,"sha256":"%s"}]}}
				""".formatted("a".repeat(64), "b".repeat(64));
	}
}
