package com.cherryoj.problemservice.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.firewall.RequestRejectedException;
import org.springframework.validation.method.MethodValidationException;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.DisconnectedClientHelper;

@RestControllerAdvice(basePackages = "com.cherryoj.problemservice.api")
public class ProblemExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProblemExceptionHandler.class);

    @ExceptionHandler(ProblemApiException.class)
    ResponseEntity<Map<String, Object>> problem(ProblemApiException error) {
        return response(error.status().value(), error.code(), error.getMessage());
    }

    @ExceptionHandler({
            ConstraintViolationException.class,
            HandlerMethodValidationException.class,
            MethodArgumentNotValidException.class,
            MethodArgumentTypeMismatchException.class
    })
    ResponseEntity<Map<String, Object>> validation(Exception ignored) {
        return response(400, "INVALID_QUERY", "题库查询参数无效。");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<Map<String, Object>> uploadTooLarge(MaxUploadSizeExceededException ignored) {
        return response(413, "PAYLOAD_TOO_LARGE", "测试数据 ZIP 超过安全限额。");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Map<String, Object>> unexpected(Exception error, HttpServletRequest request) throws Exception {
        boolean frameworkHandled = error instanceof ErrorResponse || error instanceof TypeMismatchException
                || error instanceof HttpMessageNotReadableException || error instanceof HttpMessageNotWritableException
                || error instanceof MethodValidationException || error instanceof AsyncRequestNotUsableException;

        // Existing advice matches known causes before MVC's status resolvers or the security filter run.
        var seen = Collections.newSetFromMap(new IdentityHashMap<Throwable, Boolean>());
        Throwable cause = error;
        for (; cause != null && seen.add(cause); cause = cause.getCause()) {
            if (cause instanceof ProblemApiException problem) {
                return problem(problem);
            }
            if (cause instanceof ConstraintViolationException || cause instanceof HandlerMethodValidationException
                    || cause instanceof MethodArgumentNotValidException || cause instanceof MethodArgumentTypeMismatchException) {
                return validation((Exception) cause);
            }
            if (cause instanceof MaxUploadSizeExceededException upload) {
                return uploadTooLarge(upload);
            }
            if (cause instanceof AccessDeniedException || cause instanceof AuthenticationException
                    || cause instanceof RequestRejectedException
                    || cause instanceof ResponseStatusException
                    || AnnotatedElementUtils.findMergedAnnotation(cause.getClass(), ResponseStatus.class) != null) {
                frameworkHandled = true;
            }
        }
        // Spring's disconnect helper walks causes itself, so do not pass it a cycle.
        if (frameworkHandled || (cause == null && DisconnectedClientHelper.isClientDisconnectedException(error))) {
            throw error;
        }

        var event = LOGGER.atError().addKeyValue("event", "api.unexpected_error");
        String errorType = error.getClass().getName();
        if (errorType.length() <= 200) {
            event.addKeyValue("error_type", errorType);
        }
        String requestId = null;
        try {
            requestId = request.getHeader("X-Request-Id");
        } catch (RequestRejectedException ignored) {
            // Correlation is optional; a rejected diagnostic header must not replace the original failure.
        }
        if (requestId != null && requestId.length() == 36 && requestId.matches("req_[0-9a-f]{32}")) {
            event.addKeyValue("request_id", requestId);
        }
        event.log("Unexpected problem API error");
        return response(500, "INTERNAL_ERROR", "服务器暂时无法处理请求。");
    }

    private static ResponseEntity<Map<String, Object>> response(int status, String code, String detail) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(Map.of(
                        "title", status == 404 ? "题目不存在" : "请求无法处理",
                        "status", status,
                        "code", code,
                        "detail", detail));
    }
}
