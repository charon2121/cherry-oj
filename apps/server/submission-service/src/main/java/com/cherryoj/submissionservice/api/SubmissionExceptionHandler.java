package com.cherryoj.submissionservice.api;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class SubmissionExceptionHandler {
    @ExceptionHandler(SubmissionException.class)
    ResponseEntity<?> problem(SubmissionException error) { return response(error.status().value(),error.code(),error.getMessage()); }
    @ExceptionHandler({org.springframework.web.bind.MethodArgumentNotValidException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingRequestHeaderException.class})
    ResponseEntity<?> invalid(Exception error) { return response(400,"INVALID_SUBMISSION_REQUEST","提交参数不正确。"); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unavailable(Exception error) {
        // 不记录异常 message/堆栈：SQL 参数及解析失败对象可能包含用户源码。
        org.slf4j.LoggerFactory.getLogger(getClass()).warn("submission_request_failed errorType={}",error.getClass().getSimpleName());
        return response(503,"SUBMISSION_UNAVAILABLE","提交服务暂时不可用。");
    }
    private static ResponseEntity<?> response(int status,String code,String message) {
        return ResponseEntity.status(status).header("Cache-Control","no-store").header("Content-Type","application/problem+json")
                .body(Map.of("type","about:blank","title",message,"status",status,"code",code));
    }
}
