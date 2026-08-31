package com.cherryoj.problemservice.persistence;

import com.cherryoj.problemservice.api.TestDataDtos.Status;
import java.time.LocalDateTime;

public final class TestDataRows {

    private TestDataRows() {
    }

    public record TestDataRow(
            String id,
            String problemId,
            Status status,
            String storageRef,
            String contentSha256,
            Integer caseCount,
            Long totalBytes,
            String manifestJson,
            LocalDateTime createdAt,
            LocalDateTime readyAt,
            String errorMessage) {
    }
}
