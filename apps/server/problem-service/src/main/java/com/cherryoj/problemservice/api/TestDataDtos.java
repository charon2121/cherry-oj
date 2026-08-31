package com.cherryoj.problemservice.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.List;

public final class TestDataDtos {

    private TestDataDtos() {
    }

    public record ManifestFile(String name, long sizeBytes, String sha256) {
    }

    public record Manifest(int caseCount, long totalBytes, List<ManifestFile> files) {
    }

    public record TestDataVersion(
            String id,
            String problemId,
            Status status,
            String sourceType,
            String contentSha256,
            Integer caseCount,
            Long totalBytes,
            Manifest manifest,
            LocalDateTime createdAt,
            LocalDateTime readyAt,
            String errorMessage) {
    }

    public record BindTestDataRequest(
            @NotBlank
            @Pattern(regexp = "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")
            String testDataVersionId,
            @Min(0) long rowVersion) {
    }

    public enum Status { UPLOADING, READY, FAILED }
}
