package com.cherryoj.problemservice.api;

import com.cherryoj.problemservice.api.TestDataDtos.BindTestDataRequest;
import com.cherryoj.problemservice.application.TestDataService;
import com.cherryoj.problemservice.security.CurrentIdentity;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.List;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.validation.annotation.Validated;

@Validated
@RestController
@RequestMapping("/internal/admin/problems/{problemId}")
public class TestDataController {

    private final TestDataService testData;

    public TestDataController(TestDataService testData) {
        this.testData = testData;
    }

    @GetMapping("/test-data")
    ResponseEntity<List<TestDataDtos.TestDataVersion>> list(
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = UUID_PATTERN) String problemId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(testData.list(problemId));
    }

    @PostMapping(path = "/test-data", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<TestDataDtos.TestDataVersion> upload(
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = UUID_PATTERN) String problemId,
            @RequestPart("file") MultipartFile file,
            JwtAuthenticationToken authentication) {
        var created = testData.upload(problemId, file, actor(authentication));
        return ResponseEntity.status(HttpStatus.CREATED)
                .location(java.net.URI.create("/internal/admin/problems/" + problemId + "/test-data/" + created.id()))
                .cacheControl(CacheControl.noStore())
                .body(created);
    }

    @GetMapping(value = "/test-data/{testDataVersionId}/download", produces = "application/zip")
    ResponseEntity<StreamingResponseBody> download(
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = UUID_PATTERN) String problemId,
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = UUID_PATTERN) String testDataVersionId) {
        var asset = testData.openReady(problemId, testDataVersionId);
        StreamingResponseBody body = output -> {
            try (asset) {
                asset.stream().transferTo(output);
            }
            catch (IOException error) {
                throw error;
            }
        };
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + asset.id() + ".zip\"")
                .contentLength(asset.size())
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @PutMapping("/versions/{versionId}/test-data")
    ResponseEntity<AdminProblemDtos.Version> bind(
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = UUID_PATTERN) String problemId,
            @PathVariable @jakarta.validation.constraints.Pattern(regexp = UUID_PATTERN) String versionId,
            @Valid @RequestBody BindTestDataRequest request,
            JwtAuthenticationToken authentication) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(testData.bind(problemId, versionId, request, actor(authentication)));
    }

    private static String actor(JwtAuthenticationToken authentication) {
        return CurrentIdentity.from(authentication).userId();
    }

    private static final String UUID_PATTERN =
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$";
}
