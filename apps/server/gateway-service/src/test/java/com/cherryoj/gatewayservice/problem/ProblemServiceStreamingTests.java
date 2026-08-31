package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Subscription;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Flux;
import reactor.core.publisher.BaseSubscriber;
import reactor.core.publisher.Mono;

class ProblemServiceStreamingTests {

	private static final DefaultDataBufferFactory BUFFERS = new DefaultDataBufferFactory();

	@Test
	void multipartUploadConsumesFilePublisherWithoutJoiningItInGatewayCode() {
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
		ProblemServiceClient client = new ProblemServiceClient(webClient, properties());
		FilePart file = filePart(Flux.defer(() -> {
			subscriptions.incrementAndGet();
			return Flux.just(buffer("zip-first-"), buffer("zip-second"));
		}));

		ProblemDtos.TestDataVersion result = client.uploadTestData(
				"delegated-jwt-secret", "019c8e42-7f70-7000-8000-000000000101",
				file, "req_0123456789abcdef0123456789abcdef").block();

		assertThat(result).isNotNull();
		assertThat(subscriptions).hasValue(1);
		assertThat(outgoingRequest.get().headers().getFirst(HttpHeaders.AUTHORIZATION))
				.isEqualTo("Bearer delegated-jwt-secret");
		assertThat(outgoingBody.get()).contains("zip-first-", "zip-second", "name=\"file\"")
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
		ProblemServiceClient.Download download = new ProblemServiceClient(webClient, properties())
				.downloadTestData("delegated-jwt", "019c8e42-7f70-7000-8000-000000000101",
						"019c8e42-7f70-7000-8000-000000000103",
						"req_0123456789abcdef0123456789abcdef")
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

	private static FilePart filePart(Flux<DataBuffer> content) {
		return new FilePart() {
			@Override
			public String filename() {
				return "private-original-name.zip";
			}

			@Override
			public String name() {
				return "file";
			}

			@Override
			public HttpHeaders headers() {
				HttpHeaders headers = new HttpHeaders();
				headers.setContentType(MediaType.parseMediaType("application/zip"));
				return headers;
			}

			@Override
			public Flux<DataBuffer> content() {
				return content;
			}

			@Override
			public Mono<Void> transferTo(Path dest) {
				return Mono.error(new UnsupportedOperationException("not used"));
			}
		};
	}

	private static DataBuffer buffer(String value) {
		return BUFFERS.wrap(value.getBytes(StandardCharsets.UTF_8));
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
