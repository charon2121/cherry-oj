package com.cherryoj.problemservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.problemservice.application.SubmissionSnapshotService;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** contracts/ 是跨服务字段的唯一真源：schema 里的示例必须能落进这里的 DTO，字段名不能各写各的。 */
class ContractAlignmentTests {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void problemTestDataExampleDecodesIntoTheDto() throws Exception {
        JsonNode schema = schema("problem-test-data.schema.json");

        var decoded = JSON.treeToValue(schema.get("examples").get(0), TestDataDtos.ProblemTestData.class);

        assertThat(decoded.location()).startsWith("/");
        assertThat(decoded.digest()).matches("[a-f0-9]{64}");
        assertThat(decoded.testcaseCount()).isEqualTo(2);
        assertThat(decoded.totalBytes()).isEqualTo(16);
        assertThat(fieldNames(schema.get("properties"))).isEqualTo(components(TestDataDtos.ProblemTestData.class));
        assertThat(required(schema)).isEqualTo(components(TestDataDtos.ProblemTestData.class));
    }

    @Test
    void submissionSnapshotHasExactlyTheContractFieldsAndNoVersionOrAddress() throws Exception {
        JsonNode schema = schema("problem-judge-snapshot.schema.json");

        Set<String> contract = fieldNames(schema.get("properties"));
        Set<String> dto = components(SubmissionSnapshotService.Snapshot.class);

        assertThat(dto).isEqualTo(contract);
        assertThat(contract).noneMatch(name -> name.contains("ersion") || name.startsWith("testData"));
    }

    /** problem-service 与 judging-service 各有一份标定 DTO：两份都以 calibration.schema.json 为准。 */
    @Test
    void calibrationDtosHaveExactlyTheContractFields() throws Exception {
        JsonNode definitions = schema("calibration.schema.json").get("definitions");

        assertThat(components(com.cherryoj.problemservice.integration.judging.JudgingDtos.CalibrationRequest.class))
                .isEqualTo(fieldNames(definitions.get("CalibrationRequest").get("properties")));
        assertThat(components(com.cherryoj.problemservice.integration.judging.JudgingDtos.Calibration.class))
                .isEqualTo(fieldNames(definitions.get("Calibration").get("properties")));
        assertThat(components(com.cherryoj.problemservice.integration.judging.JudgingDtos.BenchmarkSummary.class))
                .isEqualTo(fieldNames(definitions.get("BenchmarkSummary").get("properties")));
        assertThat(components(com.cherryoj.problemservice.integration.judging.JudgingDtos.Readiness.class))
                .isEqualTo(fieldNames(definitions.get("Readiness").get("properties")));
        assertThat(components(com.cherryoj.problemservice.integration.judging.JudgingDtos.ReadinessCheck.class))
                .isEqualTo(fieldNames(definitions.get("ReadinessCheck").get("properties")));
        assertThat(components(com.cherryoj.problemservice.integration.judging.JudgingDtos.ExecutionProfile.class))
                .isEqualTo(fieldNames(definitions.get("ExecutionProfile").get("properties")));
    }

    private static Set<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    private static Set<String> fieldNames(JsonNode object) {
        return object.propertyNames().stream().collect(Collectors.toSet());
    }

    private static Set<String> required(JsonNode schema) {
        return schema.get("required").valueStream().map(JsonNode::asString).collect(Collectors.toSet());
    }

    private static JsonNode schema(String name) throws Exception {
        return JSON.readTree(Files.readString(Path.of("../../../contracts").resolve(name)));
    }
}
