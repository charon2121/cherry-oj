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
}
