package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.application.PublicProblemService;
import com.cherryoj.problemservice.application.PublicProblemService.CodeMode;
import com.cherryoj.problemservice.application.PublicProblemService.Difficulty;
import com.cherryoj.problemservice.application.PublicProblemService.Sort;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/internal/public/problems")
public class PublicProblemController {

    private final PublicProblemService problems;

    public PublicProblemController(PublicProblemService problems) {
        this.problems = problems;
    }

    @GetMapping
    PublicProblemDtos.ProblemList list(
            @RequestParam(required = false) @Size(min = 1, max = 100) String q,
            @RequestParam(required = false) Difficulty difficulty,
            @RequestParam(name = "tag", required = false) @Size(max = 10)
                    List<@Size(min = 1, max = 32) String> tags,
            @RequestParam(required = false) CodeMode codeMode,
            @RequestParam(required = false) @Pattern(regexp = "^[a-z][a-z0-9-]{0,31}$") String language,
            @RequestParam(defaultValue = "UPDATED_DESC") Sort sort,
            @RequestParam(required = false) @Size(min = 1, max = 2048) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return problems.list(q, difficulty, tags, codeMode, language, sort, cursor, size);
    }

    @GetMapping("/{slug}")
    PublicProblemDtos.ProblemDetail detail(
            @PathVariable @Pattern(regexp = "^[a-z0-9]+(?:-[a-z0-9]+)*$") @Size(max = 128) String slug) {
        return problems.detail(slug);
    }
}
