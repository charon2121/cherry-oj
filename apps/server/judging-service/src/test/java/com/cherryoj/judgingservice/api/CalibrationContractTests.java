package com.cherryoj.judgingservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** problem-service 与 judging-service 各有一份标定 DTO：两份都以 contracts/calibration.schema.json 为准。 */
class CalibrationContractTests {

    @Test
    void calibrationDtosHaveExactlyTheContractFields() throws Exception {
        JsonNode definitions = JsonMapper.builder().build()
                .readTree(Files.readString(Path.of("../../../contracts/calibration.schema.json"))).get("definitions");

        assertThat(components(JudgingDtos.CalibrationRequest.class)).isEqualTo(properties(definitions, "CalibrationRequest"));
        assertThat(components(JudgingDtos.Calibration.class)).isEqualTo(properties(definitions, "Calibration"));
        assertThat(components(JudgingDtos.BenchmarkSummary.class)).isEqualTo(properties(definitions, "BenchmarkSummary"));
        assertThat(components(JudgingDtos.Readiness.class)).isEqualTo(properties(definitions, "Readiness"));
        assertThat(components(JudgingDtos.ReadinessCheck.class)).isEqualTo(properties(definitions, "ReadinessCheck"));
        assertThat(components(JudgingDtos.ExecutionProfile.class)).isEqualTo(properties(definitions, "ExecutionProfile"));
    }

    private static Set<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    private static Set<String> properties(JsonNode definitions, String name) {
        return definitions.get(name).get("properties").propertyNames().stream().collect(Collectors.toSet());
    }
}
