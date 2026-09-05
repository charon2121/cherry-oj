package com.cherryoj.gatewayservice.problem;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.multipart.FilePartEvent;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;

import com.cherryoj.gatewayservice.auth.DelegatedIdentity;
import com.cherryoj.gatewayservice.auth.InternalRequestFactory;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
final class ProblemServiceClient {

	private static final MediaType APPLICATION_ZIP = MediaType.parseMediaType("application/zip");
	private static final ParameterizedTypeReference<List<ProblemDtos.TestDataVersion>> TEST_DATA_LIST =
			new ParameterizedTypeReference<>() { };

	private final WebClient client;
	private final ProblemServiceProperties properties;
	private final InternalRequestFactory requests;

	ProblemServiceClient(
			WebClient.Builder builder,
			ProblemServiceProperties properties,
			InternalRequestFactory requests) {
		this.client = builder.clone()
				.baseUrl(properties.baseUrl().toString())
				.codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(properties.maxJsonBytes()))
				.build();
		this.properties = properties;
		this.requests = requests;
	}

	Mono<ProblemDtos.ProblemList> listPublic(
			String q, String difficulty, List<String> tags, String codeMode, String language,
			String sort, String cursor, int size, String requestId) {
		return json(requests.request(client.get().uri(builder -> {
			builder.path("/internal/public/problems");
			query(builder, "q", q);
			query(builder, "difficulty", difficulty);
			if (tags != null) {
				tags.forEach(tag -> builder.queryParam("tag", tag));
			}
			query(builder, "codeMode", codeMode);
			query(builder, "language", language);
			query(builder, "sort", sort);
			query(builder, "cursor", cursor);
			return builder.queryParam("size", size).build();
		}), requestId), ProblemDtos.ProblemList.class);
	}

	Mono<ProblemDtos.ProblemDetail> getPublic(String slug, String requestId) {
		return json(requests.request(client.get().uri("/internal/public/problems/{slug}", slug), requestId),
				ProblemDtos.ProblemDetail.class);
	}

	Mono<ProblemDtos.AdminProblemPage> listAdmin(
			DelegatedIdentity identity, String q, String status, int page, int size) {
		return json(requests.authenticated(client.get().uri(builder -> {
			builder.path("/internal/admin/problems");
			query(builder, "q", q);
			query(builder, "status", status);
			return builder.queryParam("page", page).queryParam("size", size).build();
		}), identity), ProblemDtos.AdminProblemPage.class);
	}

	Mono<ProblemDtos.AdminProblem> createProblem(
			DelegatedIdentity identity, Object body) {
		return json(requests.authenticated(client.post().uri("/internal/admin/problems"), identity)
				.bodyValue(body), ProblemDtos.AdminProblem.class);
	}

	Mono<ProblemDtos.AdminProblem> getProblem(DelegatedIdentity identity, String problemId) {
		return json(requests.authenticated(
				client.get().uri("/internal/admin/problems/{problemId}", problemId), identity),
				ProblemDtos.AdminProblem.class);
	}

	Mono<ProblemDtos.AdminProblem> updateProblem(
			DelegatedIdentity identity, String problemId, Object body) {
		return json(requests.authenticated(
				client.patch().uri("/internal/admin/problems/{problemId}", problemId), identity)
				.bodyValue(body), ProblemDtos.AdminProblem.class);
	}

	Mono<ProblemDtos.AdminProblem> archiveProblem(
			DelegatedIdentity identity, String problemId, Object body) {
		return json(requests.authenticated(
				client.post().uri("/internal/admin/problems/{problemId}/archive", problemId), identity)
				.bodyValue(body), ProblemDtos.AdminProblem.class);
	}

	Mono<ProblemDtos.AdminProblemVersion> createRevision(
			DelegatedIdentity identity, String problemId, Object body) {
		return json(requests.authenticated(
				client.post().uri("/internal/admin/problems/{problemId}/versions", problemId), identity)
				.bodyValue(body), ProblemDtos.AdminProblemVersion.class);
	}

	Mono<ProblemDtos.AdminProblemVersion> getVersion(
			DelegatedIdentity identity, String problemId, String versionId) {
		return json(requests.authenticated(client.get().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}", problemId, versionId),
				identity), ProblemDtos.AdminProblemVersion.class);
	}

	Mono<ProblemDtos.AdminProblemVersion> updateVersion(
			DelegatedIdentity identity, String problemId, String versionId, Object body) {
		return json(requests.authenticated(client.patch().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}", problemId, versionId),
				identity).bodyValue(body), ProblemDtos.AdminProblemVersion.class);
	}

	Mono<Void> deleteVersion(
			DelegatedIdentity identity, String problemId, String versionId, long rowVersion) {
		return noContent(requests.authenticated(client.delete().uri(builder -> builder
				.path("/internal/admin/problems/{problemId}/versions/{versionId}")
				.queryParam("rowVersion", rowVersion).build(problemId, versionId)), identity));
	}

	Mono<ProblemDtos.ProblemDetail> preview(
			DelegatedIdentity identity, String problemId, String versionId) {
		return json(requests.authenticated(client.get().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}/preview", problemId, versionId),
				identity), ProblemDtos.ProblemDetail.class);
	}

	Mono<List<ProblemDtos.TestDataVersion>> listTestData(
			DelegatedIdentity identity, String problemId) {
		return json(requests.authenticated(
				client.get().uri("/internal/admin/problems/{problemId}/test-data", problemId), identity),
				TEST_DATA_LIST);
	}

	Mono<ProblemDtos.TestDataVersion> uploadTestData(
			DelegatedIdentity identity, String problemId, Flux<DataBuffer> content) {
		Flux<PartEvent> parts = FilePartEvent.create(
				"file", "test-data.zip", APPLICATION_ZIP, content).cast(PartEvent.class);
		return streamingJson(requests.authenticated(client.post().uri(
				"/internal/admin/problems/{problemId}/test-data", problemId), identity)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts, PartEvent.class),
				ProblemDtos.TestDataVersion.class);
	}

	Mono<Download> downloadTestData(
			DelegatedIdentity identity, String problemId, String testDataVersionId) {
		return requests.authenticated(client.get().uri(
				"/internal/admin/problems/{problemId}/test-data/{testDataVersionId}/download",
				problemId, testDataVersionId), identity)
				.accept(APPLICATION_ZIP)
				.retrieve()
				.onStatus(status -> !status.is2xxSuccessful(), this::downloadError)
				.toEntityFlux(DataBuffer.class)
				.timeout(properties.metadataTimeout())
				.map(entity -> download(entity, testDataVersionId));
	}

	Mono<ProblemDtos.AdminProblemVersion> bindTestData(
			DelegatedIdentity identity, String problemId, String versionId, Object body) {
		return json(requests.authenticated(client.put().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}/test-data", problemId, versionId),
				identity).bodyValue(body), ProblemDtos.AdminProblemVersion.class);
	}

	Mono<ProblemDtos.TestDataDeployment> deploy(
			DelegatedIdentity identity, String problemId, String versionId, Object body) {
		return streamingJson(requests.authenticated(client.post().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}/deployment", problemId, versionId),
				identity).bodyValue(body), ProblemDtos.TestDataDeployment.class);
	}

	Mono<ProblemDtos.LanguageCalibration> calibrate(
			DelegatedIdentity identity, String problemId, String versionId, Object body) {
		return streamingJson(requests.authenticated(client.post().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}/calibration", problemId, versionId),
				identity).bodyValue(body), ProblemDtos.LanguageCalibration.class);
	}

	Mono<ProblemDtos.PublishCheck> publishCheck(
			DelegatedIdentity identity, String problemId, String versionId) {
		return json(requests.authenticated(client.get().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}/publish-check", problemId, versionId),
				identity), ProblemDtos.PublishCheck.class);
	}

	Mono<ProblemDtos.AdminProblemVersion> publish(
			DelegatedIdentity identity, String problemId, String versionId, Object body) {
		return streamingJson(requests.authenticated(client.post().uri(
				"/internal/admin/problems/{problemId}/versions/{versionId}/publish", problemId, versionId),
				identity).bodyValue(body), ProblemDtos.AdminProblemVersion.class);
	}

	private <T> Mono<T> json(WebClient.RequestHeadersSpec<?> request, Class<T> type) {
		return response(request, response -> response.bodyToMono(type), properties.metadataTimeout());
	}

	private <T> Mono<T> json(
			WebClient.RequestHeadersSpec<?> request, ParameterizedTypeReference<T> type) {
		return response(request, response -> response.bodyToMono(type), properties.metadataTimeout());
	}

	private <T> Mono<T> streamingJson(WebClient.RequestHeadersSpec<?> request, Class<T> type) {
		return response(request, response -> response.bodyToMono(type), properties.streamingTimeout());
	}

	private <T> Mono<T> response(
			WebClient.RequestHeadersSpec<?> request,
			java.util.function.Function<ClientResponse, Mono<T>> decoder,
			java.time.Duration timeout) {
		return request.exchangeToMono(response -> response.statusCode().is2xxSuccessful()
				? decoder.apply(response).switchIfEmpty(Mono.error(new IllegalStateException("empty response")))
				: error(response.statusCode(), response.bodyToMono(InternalError.class)))
				.timeout(timeout);
	}

	private Mono<Void> noContent(WebClient.RequestHeadersSpec<?> request) {
		return request.exchangeToMono(response -> response.statusCode().is2xxSuccessful()
				? response.releaseBody()
				: error(response.statusCode(), response.bodyToMono(InternalError.class)))
				.timeout(properties.metadataTimeout());
	}

	private Mono<? extends Throwable> downloadError(ClientResponse response) {
		return response.bodyToMono(InternalError.class)
				.onErrorReturn(new InternalError("UPSTREAM_ERROR"))
				.defaultIfEmpty(new InternalError("UPSTREAM_ERROR"))
				.map(error -> new ProblemServiceClientException(
						response.statusCode(), safeCode(error.code())));
	}

	private Download download(ResponseEntity<Flux<DataBuffer>> entity, String testDataVersionId) {
		MediaType contentType = entity.getHeaders().getContentType();
		if (contentType == null || !APPLICATION_ZIP.isCompatibleWith(contentType)) {
			throw new IllegalStateException("invalid download media type");
		}
		long length = entity.getHeaders().getContentLength();
		Flux<DataBuffer> body = entity.getBody();
		if (body == null) {
			throw new IllegalStateException("empty download body");
		}
		return new Download(length >= 0 ? length : null, testDataVersionId + ".zip",
				body.timeout(properties.streamingTimeout()));
	}

	private static <T> Mono<T> error(HttpStatusCode status, Mono<InternalError> body) {
		return body.onErrorReturn(new InternalError("UPSTREAM_ERROR"))
				.defaultIfEmpty(new InternalError("UPSTREAM_ERROR"))
				.flatMap(error -> Mono.error(
						new ProblemServiceClientException(status, safeCode(error.code()))));
	}

	private static void query(UriBuilder builder, String name, String value) {
		if (value != null) {
			builder.queryParam(name, value);
		}
	}

	private static String safeCode(String code) {
		return code != null && code.matches("^[A-Z][A-Z0-9_]{0,63}$") ? code : "UPSTREAM_ERROR";
	}

	record Download(Long contentLength, String filename, Flux<DataBuffer> body) {
	}

	private record InternalError(String code) {
	}
}
