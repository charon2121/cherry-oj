package com.cherryoj.gatewayservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.web.ErrorProperties;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.error.DefaultErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.DefaultErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.web.server.MockWebSession;
import org.springframework.security.web.server.csrf.CsrfToken;
import org.springframework.security.web.server.csrf.CsrfWebFilter;
import org.springframework.security.web.server.csrf.DefaultCsrfToken;
import org.springframework.security.web.server.csrf.WebSessionServerCsrfTokenRepository;
import org.springframework.security.web.server.csrf.XorServerCsrfTokenRequestAttributeHandler;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.session.CookieWebSessionIdResolver;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionManager;
import org.springframework.web.server.session.WebSessionStore;

import reactor.core.publisher.Mono;

class UnhandledApiErrorObserverTests {

	private static final String PATH = "/api/admin/problems/00000000-0000-0000-0000-000000000001";

	@ParameterizedTest
	@ValueSource(ints = {400, 403, 404, 405, 429, 500, 503})
	void explicitClientErrorsAreNotRecordedAsUnexpectedServerFailures(int status) {
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.patch(PATH));
		var failure = new ResponseStatusException(HttpStatus.valueOf(status), "private-detail");
		try (var logs = new CapturedLogs()) {
			assertThatThrownBy(() -> new UnhandledApiErrorObserver().handle(exchange, failure).block())
					.isSameAs(failure);
			assertThat(exchange.getResponse().getStatusCode()).isNull();
			assertThat(exchange.getResponse().getHeaders().isEmpty()).isTrue();
			assertThat(logs.observerEvents.list).hasSize(status >= 500 ? 1 : 0);
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void sessionReadFailureBypassesAdviceAndOnlyObserverProvidesSafeEvent(boolean observe) {
		var failure = new RedisConnectionFailureException("private-read-error");
		try (var logs = new CapturedLogs(); var fixture = new Fixture(observe, true, failure, null)) {
			EntityExchangeResult<byte[]> response = fixture.patch(true)
					.expectStatus().isEqualTo(500).expectBody().returnResult();

			assertThat(fixture.controller.writes).hasValue(0);
			assertThat(fixture.reads).hasValue(1);
			assertThat(fixture.errors.seen).containsExactly(failure);
			assertThat(logs.adviceEvents.list).isEmpty();
			if (observe) assertSafeEvent(logs.observerEvents, response, failure);
			else assertThat(logs.observerEvents.list).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void sessionSaveFailureAfterOneWriteIsObservedOnceAndNeverRetried(boolean advice) {
		var failure = new RedisConnectionFailureException("private-save-error");
		try (var logs = new CapturedLogs(); var fixture = new Fixture(true, advice, null, failure)) {
			EntityExchangeResult<byte[]> response = fixture.patch(true)
					.expectStatus().isEqualTo(500).expectBody().returnResult();

			assertThat(fixture.controller.writes).hasValue(1);
			assertThat(fixture.session.saves).hasValue(1);
			if (advice) {
				assertThat(fixture.errors.seen).isEmpty();
				assertThat(logs.observerEvents.list).isEmpty();
				assertSafeEvent(logs.adviceEvents, response, failure);
			}
			else {
				// Without advice the same beforeCommit failure reaches WebExceptionHandler.
				assertThat(fixture.errors.seen).containsExactly(failure);
				assertThat(logs.adviceEvents.list).isEmpty();
				assertSafeEvent(logs.observerEvents, response, failure);
			}
		}
	}

	@Test
	void ordinaryCsrfRejectionStaysForbiddenAndIsNotAnUnexpectedError() {
		try (var logs = new CapturedLogs(); var fixture = new Fixture(true, true, null, null)) {
			fixture.patch(false).expectStatus().isForbidden();

			assertThat(fixture.controller.writes).hasValue(0);
			assertThat(fixture.errors.seen).isEmpty();
			assertThat(logs.observerEvents.list).isEmpty();
			assertThat(logs.adviceEvents.list).isEmpty();
		}
	}

	@ParameterizedTest
	@ValueSource(booleans = {false, true})
	void originalErrorAndResponseSurviveObservationWithoutRequestIdEvenAfterCommit(boolean committed) {
		var exchange = MockServerWebExchange.from(MockServerHttpRequest.patch(PATH));
		exchange.getResponse().setStatusCode(HttpStatus.ACCEPTED);
		exchange.getResponse().getHeaders().set("X-Test-State", "retained");
		if (committed) exchange.getResponse().setComplete().block();
		Map<String, List<String>> headers = exchange.getResponse().getHeaders().headerSet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, entry -> List.copyOf(entry.getValue())));
		Map<String, Object> attributes = Map.copyOf(exchange.getAttributes());
		var failure = new IllegalStateException("private-token", new IllegalArgumentException("private-body"));
		try (var logs = new CapturedLogs()) {
			assertThatThrownBy(() -> new UnhandledApiErrorObserver().handle(exchange, failure).block())
					.isSameAs(failure);

			assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
			assertThat(exchange.getResponse().getHeaders().asMultiValueMap()).isEqualTo(headers);
			assertThat(exchange.getAttributes()).isEqualTo(attributes);
			assertThat(exchange.getResponse().isCommitted()).isEqualTo(committed);
			assertThat(logs.observerEvents.list).hasSize(1);
			ILoggingEvent event = logs.observerEvents.list.getFirst();
			assertThat(fields(event)).containsEntry("request_id", "unavailable");
			assertThat(fields(event).get("exception_types")).isEqualTo(List.of(
					IllegalStateException.class.getName(), IllegalArgumentException.class.getName()));
			assertThat(fields(event).toString()).doesNotContain("private-token", "private-body");
			assertThat(event.getThrowableProxy()).isNull();
		}
	}

	private static void assertSafeEvent(ListAppender<ILoggingEvent> events,
			EntityExchangeResult<byte[]> response, Throwable error) {
		assertThat(events.list).hasSize(1);
		ILoggingEvent event = events.list.getFirst();
		String requestId = response.getResponseHeaders().getFirst(ApiRequestContext.REQUEST_ID_HEADER);
		assertThat(requestId).matches("req_[0-9a-f]{32}");
		assertThat(fields(event)).containsOnlyKeys(
				"event", "request_id", "error_type", "exception_types", "application_frames")
				.containsEntry("event", "api.unexpected_error")
				.containsEntry("request_id", requestId)
				.containsEntry("error_type", error.getClass().getName());
		assertThat((List<?>) fields(event).get("exception_types")).isNotEmpty().hasSizeLessThanOrEqualTo(8);
		assertThat((List<?>) fields(event).get("application_frames")).isNotEmpty().hasSizeLessThanOrEqualTo(12);
		assertThat(fields(event).toString()).doesNotContain("private-read-error", "private-save-error",
				"Session was invalidated");
		assertThat(event.getFormattedMessage()).doesNotContain("private-");
		assertThat(event.getThrowableProxy()).isNull();
	}

	private static Map<String, Object> fields(ILoggingEvent event) {
		return event.getKeyValuePairs().stream().collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}

	private static final class CapturedLogs implements AutoCloseable {
		final Logger observer = (Logger) LoggerFactory.getLogger(UnhandledApiErrorObserver.class);
		final Logger advice = (Logger) LoggerFactory.getLogger(ApiProblemHandler.class);
		final ListAppender<ILoggingEvent> observerEvents = new ListAppender<>();
		final ListAppender<ILoggingEvent> adviceEvents = new ListAppender<>();

		CapturedLogs() {
			observerEvents.start();
			adviceEvents.start();
			observer.addAppender(observerEvents);
			advice.addAppender(adviceEvents);
		}

		@Override
		public void close() {
			observer.detachAppender(observerEvents);
			advice.detachAppender(adviceEvents);
			observerEvents.stop();
			adviceEvents.stop();
		}
	}

	private static final class Fixture implements AutoCloseable {
		final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
		final PatchController controller = new PatchController();
		final AtomicInteger reads = new AtomicInteger();
		final TestSession session;
		final RecordingDefaultHandler errors;
		final WebTestClient client;

		Fixture(boolean observer, boolean advice, RuntimeException readFailure, RuntimeException saveFailure) {
			session = new TestSession(saveFailure);
			WebSessionStore store = mock(WebSessionStore.class);
			when(store.createWebSession()).thenReturn(Mono.just(new MockWebSession()));
			when(store.retrieveSession(session.getId())).thenReturn(Mono.defer(() -> {
				reads.incrementAndGet();
				return readFailure == null ? Mono.just(session) : Mono.error(readFailure);
			}));
			DefaultWebSessionManager manager = new DefaultWebSessionManager();
			CookieWebSessionIdResolver cookies = new CookieWebSessionIdResolver();
			cookies.setCookieName("CHERRY_SESSION");
			manager.setSessionIdResolver(cookies);
			manager.setSessionStore(store);
			context.register(WebFluxConfiguration.class);
			context.registerBean("webSessionManager", WebSessionManager.class, () -> manager);
			context.registerBean(PatchController.class, () -> controller);
			context.registerBean(RequestIdWebFilter.class);
			if (advice) context.registerBean(ApiProblemHandler.class);
			if (observer) context.registerBean(UnhandledApiErrorObserver.class);
			context.refresh();
			errors = context.getBean(RecordingDefaultHandler.class);
			client = WebTestClient.bindToApplicationContext(context).build();
		}

		WebTestClient.ResponseSpec patch(boolean validCsrf) {
			var request = client.patch().uri(PATH).cookie("CHERRY_SESSION", session.getId())
					.accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON);
			if (validCsrf) {
				// Use Spring's masking implementation, not a csrf() mutator that bypasses validation.
				var exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/auth/csrf"));
				new XorServerCsrfTokenRequestAttributeHandler().handle(exchange, Mono.just(session.token));
				CsrfToken token = exchange.<Mono<CsrfToken>>getAttribute(CsrfToken.class.getName()).block();
				request.header(token.getHeaderName(), token.getToken());
			}
			return request.bodyValue(Map.of("slug", "test-problem", "visibility", "PUBLIC", "rowVersion", 1))
					.exchange();
		}

		@Override
		public void close() {
			context.close();
		}
	}

