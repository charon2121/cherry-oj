package com.cherryoj.submissionservice.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
final class SecurityProblemWriter implements AuthenticationEntryPoint, AccessDeniedHandler {
	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException error)
			throws IOException {
		if (keyServiceUnavailable(error)) {
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
