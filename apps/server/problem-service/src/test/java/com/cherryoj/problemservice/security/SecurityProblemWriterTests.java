package com.cherryoj.problemservice.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.stream.Collectors;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.client.ResourceAccessException;

class SecurityProblemWriterTests {

	@Test
	void classifiesSecurityFailuresWithSafeRequestIdAndNoCredentialValues() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(SecurityProblemWriter.class);
		ListAppender<ILoggingEvent> events = new ListAppender<>();
		events.start();
		logger.addAppender(events);
		try {
			SecurityProblemWriter writer = new SecurityProblemWriter();
			MockHttpServletRequest request = request("req_0123456789abcdef0123456789abcdef");

			MockHttpServletResponse invalid = new MockHttpServletResponse();
			writer.commence(request, invalid, new BadCredentialsException("Bearer secret-token"));
			assertThat(invalid.getStatus()).isEqualTo(401);
			assertThat(invalid.getContentAsString()).contains("INVALID_ACCESS_TOKEN")
					.doesNotContain("secret-token");

			MockHttpServletResponse unavailable = new MockHttpServletResponse();
			writer.commence(request, unavailable, new AuthenticationServiceException(
					"decoder failed", new ResourceAccessException("Couldn't retrieve remote JWK set")));
			assertThat(unavailable.getStatus()).isEqualTo(503);
			assertThat(unavailable.getContentAsString()).contains("IDENTITY_KEY_UNAVAILABLE");

			MockHttpServletResponse forbidden = new MockHttpServletResponse();
			writer.handle(request, forbidden, new AccessDeniedException("private-role"));
			assertThat(forbidden.getStatus()).isEqualTo(403);

			assertThat(events.list).extracting(SecurityProblemWriterTests::fields)
					.containsExactly(
							Map.of("event", "identity_authentication_failed", "http_status", 401,
									"identity_failure", "missing_bearer",
									"request_id", "req_0123456789abcdef0123456789abcdef"),
							Map.of("event", "identity_authentication_failed", "http_status", 503,
									"identity_failure", "key_service_unavailable",
									"request_id", "req_0123456789abcdef0123456789abcdef"),
							Map.of("event", "access_denied", "http_status", 403,
									"request_id", "req_0123456789abcdef0123456789abcdef"));
			assertThat(events.list).extracting(ILoggingEvent::getFormattedMessage)
					.allMatch(message -> !message.contains("secret-token")
							&& !message.contains("private-role"));
		}
		finally {
			logger.detachAppender(events);
			events.stop();
		}
	}

	@Test
	void omitsUntrustedRequestIdFromSecurityEvent() throws Exception {
		Logger logger = (Logger) LoggerFactory.getLogger(SecurityProblemWriter.class);
		ListAppender<ILoggingEvent> events = new ListAppender<>();
		events.start();
		logger.addAppender(events);
		try {
			new SecurityProblemWriter().commence(
					request("attacker\nforged"), new MockHttpServletResponse(),
					new BadCredentialsException("invalid"));
			assertThat(fields(events.list.getFirst())).doesNotContainKey("request_id");
		}
		finally {
			logger.detachAppender(events);
			events.stop();
		}
	}

	private static MockHttpServletRequest request(String requestId) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.addHeader("X-Request-Id", requestId);
		return request;
	}

	private static Map<String, Object> fields(ILoggingEvent event) {
		return event.getKeyValuePairs().stream().collect(Collectors.toMap(
				field -> field.key, field -> field.value));
	}
}
