package com.cherryoj.judgingservice.limits;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.judgingservice.judge.LanguageCatalog;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 语言限制参数以 contracts/languages.json 为准：字段名和取值都必须一致。 */
class LanguageLimitModelContractTests {

    @Test
    void modelsMatchTheLanguageCatalog() throws Exception {
        JsonNode languages = JsonMapper.builder()
                .build()
                .readTree(Files.readString(Path.of("../../../contracts/languages.json")))
                .get("languages");

        var catalog = new LanguageCatalog(JsonMapper.builder().build());
        for (String languageId : languages.propertyNames()) {
            JsonNode contract = languages.get(languageId).get("limitModel");
            LimitModel model = catalog.limitModel(languageId);
            if (contract == null || contract.isNull()) {
                assertThat(model).isNull();
                continue;
            }
            assertThat(contract.propertyNames().stream().collect(Collectors.toSet()))
                    .isEqualTo(components());
            assertThat(contract.get("cpuFactorPermille").asLong()).isEqualTo(model.cpuFactorPermille());
            assertThat(
                            contract.get("cpuFactorCapPermille").isNull()
                                    ? null
                                    : contract.get("cpuFactorCapPermille").asLong())
                    .isEqualTo(model.cpuFactorCapPermille());
            assertThat(contract.get("cpuOverheadNs").asLong()).isEqualTo(model.cpuOverheadNs());
            assertThat(contract.get("ioPicosPerByte").asLong()).isEqualTo(model.ioPicosPerByte());
            assertThat(contract.get("memoryFactorPermille").asLong()).isEqualTo(model.memoryFactorPermille());
            assertThat(contract.get("memoryOverheadBytes").asLong()).isEqualTo(model.memoryOverheadBytes());
            assertThat(contract.get("baseEligible").asBoolean()).isEqualTo(model.baseEligible());
        }
    }

    private static Set<String> components() {
        return Arrays.stream(LimitModel.class.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toSet());
    }
}
