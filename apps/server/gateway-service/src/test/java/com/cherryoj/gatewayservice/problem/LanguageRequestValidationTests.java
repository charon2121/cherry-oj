package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class LanguageRequestValidationTests {

    @Test
    void everyRegisteredLanguageCanReachCreationAndCalibration() throws Exception {
        var catalog =
                JsonMapper.builder().build().readTree(Files.readString(Path.of("../../../contracts/languages.json")));
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String languageId : catalog.get("languages").propertyNames()) {
                assertThat(validator.validate(create(languageId)))
                        .as(languageId)
                        .isEmpty();
                assertThat(validator.validate(calibrate(languageId)))
                        .as(languageId)
                        .isEmpty();
            }
        }
    }

    @Test
    void malformedLanguageIdsAreRejectedBeforeForwarding() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            for (String languageId : List.of("", "C", "../c", "c/../cpp", "c\n", "a".repeat(33))) {
                assertThat(validator.validate(create(languageId)))
                        .as(languageId)
                        .anyMatch(violation ->
                                violation.getPropertyPath().toString().equals("languageId"));
                assertThat(validator.validate(calibrate(languageId)))
                        .as(languageId)
                        .anyMatch(violation ->
                                violation.getPropertyPath().toString().equals("languageId"));
            }
        }
    }

    @Test
    void legacyCalibrationDefaultsToBaseButExplicitNullAndUnknownKindsAreRejected() throws Exception {
        var json = JsonMapper.builder().build();
        String body =
                "{\"languageId\":\"c\",\"cpuNs\":1,\"memoryBytes\":1,\"clockNs\":null,\"referenceSource\":\"reference\"}";
        assertThat(json.readValue(body, AdminProblemsController.CalibrateProblemRequest.class)
                        .kind())
                .isEqualTo("BASE");
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var explicitNull = json.readValue(
                    body.replace("\"languageId\"", "\"kind\":null,\"languageId\""),
                    AdminProblemsController.CalibrateProblemRequest.class);
            assertThat(factory.getValidator().validate(explicitNull))
                    .anyMatch(
                            violation -> violation.getPropertyPath().toString().equals("kind"));
            var value = json.readValue(
                    body.replace("\"languageId\"", "\"kind\":\"OTHER\",\"languageId\""),
                    AdminProblemsController.CalibrateProblemRequest.class);
            assertThat(factory.getValidator().validate(value))
                    .anyMatch(
                            violation -> violation.getPropertyPath().toString().equals("kind"));
        }
    }

    @Test
    void replacementRequiresAVersionAndRejectsNullLanguageEntries() throws Exception {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var validator = factory.getValidator();
            var withoutVersion = new AdminProblemsController.ReplaceProblemLanguagesRequest(
                    null, List.of(new AdminProblemsController.ProblemLanguageInput("c", "", null)));
            assertThat(validator.validate(withoutVersion))
                    .anyMatch(
                            violation -> violation.getPropertyPath().toString().equals("rowVersion"));
            var nullEntry = new AdminProblemsController.ReplaceProblemLanguagesRequest(
                    0L, java.util.Arrays.asList((AdminProblemsController.ProblemLanguageInput) null));
            assertThat(validator.validate(nullEntry))
                    .anyMatch(
                            violation -> violation.getPropertyPath().toString().startsWith("allowedLanguages"));
        }
        var json = JsonMapper.builder().build();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> json.readValue(
                        "{\"id\":\"c\",\"starterCode\":\"\"}", AdminProblemsController.ProblemLanguageInput.class))
                .isInstanceOf(RuntimeException.class);
        assertThat(json.readValue(
                                "{\"id\":\"c\",\"starterCode\":\"\",\"judgeTemplate\":null}",
                                AdminProblemsController.ProblemLanguageInput.class)
                        .judgeTemplate())
                .isNull();
    }

    private static AdminProblemsController.CreateProblemRequest create(String languageId) {
        return new AdminProblemsController.CreateProblemRequest("a-plus-b", "A+B", "EASY", "ACM", languageId);
    }

    private static AdminProblemsController.CalibrateProblemRequest calibrate(String languageId) {
        return new AdminProblemsController.CalibrateProblemRequest(
                languageId, 1_000_000L, 64_000_000L, null, "reference");
    }
}
