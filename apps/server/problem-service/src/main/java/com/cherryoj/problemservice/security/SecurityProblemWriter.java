package com.cherryoj.problemservice.security;

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
		if (keyServiceUnavailable(error)) {
			log(request, "identity_key_unavailable", 503);
			write(response, 503, "IDENTITY_KEY_UNAVAILABLE", "身份密钥暂不可用");
			return;
		}
		log(request, "invalid_access_token", 401);
		write(response, 401, "INVALID_ACCESS_TOKEN", "访问令牌无效");
	}

	@Override
	public void handle(
			HttpServletRequest request, HttpServletResponse response, AccessDeniedException error)
			throws IOException {
		log(request, "access_denied", 403);
		write(response, 403, "FORBIDDEN", "当前身份无权执行此操作");
	}

	private static void log(HttpServletRequest request, String event, int status) {
		String requestId = request.getHeader(REQUEST_ID_HEADER);
		var log = LOGGER.atWarn()
				.addKeyValue("event", event)
				.addKeyValue("http_status", status);
		if (requestId != null && requestId.matches("^req_[0-9a-f]{32}$")) {
			log = log.addKeyValue("request_id", requestId);
		}
		log.log("Resource security rejected request");
	}

	private static boolean keyServiceUnavailable(Throwable error) {
		for (Throwable current = error; current != null; current = current.getCause()) {
			String name = current.getClass().getName();
			String message = current.getMessage();
			if (name.contains("ResourceAccessException") || name.contains("RemoteKeySourceException")
					|| name.contains("JwtDecoderInitializationException")
					|| message != null && (message.contains("retrieve the remote JWK set")
							|| message.contains("Couldn't retrieve remote JWK set"))) {
				return true;
			}
		}
		return false;
	}

	private static void write(HttpServletResponse response, int status, String code, String message)
			throws IOException {
		response.setStatus(status);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.getWriter().write("{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}");
	}
}
