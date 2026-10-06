package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.application.TestDataService;
import com.cherryoj.problemservice.security.CurrentIdentity;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/internal/admin/problems/{problemId}/test-data")
public class TestDataController {

    private static final String ID = AdminProblemDtos.UUID_PATTERN;

    private final TestDataService testData;

    public TestDataController(TestDataService testData) {
        this.testData = testData;
    }

    @GetMapping
    ResponseEntity<TestDataDtos.TestData> get(@PathVariable @Pattern(regexp = ID) String problemId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(testData.get(problemId));
    }

    /** 上传即替换：题目只有一份测试数据。 */
    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<TestDataDtos.TestData> upload(
            @PathVariable @Pattern(regexp = ID) String problemId,
            @RequestPart("file") MultipartFile file,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(testData.upload(problemId, file, CurrentIdentity.from(authentication).userId()));
    }
}
