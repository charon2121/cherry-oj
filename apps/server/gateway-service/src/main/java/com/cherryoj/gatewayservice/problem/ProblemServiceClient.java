package com.cherryoj.gatewayservice.problem;

import java.util.List;
import java.util.concurrent.TimeoutException;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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

	Mono<Void> deleteProblem(DelegatedIdentity identity, String problemId, long rowVersion) {
		return noContent(requests.authenticated(client.delete().uri(builder -> builder
				.path("/internal/admin/problems/{problemId}")
				.queryParam("rowVersion", rowVersion).build(problemId)), identity));
	}

	Mono<ProblemDtos.ProblemDetail> preview(DelegatedIdentity identity, String problemId) {
		return json(requests.authenticated(
				client.get().uri("/internal/admin/problems/{problemId}/preview", problemId), identity),
				ProblemDtos.ProblemDetail.class);
	}

	Mono<ProblemDtos.TestData> getTestData(DelegatedIdentity identity, String problemId) {
		return json(requests.authenticated(
				client.get().uri("/internal/admin/problems/{problemId}/test-data", problemId), identity),
				ProblemDtos.TestData.class);
	}

	Mono<ProblemDtos.TestData> replaceTestData(
			DelegatedIdentity identity, String problemId, Flux<DataBuffer> content) {
		Flux<PartEvent> parts = FilePartEvent.create(
				"file", "test-data.zip", APPLICATION_ZIP, content).cast(PartEvent.class);
		return streamingJson(requests.authenticated(client.put().uri(
				"/internal/admin/problems/{problemId}/test-data", problemId), identity)
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(parts, PartEvent.class),
				ProblemDtos.TestData.class);
	}

	Mono<ProblemDtos.LanguageCalibration> calibrate(
			DelegatedIdentity identity, String problemId, Object body) {
		return streamingJson(requests.authenticated(client.post().uri(
				"/internal/admin/problems/{problemId}/calibration", problemId), identity)
				.bodyValue(body), ProblemDtos.LanguageCalibration.class);
	}

	Mono<ProblemDtos.PublishCheck> publishCheck(DelegatedIdentity identity, String problemId) {
		return json(requests.authenticated(
				client.get().uri("/internal/admin/problems/{problemId}/publish-check", problemId), identity),
				ProblemDtos.PublishCheck.class);
	}

	Mono<ProblemDtos.AdminProblem> publish(DelegatedIdentity identity, String problemId, Object body) {
		return streamingJson(requests.authenticated(client.post().uri(
				"/internal/admin/problems/{problemId}/publish", problemId), identity)
				.bodyValue(body), ProblemDtos.AdminProblem.class);
	}

	Mono<ProblemDtos.AdminProblem> unpublish(DelegatedIdentity identity, String problemId, Object body) {
		return json(requests.authenticated(client.post().uri(
				"/internal/admin/problems/{problemId}/unpublish", problemId), identity)
				.bodyValue(body), ProblemDtos.AdminProblem.class);
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

	private static <T> Mono<T> error(HttpStatusCode status, Mono<InternalError> body) {
		return body.onErrorReturn(new InternalError("UPSTREAM_ERROR"))
				.defaultIfEmpty(new InternalError("UPSTREAM_ERROR"))
				.flatMap(error -> Mono.error(
						new ProblemServiceClientException(
								status, safeCode(error.code()), safeDetail(error.detail()))));
	}

	private static void query(UriBuilder builder, String name, String value) {
		if (value != null) {
			builder.queryParam(name, value);
		}
	}

	private static String safeCode(String code) {
		return code != null && code.matches("^[A-Z][A-Z0-9_]{0,63}$") ? code : "UPSTREAM_ERROR";
	}

	private static String safeDetail(String detail) {
		if (detail == null) return null;
		String candidate = detail.strip();
		if (candidate.isEmpty() || candidate.length() > 512
				|| candidate.chars().anyMatch(Character::isISOControl)) {
			return null;
		}
		return candidate;
	}

	private record InternalError(String code, String detail) {
		private InternalError(String code) {
			this(code, null);
		}
	}
}
