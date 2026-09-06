package com.cherryoj.judgingservice.api;

import com.cherryoj.judgingservice.application.JudgeNodeRegistry;
import jakarta.validation.Valid;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/judge-nodes/v1")
public class JudgeNodeController {
    private final JudgeNodeRegistry registry;
    public JudgeNodeController(JudgeNodeRegistry registry) { this.registry = registry; }
    @PostMapping("/register")
    public JudgeNodeDtos.Lease register(@Valid @RequestBody JudgeNodeDtos.Registration request) { return registry.register(request); }
    @PostMapping("/heartbeat")
    public JudgeNodeDtos.Lease heartbeat(@Valid @RequestBody JudgeNodeDtos.Heartbeat request) { return registry.heartbeat(request); }

    // This private protocol has its own two-field Error contract, separate from
    // the administrator API's problem envelope. Never return parser details.
    @ExceptionHandler(JudgingApiException.class)
    ResponseEntity<Map<String, String>> problem(JudgingApiException error) {
        return ResponseEntity.status(error.status()).body(Map.of("code", error.code(), "detail", error.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<Map<String, String>> invalid(Exception ignored) {
        return ResponseEntity.badRequest().body(Map.of("code", "NODE_INVALID_REQUEST", "detail", "Node request invalid"));
    }
}
