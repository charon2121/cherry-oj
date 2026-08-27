package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class GatewayAuthPropertiesTests {

	@Test
	void convertsValidatedSecondsAndStrictBoolean() {
		GatewayAuthProperties properties = properties(7_200, 86_400, "false");

		assertThat(properties.sessionIdleTimeout()).isEqualTo(Duration.ofSeconds(7_200));
		assertThat(properties.sessionAbsoluteTimeout()).isEqualTo(Duration.ofSeconds(86_400));
		assertThat(properties.refreshIdleOnActivity()).isFalse();
	}

	@Test
	void rejectsNonBooleanAndInvalidDeadlineOrder() {
		assertThatThrownBy(() -> properties(1_800, 43_200, "yes"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("session-refresh-idle-on-activity");
		assertThatThrownBy(() -> properties(7_200, 3_600, "true"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("must not exceed");
		assertThatThrownBy(() -> properties(299, 3_600, "true"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("[300, 7200]");
		assertThatThrownBy(() -> properties(1_800, 604_801, "true"))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("[3600, 604800]");
	}

	private static GatewayAuthProperties properties(long idle, long absolute, String refresh) {
		return new GatewayAuthProperties(
				URI.create("http://localhost:8081"), Duration.ofSeconds(5), idle, absolute, refresh,
				Duration.ofSeconds(15), List.of("http://localhost:5173"), 10);
	}
}
