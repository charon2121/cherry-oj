package com.cherryoj.gatewayservice.problem;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class ProblemApiErrorsTests {

	@Test
	void distinguishesIdentityInvariantFromAuthorizationAndBusinessErrors() {
		var identity = ProblemApiErrors.map(
				new ProblemServiceClientException(HttpStatus.UNAUTHORIZED, "INVALID_TOKEN"), false);
		var forbidden = ProblemApiErrors.map(
				new ProblemServiceClientException(HttpStatus.FORBIDDEN, "FORBIDDEN"), false);
		var conflict = ProblemApiErrors.map(
				new ProblemServiceClientException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT"), false);

		assertThat(identity.status()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
		assertThat(identity.code()).isEqualTo("SERVICE_UNAVAILABLE");
		assertThat(forbidden.status()).isEqualTo(HttpStatus.FORBIDDEN);
		assertThat(forbidden.code()).isEqualTo("FORBIDDEN");
		assertThat(conflict.status()).isEqualTo(HttpStatus.CONFLICT);
		assertThat(conflict.code()).isEqualTo("ROW_VERSION_CONFLICT");
	}

	@Test
	void preservesVettedBusinessDetailButNeverLeaksUnknownUpstreamErrors() {
		var archive = ProblemApiErrors.map(new ProblemServiceClientException(
				HttpStatus.UNPROCESSABLE_ENTITY,
				"INVALID_TEST_DATA_ARCHIVE",
				"每个测试点都必须同时包含同名的 .in 和 .out 文件。"), false);
		var unknown = ProblemApiErrors.map(new ProblemServiceClientException(
				HttpStatus.UNPROCESSABLE_ENTITY, "INTERNAL_STORAGE_FAILURE", "secret storage path"), false);

		assertThat(archive.status().value()).isEqualTo(422);
		assertThat(archive.code()).isEqualTo("INVALID_TEST_DATA_ARCHIVE");
		assertThat(archive.getMessage()).isEqualTo("每个测试点都必须同时包含同名的 .in 和 .out 文件。");
		assertThat(unknown.status()).isEqualTo(HttpStatus.BAD_GATEWAY);
		assertThat(unknown.getMessage()).doesNotContain("secret storage path");
	}
    @Test
    void nodeFailuresAreActionableOnlyForAdminsAndUseFixedDetails() {
        for (String code : java.util.List.of("NO_ONLINE_JUDGE_NODE", "JUDGE_NODE_UNREACHABLE",
                "JUDGE_NODE_DATA_REJECTED", "JUDGE_NODE_RECEIPT_MISMATCH")) {
            var upstream = new ProblemServiceClientException(HttpStatus.SERVICE_UNAVAILABLE, code, "private token path");
            var admin = ProblemApiErrors.map(upstream, false);
            assertThat(admin.code()).isEqualTo(code);
            assertThat(admin.getMessage()).doesNotContain("private", "token", "path");
            assertThat(ProblemApiErrors.map(upstream, true).code()).isEqualTo("SERVICE_UNAVAILABLE");
        }
    }
}
