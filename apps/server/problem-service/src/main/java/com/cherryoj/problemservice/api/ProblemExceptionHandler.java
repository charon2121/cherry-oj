package com.cherryoj.problemservice.api;

import jakarta.validation.ConstraintViolationException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(basePackages = "com.cherryoj.problemservice.api")
public class ProblemExceptionHandler {

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
