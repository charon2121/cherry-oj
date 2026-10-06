package com.cherryoj.problemservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.problemservice.application.SubmissionSnapshotService;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
        assertThat(decoded.caseCount()).isEqualTo(2);
        assertThat(decoded.totalBytes()).isEqualTo(16);
        assertThat(fieldNames(schema.get("properties"))).isEqualTo(components(TestDataDtos.ProblemTestData.class));
        assertThat(required(schema)).isEqualTo(components(TestDataDtos.ProblemTestData.class));
    }

    @Test
    void submissionSnapshotHasExactlyTheContractFieldsAndNoVersionOrAddress() throws Exception {
        JsonNode schema = schema("problem-judge-snapshot.schema.json");

        Set<String> contract = fieldNames(schema.get("properties"));
        Set<String> dto = components(SubmissionSnapshotService.Snapshot.class);

        // 契约比 DTO 多且只多一个 judgeTemplate：它只在 CORE 题目返回，目前只支持 ACM，DTO 还没有它
        assertThat(contract).containsAll(dto);
        assertThat(contract).containsExactlyInAnyOrderElementsOf(
                Stream.concat(dto.stream(), Stream.of("judgeTemplate")).collect(Collectors.toSet()));
        assertThat(contract).noneMatch(name -> name.contains("ersion") || name.startsWith("testData"));
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
