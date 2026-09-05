package com.cherryoj.identitysecurity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationServiceException;

class IdentityFailureClassifierTests {

	@Test
	void distinguishesSafeFailureReasonsWithoutReturningCredentials() {
		MockHttpServletRequest missing = new MockHttpServletRequest();
		assertThat(IdentityFailureClassifier.classify(missing, new RuntimeException("ignored")))
				.isEqualTo(IdentityFailureReason.MISSING_BEARER);

		MockHttpServletRequest bearer = new MockHttpServletRequest();
		bearer.addHeader("Authorization", "Bearer secret-token");
		assertThat(IdentityFailureClassifier.classify(bearer,
				new AuthenticationServiceException("Couldn't retrieve remote JWK set")))
				.isEqualTo(IdentityFailureReason.KEY_SERVICE_UNAVAILABLE);
		assertThat(IdentityFailureClassifier.classify(bearer, new RuntimeException("Jwt expired at 2026-01-01")))
				.isEqualTo(IdentityFailureReason.EXPIRED_TOKEN);
		assertThat(IdentityFailureClassifier.classify(bearer, new RuntimeException("No matching key for kid")))
				.isEqualTo(IdentityFailureReason.UNKNOWN_KEY);
		assertThat(IdentityFailureClassifier.classify(bearer, new RuntimeException("Invalid signature")))
				.isEqualTo(IdentityFailureReason.BAD_SIGNATURE);
		assertThat(IdentityFailureClassifier.classify(bearer, new RuntimeException("Malformed token")))
				.isEqualTo(IdentityFailureReason.MALFORMED_TOKEN);
		assertThat(IdentityFailureClassifier.classify(bearer, new RuntimeException("Invalid audience")))
				.isEqualTo(IdentityFailureReason.INVALID_CLAIMS);
	}
}
