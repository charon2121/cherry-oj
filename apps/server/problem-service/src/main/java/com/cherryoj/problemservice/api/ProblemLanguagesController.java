package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.application.ProblemLanguagesService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
public class ProblemLanguagesController {
    private final ProblemLanguagesService languages;

    public ProblemLanguagesController(ProblemLanguagesService languages) {
        this.languages = languages;
    }

    @GetMapping("/internal/public/problems/{slug}/languages")
    ResponseEntity<PublicProblemDtos.ProblemLanguages> ready(
            @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 128) String slug,
            @RequestHeader(name = "traceparent", required = false) String traceparent) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(languages.ready(slug, traceparent));
    }
}
