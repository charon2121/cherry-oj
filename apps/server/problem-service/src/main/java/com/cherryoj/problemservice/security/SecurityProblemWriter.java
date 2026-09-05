package com.cherryoj.problemservice.security;

import com.cherryoj.identitysecurity.IdentityFailureClassifier;
import com.cherryoj.identitysecurity.IdentityFailureReason;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
final class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final Logger LOGGER = LoggerFactory.getLogger(SecurityProblemWriter.class);
	private static final String REQUEST_ID_HEADER = "X-Request-Id";

	@Override
	public void commence(
			HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
			throws IOException {
		IdentityFailureReason reason = IdentityFailureClassifier.classify(request, error);
		if (reason == IdentityFailureReason.KEY_SERVICE_UNAVAILABLE) {
			log(request, "identity_authentication_failed", reason, 503);
			write(response, 503, "IDENTITY_KEY_UNAVAILABLE", "身份密钥暂不可用");
			return;
		}
		log(request, "identity_authentication_failed", reason, 401);
		write(response, 401, "INVALID_ACCESS_TOKEN", "访问令牌无效");
	}

	@Override
	public void handle(
			HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
			throws IOException {
		log(request, "access_denied", null, 403);
		write(response, 403, "FORBIDDEN", "当前身份无权执行此操作");
	}

	private static void log(HttpServletRequest request, String event, IdentityFailureReason reason, int status) {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		var log = LOGGER.atWarn()
				.addKeyValue("event", event)
				.addKeyValue("http_status", status);
		if (reason != null) {
			log = log.addKeyValue("identity_failure", reason.name().toLowerCase());
		}
		if (requestId != null && requestId.matches("^req_[0-9a-f]{32}$")) {
			log = log.addKeyValue("request_id", requestId);
		}
		log.log("Resource security rejected request");
	}

	private static void write(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
