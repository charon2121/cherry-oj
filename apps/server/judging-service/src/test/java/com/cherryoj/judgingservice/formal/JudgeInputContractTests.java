package com.cherryoj.judgingservice.formal;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JudgeInputContractTests {
    @Test
    void readsBothFrozenSourceFilesFromTheExistingCorePayload() throws Exception {
        var json = JsonMapper.builder().build();
        JsonNode example = json.readTree(Files.readString(Path.of("../../../contracts/judge-input.schema.json")))
                .path("examples")
                .get(1);

        JudgeInput judgeInput = json.treeToValue(example, JudgeInput.class);

        assertThat(judgeInput.completeSource())
                .isEqualTo(example.path("completeSource").asText());
        assertThat(judgeInput.sourceSha256())
                .isEqualTo(example.path("sourceSha256").asText());
        assertThat(judgeInput.solutionSource())
                .isEqualTo(example.path("solutionSource").asText());
        assertThat(judgeInput.solutionSha256())
                .isEqualTo(example.path("solutionSha256").asText());
        assertThat(judgeInput.solutionPrefixLineCount()).isEqualTo(2);
        assertThat(judgeInput.effectiveLimits().clockNs()).isNull();
    }
}
