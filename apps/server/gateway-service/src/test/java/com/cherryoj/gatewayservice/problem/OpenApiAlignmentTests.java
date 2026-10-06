package com.cherryoj.gatewayservice.problem;

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

/** contracts/web-api.openapi.json 是对浏览器的唯一真源：网关 DTO 的字段必须与它逐一对应。 */
class OpenApiAlignmentTests {

	private static final JsonNode SCHEMAS = schemas();

	@Test
	void adminAndPublicProblemModelsMatchTheContract() {
		assertAligned("AdminProblem", ProblemDtos.AdminProblem.class);
		assertAligned("AdminProblemSummary", ProblemDtos.AdminProblemSummary.class);
		assertAligned("TestData", ProblemDtos.TestData.class);
		assertAligned("LanguageCalibration", ProblemDtos.LanguageCalibration.class);
		assertAligned("ProblemSummary", ProblemDtos.ProblemSummary.class);
		assertAligned("ProblemDetail", ProblemDtos.ProblemDetail.class);
	}

	@Test
	void requestsMatchTheContract() {
		assertAligned("UpdateProblemRequest", AdminProblemsController.UpdateProblemRequest.class);
		assertAligned("CalibrateProblemRequest", AdminProblemsController.CalibrateProblemRequest.class);
		assertAligned("RowVersionRequest", AdminProblemsController.RowVersionRequest.class);
	}

	@Test
	void noVersionConceptsRemainInTheBrowserContract() {
		String text = SCHEMAS.toString();
		for (String removed : new String[] {"problemVersionId", "problemVersionNo", "currentVersionId",
				"expectedProblemVersionId", "testDataVersionId", "versionNo", "DeployTestData"}) {
			assertThat(text).doesNotContain(removed);
		}
	}

	private static void assertAligned(String schema, Class<? extends Record> type) {
		JsonNode definition = SCHEMAS.get(schema);
		Set<String> contract = definition.get("properties").propertyNames().stream().collect(Collectors.toSet());
		Set<String> dto = Arrays.stream(type.getRecordComponents()).map(RecordComponent::getName)
				.collect(Collectors.toSet());
		assertThat(dto).as(schema).isEqualTo(contract);
	}

	private static JsonNode schemas() {
		try {
			return JsonMapper.builder().build()
					.readTree(Files.readString(Path.of("../../../contracts/web-api.openapi.json")))
					.get("components").get("schemas");
		}
		catch (java.io.IOException error) {
			throw new IllegalStateException(error);
		}
	}
}
