package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
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
import reactor.core.publisher.BaseSubscriber;
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
					MockClientHttpRequest sink = new MockClientHttpRequest(HttpMethod.POST, request.url());
					return request.writeTo(sink, strategies).then(Mono.defer(sink::getBodyAsString))
							.doOnNext(outgoingBody::set)
							.thenReturn(ClientResponse.create(HttpStatus.CREATED)
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

		ProblemDtos.TestDataVersion result = client.uploadTestData(identity("delegated-jwt-secret"),
				"019c8e42-7f70-7000-8000-000000000101", content).block();

		assertThat(result).isNotNull();
		assertThat(subscriptions).hasValue(1);
		assertThat(outgoingRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
				.isEqualTo("Bearer delegated-jwt-secret");
		assertThat(outgoingBody.get()).contains("1.in", "1.out", "name=\"file\"")
				.doesNotContain("delegated-jwt-secret");
	}

	@Test
	void downloadBodyPreservesDemandAndCancellation() {
		AtomicBoolean cancelled = new AtomicBoolean();
		Flux<DataBuffer> upstream = Flux.just(buffer("zip-first"), buffer("zip-second"))
				.doOnCancel(() -> cancelled.set(true));
		WebClient.Builder webClient = WebClient.builder().exchangeFunction(request -> Mono.just(
				ClientResponse.create(HttpStatus.OK)
						.header(HttpHeaders.CONTENT_TYPE, "application/zip")
						.header(HttpHeaders.CONTENT_LENGTH, "19")
						.body(upstream).build()));
		ProblemServiceClient.Download download = new ProblemServiceClient(
				webClient, properties(), new InternalRequestFactory())
				.downloadTestData(identity("delegated-jwt"),
						"019c8e42-7f70-7000-8000-000000000101",
						"019c8e42-7f70-7000-8000-000000000103")
				.block();

		assertThat(download).isNotNull();
		assertThat(download.contentLength()).isEqualTo(19);
		AtomicReference<String> first = new AtomicReference<>();
		download.body().map(ProblemServiceStreamingTests::readAndRelease)
				.subscribe(new BaseSubscriber<>() {
					@Override
					protected void hookOnSubscribe(Subscription subscription) {
						request(1);
					}

					@Override
					protected void hookOnNext(String value) {
						first.set(value);
						cancel();
					}
				});
		assertThat(first).hasValue("zip-first");
		assertThat(cancelled).isTrue();
	}

	private static ProblemServiceProperties properties() {
		return new ProblemServiceProperties(URI.create("http://problem-service.test"),
				Duration.ofSeconds(2), Duration.ofSeconds(3), 1_048_576);
	}

	private static DelegatedIdentity identity(String token) {
		return new DelegatedIdentity(token, java.time.Instant.now().plusSeconds(300),
				"req_0123456789abcdef0123456789abcdef");
	}

	private static DataBuffer buffer(String value) {
		return BUFFERS.wrap(value.getBytes(StandardCharsets.UTF_8));
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

	private static String readAndRelease(DataBuffer buffer) {
		byte[] bytes = new byte[buffer.readableByteCount()];
		buffer.read(bytes);
		DataBufferUtils.release(buffer);
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private static String testDataJson() {
		return """
				{"id":"019c8e42-7f70-7000-8000-000000000103",
				 "problemId":"019c8e42-7f70-7000-8000-000000000101","status":"READY",
				 "sourceType":"MANUAL_UPLOAD","contentSha256":null,"caseCount":null,
				 "totalBytes":null,"manifest":null,"createdAt":"2026-08-30T00:00:00",
				 "readyAt":null,"errorMessage":null}
				""";
	}
}
