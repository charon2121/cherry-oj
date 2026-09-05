package com.cherryoj.identitysecurity;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;

import org.junit.jupiter.api.Test;

class IdentityVerifierPropertiesTests {

	@Test
	void rejectsUnsafeOrIncompleteVerifierConfiguration() {
		assertThatThrownBy(() -> new IdentityVerifierProperties(
				"issuer", "audience", URI.create("file:///tmp/jwks"), URI.create("https://identity.example/metadata"),
				Duration.ofHours(2), Duration.ZERO,
				Duration.ofSeconds(2), Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IdentityVerifierProperties(
				"issuer", "audience", URI.create("https://identity.example/jwks"), URI.create("file:///tmp/metadata"),
				Duration.ofHours(2), Duration.ZERO,
				Duration.ofSeconds(2), Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IdentityVerifierProperties(
				"issuer", "audience", URI.create("https://identity.example/jwks"), URI.create("https://identity.example/metadata"),
				Duration.ofHours(2), Duration.ofSeconds(61),
				Duration.ofSeconds(2), Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new IdentityVerifierProperties(
				"issuer", "audience", URI.create("https://identity.example/jwks"), URI.create("https://identity.example/metadata"),
				Duration.ofHours(2), Duration.ZERO,
				Duration.ofSeconds(30), Duration.ofSeconds(2)))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatCode(() -> new IdentityVerifierProperties(
				"issuer", "audience", URI.create("http://identity.example/jwks"),
				URI.create("http://identity.example/metadata"), Duration.ofHours(2), Duration.ZERO,
				Duration.ofSeconds(2), Duration.ofSeconds(2)))
				.doesNotThrowAnyException();
		assertThatCode(() -> new IdentityVerifierProperties(
				"issuer", "audience", URI.create("https://identity.example/jwks"),
				URI.create("https://metadata.example/metadata"), Duration.ofHours(2), Duration.ZERO,
				Duration.ofSeconds(2), Duration.ofSeconds(2)))
				.doesNotThrowAnyException();
	}
}
