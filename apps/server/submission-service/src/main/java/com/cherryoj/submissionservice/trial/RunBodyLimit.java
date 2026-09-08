package com.cherryoj.submissionservice.trial;

import java.io.*;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice(assignableTypes = CustomRunController.class)
public class RunBodyLimit extends RequestBodyAdviceAdapter {

	public boolean supports(MethodParameter p, Type t, Class<? extends HttpMessageConverter<?>> c) {
		return true;
	}

	public HttpInputMessage beforeBodyRead(HttpInputMessage input, MethodParameter p, Type t,
			Class<? extends HttpMessageConverter<?>> c) throws IOException {
		byte[] bytes = input.getBody().readNBytes(2 * 1024 * 1024 + 1);
		if (bytes.length > 2 * 1024 * 1024)
			throw new org.springframework.web.server.ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
		return new HttpInputMessage() {
			public InputStream getBody() {
				return new ByteArrayInputStream(bytes);
			}

			public HttpHeaders getHeaders() {
				return input.getHeaders();
			}
		};
	}

}
