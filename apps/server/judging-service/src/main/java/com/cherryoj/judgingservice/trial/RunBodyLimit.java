package com.cherryoj.judgingservice.trial;

import com.cherryoj.http.LimitedRequestBody;
import java.io.IOException;
import java.lang.reflect.Type;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.RequestBodyAdviceAdapter;

@ControllerAdvice(assignableTypes = CustomRunController.class)
public class RunBodyLimit extends RequestBodyAdviceAdapter {
    private static final int MAX_BODY_BYTES = 2 * 1024 * 1024;

    @Override
    public boolean supports(MethodParameter p, Type t, Class<? extends HttpMessageConverter<?>> c) {
        return true;
    }

    @Override
    public HttpInputMessage beforeBodyRead(
            HttpInputMessage input, MethodParameter p, Type t, Class<? extends HttpMessageConverter<?>> c)
            throws IOException {
        return LimitedRequestBody.read(
                input, MAX_BODY_BYTES, () -> new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE));
    }
}
