package com.cherryoj.submissionservice.api;

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

/** contracts/ 是跨服务字段的唯一真源：冻结的判题输入与读入的题目快照，字段必须与 schema 一一对应。 */
class ContractAlignmentTests {

    @Test
    void judgeInputAndProblemSnapshotHaveExactlyTheContractFields() throws Exception {
        assertThat(components(SubmissionDtos.Input.class)).isEqualTo(properties("judge-input.schema.json"));
        assertThat(components(SubmissionDtos.Snapshot.class)).isEqualTo(properties("problem-judge-snapshot.schema.json"));
    }

    @Test
    void theCoreJudgeInputExampleDecodesIntoTheInput() throws Exception {
        JsonNode examples = schema("judge-input.schema.json").get("examples");
        var core = JsonMapper.builder().build().treeToValue(examples.get(1), SubmissionDtos.Input.class);

        assertThat(core.solutionSource()).startsWith("#include <bits/stdc++.h>");
        assertThat(core.solutionPrefixLineCount()).isEqualTo(2);
    }

    private static Set<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName).collect(Collectors.toSet());
    }

    private static Set<String> properties(String name) throws Exception {
        return schema(name).get("properties").propertyNames().stream().collect(Collectors.toSet());
    }

    private static JsonNode schema(String name) throws Exception {
        return JsonMapper.builder().build().readTree(Files.readString(Path.of("../../../contracts").resolve(name)));
    }
}
