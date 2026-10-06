package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.application.TestDataService;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/** judging-service 判题与标定前来取题目当前的测试数据地址，契约见 contracts/problem-test-data.schema.json。 */
@Validated
@RestController
public class JudgingTestDataController {

    private final TestDataService testData;

    public JudgingTestDataController(TestDataService testData) {
        this.testData = testData;
    }

    @GetMapping("/internal/judging/problems/{problemId}/test-data")
    ResponseEntity<TestDataDtos.ProblemTestData> current(
            @PathVariable @Pattern(regexp = AdminProblemDtos.UUID_PATTERN) String problemId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(testData.forJudging(problemId));
    }
}
