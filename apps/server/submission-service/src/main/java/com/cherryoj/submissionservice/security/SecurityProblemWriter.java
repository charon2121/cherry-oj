package com.cherryoj.submissionservice.security;

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

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
			throws IOException {
		IdentityFailureReason reason = IdentityFailureClassifier.classify(request, error);
		log(request, reason, reason == IdentityFailureReason.KEY_SERVICE_UNAVAILABLE ? 503 : 401);
		if (reason == IdentityFailureReason.KEY_SERVICE_UNAVAILABLE) {
			write(response, 503, "IDENTITY_KEY_UNAVAILABLE", "身份密钥暂不可用");
			return;
		}
		write(response, 401, "INVALID_ACCESS_TOKEN", "访问令牌无效");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
			throws IOException {
		write(response, 403, "FORBIDDEN", "当前身份无权执行此操作");
	}

	private static void log(HttpServletRequest request, IdentityFailureReason reason, int status) {
		var event = LOGGER.atWarn()
				.addKeyValue("event", "identity_authentication_failed")
				.addKeyValue("identity_failure", reason.name().toLowerCase())
				.addKeyValue("http_status", status);
		String requestId = request.getHeader("X-Request-Id");
		if (requestId != null && requestId.matches("^req_[0-9a-f]{32}$")) {
			event = event.addKeyValue("request_id", requestId);
		}
		event.log("Resource security rejected request");
	}

	private static void write(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
