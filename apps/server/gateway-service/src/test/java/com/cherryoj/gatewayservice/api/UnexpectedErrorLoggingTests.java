package com.cherryoj.gatewayservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.logging.logback.StructuredLogEncoder;
import org.springframework.core.env.Environment;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.json.JsonMapper;

class UnexpectedErrorLoggingTests {

	@Test
	void patchFailureLinksResponseToSafeStructuredExceptionFacts() {
		Logger logger = (Logger) LoggerFactory.getLogger(ApiProblemHandler.class);
		ListAppender<ILoggingEvent> events = capture(logger);
		try {
			var response = WebTestClient.bindToController(new FailureController())
					.controllerAdvice(new ApiProblemHandler()).webFilter(new RequestIdWebFilter()).build()
					.patch().uri("/test/problem").exchange()
					.expectStatus().isEqualTo(500)
					.expectHeader().contentType(MediaType.APPLICATION_PROBLEM_JSON)
					.expectBody().jsonPath("$.code").isEqualTo("INTERNAL_ERROR").returnResult();
			String requestId = response.getResponseHeaders().getFirst("X-Request-Id");
			assertThat(events.list).hasSize(1);
			ILoggingEvent event = events.list.getFirst();
			Map<String, Object> facts = fields(event);
			assertThat(facts).containsEntry("event", "api.unexpected_error")
					.containsEntry("request_id", requestId)
					.containsEntry("error_type", IllegalStateException.class.getName());
			assertThat(facts.get("exception_types")).isEqualTo(List.of(
					IllegalStateException.class.getName(), IllegalArgumentException.class.getName()));
			assertThat((List<?>) facts.get("application_frames")).isNotEmpty();
			assertThat(facts.toString()).contains("FailureController.fail").doesNotContain("private-token");
			assertThat(event.getFormattedMessage()).doesNotContain("private-token");
			assertThat(event.getThrowableProxy()).isNull();
			// 夹具读取的是实际 logstash JSON；Logback 的内存键值正确仍不足以证明可导出。
			LoggerContext encoderContext = new LoggerContext();
			encoderContext.putObject(Environment.class.getName(), new MockEnvironment());
			StructuredLogEncoder encoder = new StructuredLogEncoder();
			encoder.setContext(encoderContext);
			encoder.setFormat("logstash");
			encoder.start();
			try {
				var json = JsonMapper.builder().build().readTree(encoder.encode(event));
				assertThat(json.path("request_id").asString()).isEqualTo(requestId);
				assertThat(json.path("error_type").asString()).isEqualTo(IllegalStateException.class.getName());
				assertThat(json.path("exception_types").isArray()).isTrue();
				assertThat(json.path("exception_types").size()).isEqualTo(2);
				assertThat(json.path("application_frames").isArray()).isTrue();
				assertThat(json.toString()).doesNotContain("private-token", "stack_trace");
			}
			finally {
				encoder.stop();
				encoderContext.stop();
			}
		}
		finally {
			logger.detachAppender(events);
			events.stop();
		}
	}

	@Test
	void cyclicAndDeepCausesAreBoundedAndNeverReadExceptionTextOrFilePaths() {
		Logger logger = (Logger) LoggerFactory.getLogger(ApiProblemHandler.class);
		ListAppender<ILoggingEvent> events = capture(logger);
		try {
			var first = new UnreadableMessage();
			var second = new UnreadableMessage();
			first.initCause(second);
			second.initCause(first);
			StackTraceElement[] stack = new StackTraceElement[30];
			for (int i = 0; i < stack.length; i++) {
				stack[i] = new StackTraceElement("com.cherryoj.example.Controller", "call" + i,
						"/private-token/source.java", 123);
			}
			first.setStackTrace(stack);
			log(first);
			Map<String, Object> cyclic = fields(events.list.getLast());
			assertThat((List<?>) cyclic.get("exception_types")).hasSize(2);
			assertThat((List<?>) cyclic.get("application_frames")).hasSize(12);
			assertThat(cyclic.toString()).doesNotContain("private-token", "source.java", "123");
			Throwable deep = first;
			for (int i = 0; i < 20; i++) {
				deep = new IllegalStateException("private-token", deep);
			}
			log(deep);
			assertThat((List<?>) fields(events.list.getLast()).get("exception_types")).hasSize(8);
			for (ILoggingEvent event : events.list) {
				assertThat(event.getThrowableProxy()).isNull();
			}
		}
		finally {
			logger.detachAppender(events);
			events.stop();
		}
	}

	private static void log(Throwable error) {
		new ApiProblemHandler().handleUnexpected(error,
				MockServerWebExchange.from(MockServerHttpRequest.patch("/test/problem")));
	}

	private static ListAppender<ILoggingEvent> capture(Logger logger) {
		ListAppender<ILoggingEvent> events = new ListAppender<>();
		events.start();
		logger.addAppender(events);
		return events;
	}

	private static Map<String, Object> fields(ILoggingEvent event) {
		return event.getKeyValuePairs() == null ? Map.of() : event.getKeyValuePairs().stream()
				.collect(Collectors.toMap(pair -> pair.key, pair -> pair.value));
	}

	@RestController
	static class FailureController {
		@PatchMapping("/test/problem")
		void fail() {
			throw new IllegalStateException("private-token", new IllegalArgumentException("private-token"));
		}
	}

	static class UnreadableMessage extends RuntimeException {
		@Override
		public String getMessage() {
			throw new AssertionError("diagnosis must not read exception text");
		}
	}
}
