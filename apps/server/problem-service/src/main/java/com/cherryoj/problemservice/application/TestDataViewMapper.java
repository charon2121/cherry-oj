package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.api.TestDataDtos.Manifest;
import com.cherryoj.problemservice.persistence.TestDataRows.TestDataRow;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public final class TestDataViewMapper {

    private final ObjectMapper json;

    public TestDataViewMapper(ObjectMapper json) {
        this.json = json;
    }

    public TestDataDtos.TestDataVersion map(TestDataRow row) {
        return new TestDataDtos.TestDataVersion(
                row.id(), row.problemId(), row.status(), "MANUAL_UPLOAD", row.contentSha256(), row.caseCount(),
                row.totalBytes(), manifest(row.manifestJson()), row.createdAt(), row.readyAt(), row.errorMessage());
    }

    private Manifest manifest(String value) {
        if (value == null) return null;
        try {
            return json.readValue(value, Manifest.class);
        }
        catch (Exception error) {
            throw new IllegalStateException("Test data manifest is invalid", error);
        }
    }
}
