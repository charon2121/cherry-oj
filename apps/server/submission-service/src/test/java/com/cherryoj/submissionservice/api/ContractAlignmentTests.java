package com.cherryoj.submissionservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.submissionservice.judging.ExecutionProfile;
import com.cherryoj.submissionservice.judging.JudgeInput;
import com.cherryoj.submissionservice.judging.ProblemJudgeSnapshot;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 提交服务使用的内部判题模型必须与 contracts/ 中的字段一一对应。 */
class ContractAlignmentTests {

    @Test
    void judgingModelsHaveExactlyTheContractFields() throws Exception {
        assertThat(components(JudgeInput.class)).isEqualTo(properties("judge-input.schema.json"));
        assertThat(components(ProblemJudgeSnapshot.class)).isEqualTo(properties("problem-judge-snapshot.schema.json"));
        assertThat(components(ExecutionProfile.class)).isEqualTo(properties("execution-profile.schema.json"));
    }

    @Test
    void theCoreJudgeInputExampleDecodesIntoTheInput() throws Exception {
        JsonNode examples = schema("judge-input.schema.json").get("examples");
        var core = JsonMapper.builder().build().treeToValue(examples.get(1), JudgeInput.class);

        assertThat(core.solutionSource()).startsWith("#include <bits/stdc++.h>");
        assertThat(core.solutionPrefixLineCount()).isEqualTo(2);
    }

    private static Set<String> components(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }

    private static Set<String> properties(String name) throws Exception {
        return schema(name).get("properties").propertyNames().stream().collect(Collectors.toSet());
    }

    private static JsonNode schema(String name) throws Exception {
        return JsonMapper.builder()
                .build()
                .readTree(Files.readString(Path.of("../../../contracts").resolve(name)));
    }
}