	private static final class TestSession extends MockWebSession {
		final AtomicInteger saves = new AtomicInteger();
		final CsrfToken token = new DefaultCsrfToken("X-CSRF-Token", "_csrf", "synthetic-csrf-token");
		private final RuntimeException failure;

		TestSession(RuntimeException failure) {
			this.failure = failure;
			getAttributes().put(WebSessionServerCsrfTokenRepository.class.getName() + ".CSRF_TOKEN", token);
			start();
		}

		@Override
		public Mono<Void> save() {
			saves.incrementAndGet();
			return failure == null ? Mono.empty() : Mono.error(failure);
		}
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebFlux
	static class WebFluxConfiguration {
		@Bean
		CsrfWebFilter csrfFilter() {
			WebSessionServerCsrfTokenRepository repository = new WebSessionServerCsrfTokenRepository();
			repository.setHeaderName("X-CSRF-Token");
			CsrfWebFilter filter = new CsrfWebFilter();
			filter.setCsrfTokenRepository(repository);
			return filter;
		}

		@Bean
		@Order(-1)
		RecordingDefaultHandler defaultErrors(ApplicationContext context, ServerCodecConfigurer codecs) {
			var handler = new RecordingDefaultHandler(context);
			handler.setMessageReaders(codecs.getReaders());
			handler.setMessageWriters(codecs.getWriters());
			return handler;
		}
	}

	static final class RecordingDefaultHandler extends DefaultErrorWebExceptionHandler {
		final List<Throwable> seen = new java.util.ArrayList<>();

		RecordingDefaultHandler(ApplicationContext context) {
			super(new DefaultErrorAttributes(), new WebProperties.Resources(), new ErrorProperties(), context);
		}

		@Override
		public Mono<Void> handle(ServerWebExchange exchange, Throwable error) {
			seen.add(error);
			return super.handle(exchange, error);
		}
	}

	@RestController
	static class PatchController {
		final AtomicInteger writes = new AtomicInteger();

		@PatchMapping("/api/admin/problems/{problemId}")
		ResponseEntity<ApiSuccess<Map<String, Object>>> patch(
				@RequestBody Map<String, Object> request, ServerWebExchange exchange) {
			// Simulate the completed downstream write; no database or network is started.
			writes.incrementAndGet();
			return ResponseEntity.ok(ApiSuccess.of(request, ApiRequestContext.requestId(exchange)));
		}
	}
}
