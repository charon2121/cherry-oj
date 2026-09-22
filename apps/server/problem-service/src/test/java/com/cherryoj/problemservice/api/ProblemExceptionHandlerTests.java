package com.cherryoj.problemservice.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.EOFException;
import java.net.SocketException;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.ProblemPublicationService;
import jakarta.servlet.Filter;
import jakarta.servlet.ServletException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class ProblemExceptionHandlerTests {

	private static final String REQUEST_ID = "req_0123456789abcdef0123456789abcdef";
	private static final String PRIVATE_VALUE = "password=synthetic-secret Bearer eyJ.synthetic.jwt /private/problem.sql";
	private final Logger logger = (Logger) LoggerFactory.getLogger(ProblemExceptionHandler.class);
	private final ListAppender<ILoggingEvent> events = new ListAppender<>();
	private AnnotationConfigWebApplicationContext context;
	private AdminProblemService problems;
	private FailureController failures;
	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		context = new AnnotationConfigWebApplicationContext();
		context.setServletContext(new MockServletContext());
		context.register(Config.class);
		context.refresh();
		mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
		problems = context.getBean(AdminProblemService.class);
		failures = context.getBean(FailureController.class);
		events.start();
		logger.addAppender(events);
	}

	@AfterEach
	void tearDown() {
		logger.detachAppender(events);
		events.stop();
		context.close();
	}

	@Test
	void unexpectedPatchFailureReturnsSafeInternalProblemAndOnlyBoundedFacts() throws Exception {
		when(problems.updateProblem(anyString(), any(), anyString()))
				.thenThrow(new IllegalStateException(PRIVATE_VALUE, new IllegalArgumentException(PRIVATE_VALUE)));

		var result = mvc.perform(adminPatch().header("X-Request-Id", REQUEST_ID)
				.header("Cookie", "session=" + PRIVATE_VALUE))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(500))
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
				.andExpect(jsonPath("$.detail").value("服务器暂时无法处理请求。"))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.meta").doesNotExist())
				.andReturn();

		assertThat(result.getResponse().getContentAsString())
				.doesNotContain(PRIVATE_VALUE, "IllegalStateException", "IllegalArgumentException", "session=");
		assertThat(events.list).hasSize(1);
		var event = events.list.getFirst();
		assertThat(event.getLevel()).isEqualTo(Level.ERROR);
		assertThat(fields(event)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"event", "api.unexpected_error", "error_type", "java.lang.IllegalStateException",
				"request_id", REQUEST_ID));
		assertSafeEvent(event);
	}

	@ParameterizedTest
	@MethodSource("invalidRequestIds")
	void missingOrMalformedRequestIdIsOmittedWithoutLoggingItsContents(String requestId) throws Exception {
		failures.error = new IllegalStateException(PRIVATE_VALUE);
		var request = get("/boundary/failure");
		if (requestId != null) {
			request.header("X-Request-Id", requestId);
		}
		mvc.perform(request).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
		assertThat(events.list).hasSize(1);
		var event = events.list.getFirst();
		assertThat(fields(event)).containsExactlyInAnyOrderEntriesOf(Map.of(
				"event", "api.unexpected_error", "error_type", "java.lang.IllegalStateException"));
		assertSafeEvent(event);
	}

	static Stream<Arguments> invalidRequestIds() {
		return Stream.of(null, "", "attacker\n" + REQUEST_ID, REQUEST_ID + "\n", REQUEST_ID + "x",
				REQUEST_ID.substring(0, 35), REQUEST_ID.toUpperCase(), "req_" + "g".repeat(32),
				PRIVATE_VALUE.repeat(200)).map(value -> Arguments.of((Object) value));
	}

	@ParameterizedTest
	@MethodSource("domainFailures")
	void domainErrorsKeepTheirStatusAndCode(HttpStatus status, String code) throws Exception {
		when(problems.updateProblem(anyString(), any(), anyString()))
				.thenThrow(new ProblemApiException(status, code, "已知业务错误。"));
		mvc.perform(adminPatch()).andExpect(status().is(status.value()))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value(code))
				.andExpect(jsonPath("$.detail").value("已知业务错误。"));
		assertThat(events.list).isEmpty();
	}

	static Stream<Arguments> domainFailures() {
		return Stream.of(Arguments.of(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND"),
				Arguments.of(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT"));
	}

	@Test
	void wrappedKnownFailuresKeepTheirExistingHandlers() throws Exception {
		failures.error = new IllegalStateException(new ProblemApiException(
				HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT", "已知业务错误。"));
		mvc.perform(get("/boundary/failure")).andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("ROW_VERSION_CONFLICT"));
		failures.error = new IllegalStateException(new ConstraintViolationException(PRIVATE_VALUE, Set.of()));
		mvc.perform(get("/boundary/failure")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_QUERY"));
		failures.error = new IllegalStateException(new MaxUploadSizeExceededException(100));
		mvc.perform(get("/boundary/failure")).andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
		assertThat(events.list).isEmpty();
	}

	@Test
	void frameworkAndSecurityWrappersDoNotHidePreviouslyMatchedDomainCauses() throws Exception {
		var conflict = new ProblemApiException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT", "已知业务错误。");
		for (Exception wrapper : new Exception[] {
				new ResponseStatusException(HttpStatus.GONE, null, conflict),
				new ErrorResponseException(HttpStatus.BAD_REQUEST, conflict),
				new GoneException(conflict),
				new AccessDeniedException(PRIVATE_VALUE, conflict),
				new BadCredentialsException(PRIVATE_VALUE, conflict)}) {
			failures.error = wrapper;
			mvc.perform(get("/boundary/failure")).andExpect(status().isConflict())
					.andExpect(jsonPath("$.code").value("ROW_VERSION_CONFLICT"));
		}
		assertThat(events.list).isEmpty();
	}

	@Test
	void unknownCheckedExceptionProducesSafeProblem() throws Exception {
		failures.error = new Exception(PRIVATE_VALUE);
		mvc.perform(get("/boundary/failure")).andExpect(status().isInternalServerError())
				.andExpect(jsonPath("$.code").value("INTERNAL_ERROR"));
		assertThat(events.list).hasSize(1);
		assertSafeEvent(events.list.getFirst());
	}

	@Test
	void handlerDoesNotLoopOnCyclicCauses() throws Exception {
		// MVC's controller-local handler lookup cannot accept cycles; this only covers our boundary logic.
		var outer = new IllegalStateException(PRIVATE_VALUE);
		var inner = new IllegalArgumentException(PRIVATE_VALUE, outer);
		outer.initCause(inner);
		var response = new ProblemExceptionHandler().unexpected(outer, new MockHttpServletRequest());
		assertThat(response.getStatusCode().value()).isEqualTo(500);
		assertThat(response.getBody()).containsEntry("code", "INTERNAL_ERROR");
		assertThat(events.list).hasSize(1);
		assertSafeEvent(events.list.getFirst());
	}

	@Test
	void disconnectedClientIsLeftToMvcWithoutWritingAnotherErrorResponse() throws Exception {
		for (Exception disconnected : new Exception[] {
				new EOFException(), new SocketException("Broken pipe"),
				new IllegalStateException(new SocketException("Connection reset by peer"))}) {
			failures.error = disconnected;
			mvc.perform(get("/boundary/failure")).andExpect(content().string(""))
					.andExpect(result -> assertThat(result.getResolvedException()).isSameAs(disconnected));
		}
		assertThat(events.list).isEmpty();
	}

	@Test
	void validationAndUploadKeepExistingCodes() throws Exception {
		mvc.perform(post("/boundary/body").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
		mvc.perform(get("/boundary/query").param("count", "not-a-number"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_QUERY"));
		failures.error = new MaxUploadSizeExceededException(100);
		mvc.perform(get("/boundary/failure")).andExpect(status().isPayloadTooLarge())
				.andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
		assertThat(events.list).isEmpty();
	}

	@Test
	void frameworkParseMediaMethodAndMissingParameterErrorsKeepTheirStatuses() throws Exception {
		mvc.perform(post("/boundary/body").contentType(MediaType.APPLICATION_JSON).content("{"))
				.andExpect(status().isBadRequest());
		mvc.perform(post("/boundary/body").contentType(MediaType.TEXT_PLAIN).content("body"))
				.andExpect(status().isUnsupportedMediaType());
		mvc.perform(post("/boundary/body").contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"ok\"}").accept(MediaType.APPLICATION_XML))
				.andExpect(status().isNotAcceptable());
		mvc.perform(get("/boundary/body")).andExpect(status().isMethodNotAllowed())
				.andExpect(header().string("Allow", "POST"));
		mvc.perform(get("/boundary/query")).andExpect(status().isBadRequest());
		assertThat(events.list).isEmpty();
	}

	@Test
	void explicitHttpStatusesRemainHandledByTheFramework() throws Exception {
		failures.error = new ResponseStatusException(HttpStatus.GONE);
		mvc.perform(get("/boundary/failure")).andExpect(status().isGone());
		failures.error = new GoneException();
		mvc.perform(get("/boundary/failure")).andExpect(status().isGone());
		failures.error = new IllegalStateException(new GoneException());
		mvc.perform(get("/boundary/failure")).andExpect(status().isGone());
		assertThat(events.list).isEmpty();
	}

	@Test
	void securityExceptionsFromControllerReachTheSecurityFilterChain() throws Exception {
		failures.error = new AccessDeniedException(PRIVATE_VALUE);
		mvc.perform(get("/boundary/failure")).andExpect(status().isUnauthorized());
		mvc.perform(get("/boundary/failure").with(user("member")))
				.andExpect(status().isForbidden());
		failures.error = new BadCredentialsException(PRIVATE_VALUE);
		mvc.perform(get("/boundary/failure").with(user("member")))
				.andExpect(status().isUnauthorized());
		failures.error = new IllegalStateException(new AccessDeniedException(PRIVATE_VALUE));
		mvc.perform(get("/boundary/failure").with(user("member")))
				.andExpect(status().isForbidden());
		failures.error = new IllegalStateException(new BadCredentialsException(PRIVATE_VALUE));
		mvc.perform(get("/boundary/failure")).andExpect(status().isUnauthorized());
		assertThat(events.list).isEmpty();
	}

	@ParameterizedTest
	@MethodSource("requestRejections")
	void requestRejectionsRetainMvcBaselineAndPropagateTheOriginalException(Exception rejection) throws Exception {
		failures.error = rejection;
		// The old advice had no matching handler for these errors. Keep the same security filter for comparison.
		var baseline = MockMvcBuilders.standaloneSetup(failures)
				.apply(springSecurity(context.getBean("springSecurityFilterChain", Filter.class))).build();
		var baselineResult = baseline.perform(get("/boundary/failure")).andReturn();
		var actualResult = mvc.perform(get("/boundary/failure")).andReturn();
		assertThat(actualResult.getResponse().getStatus()).isEqualTo(baselineResult.getResponse().getStatus());
		assertThat(actualResult.getResponse().getContentAsString())
				.isEqualTo(baselineResult.getResponse().getContentAsString());
		assertThat(actualResult.getResolvedException()).isSameAs(baselineResult.getResolvedException());
		assertThatThrownBy(() -> new ProblemExceptionHandler().unexpected(rejection, new MockHttpServletRequest()))
				.isSameAs(rejection);
		assertThat(events.list).isEmpty();
	}

	static Stream<Arguments> requestRejections() {
		return Stream.of(
				Arguments.of(new RequestRejectedException(PRIVATE_VALUE)),
				Arguments.of(new IllegalStateException(new RequestRejectedException(PRIVATE_VALUE))),
				Arguments.of(new ServletException(new RequestRejectedException(PRIVATE_VALUE))));
	}

	@Test
	void unauthenticatedAndNonAdminRequestsNeverReachTheApplication() throws Exception {
		mvc.perform(patch("/internal/admin/problems/problem-1")).andExpect(status().isUnauthorized());
		mvc.perform(patch("/internal/admin/problems/problem-1").with(user("member")))
				.andExpect(status().isForbidden());
		org.mockito.Mockito.verifyNoInteractions(problems);
		assertThat(events.list).isEmpty();
	}

	private static MockHttpServletRequestBuilder adminPatch() {
		return patch("/internal/admin/problems/problem-1")
				.with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN")))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"slug\":\"a-plus-b\",\"visibility\":\"PUBLIC\",\"rowVersion\":1}");
	}

	private static Map<String, Object> fields(ILoggingEvent event) {
		return event.getKeyValuePairs().stream().collect(Collectors.toMap(field -> field.key, field -> field.value));
	}

	private static void assertSafeEvent(ILoggingEvent event) {
		assertThat(event.getFormattedMessage()).isEqualTo("Unexpected problem API error");
		assertThat(event.getThrowableProxy()).isNull();
		assertThat(event.getArgumentArray()).isNullOrEmpty();
		assertThat(fields(event).toString()).doesNotContain(PRIVATE_VALUE, "IllegalArgumentException", "session=");
	}

	@Configuration(proxyBeanMethods = false)
	@EnableWebMvc
	@EnableWebSecurity
	static class Config {
		@Bean
		ProblemExceptionHandler handler() {
			return new ProblemExceptionHandler();
		}

		@Bean
		AdminProblemService problems() {
			return mock(AdminProblemService.class);
		}

		@Bean
		AdminProblemController adminProblems(AdminProblemService problems) {
			return new AdminProblemController(problems, mock(ProblemPublicationService.class));
		}

		@Bean
		FailureController failures() {
			return new FailureController();
		}

		@Bean
		SecurityFilterChain security(HttpSecurity http) throws Exception {
			return http.csrf(csrf -> csrf.disable())
					.authorizeHttpRequests(requests -> requests
							.requestMatchers("/internal/admin/**").hasRole("ADMIN").anyRequest().permitAll())
					.exceptionHandling(errors -> errors
							.authenticationEntryPoint((request, response, error) -> response.setStatus(401))
							.accessDeniedHandler((request, response, error) -> response.setStatus(403)))
					.build();
		}
	}

	@RestController
	static class FailureController {
		Exception error;

		@GetMapping("/boundary/failure")
		String failure() throws Exception {
			throw error;
		}

		@PostMapping(path = "/boundary/body", consumes = MediaType.APPLICATION_JSON_VALUE,
				produces = MediaType.APPLICATION_JSON_VALUE)
		Map<String, String> body(@Valid @RequestBody Body body) {
			return Map.of("name", body.name());
		}

		@GetMapping("/boundary/query")
		int query(@RequestParam int count) {
			return count;
		}
	}

	record Body(@NotBlank String name) { }

	@ResponseStatus(HttpStatus.GONE)
	static class GoneException extends RuntimeException {
		GoneException() { }

		GoneException(Throwable cause) {
			super(cause);
		}
	}
}
