package com.cherryoj.judgingservice.trial;

import com.cherryoj.judgingservice.trial.CustomRunDtos.Request;
import com.cherryoj.judgingservice.trial.CustomRunDtos.Result;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CustomRunController {
    private final CustomRunService service;

    CustomRunController(CustomRunService service) {
        this.service = service;
    }

    @PostMapping("/internal/submission/trials")
    public ResponseEntity<Result> run(
            @Valid @RequestBody Request request, @RequestHeader(value = "traceparent", required = false) String trace) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.run(request, trace));
    }
}
