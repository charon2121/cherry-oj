package com.cherryoj.gatewayservice.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

class GatewayAuthPropertiesTests {

	@Test
	void usesExplicitFixedAbsolutePolicy() {
		GatewayAuthProperties properties = properties("fixed-absolute", 2_592_000,
				Duration.ofHours(2), Duration.ofMinutes(5));
		assertThat(properties.sessionAbsoluteTimeout())
				.isEqualTo(Duration.ofDays(30));
		assertThat(properties.accessTokenTtl()).isEqualTo(Duration.ofHours(2));
		assertThat(properties.tokenRefreshAhead()).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	void rejectsUnknownPolicyAndOutOfRangeLifetime() {
		assertThatThrownBy(() -> properties("sliding-idle", 2_592_000,
				Duration.ofHours(2), Duration.ofMinutes(5)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("fixed-absolute");
		assertThatThrownBy(() -> properties("fixed-absolute", 86_399,
				Duration.ofHours(2), Duration.ofMinutes(5)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("[86400, 7776000]");
		assertThatThrownBy(() -> properties("fixed-absolute", 2_592_000,
				Duration.ofMinutes(5), Duration.ofMinutes(5)))
				.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("token-refresh-ahead");
		assertThatCode(() -> new GatewayAuthProperties(
				URI.create("http://user-service.internal:8081"), Duration.ofSeconds(5),
				"fixed-absolute", 2_592_000, Duration.ofHours(2), Duration.ofMinutes(5),
				List.of("https://oj.example"), 10))
				.doesNotThrowAnyException();
	}

	@Test
	void productionProfileAlwaysMarksTheSessionCookieSecure() throws Exception {
		var sources = new YamlPropertySourceLoader().load(
				"gateway", new ClassPathResource("application.yaml"));

		assertThat(sources).anySatisfy(source -> {
			assertThat(source.getProperty("spring.config.activate.on-profile"))
					.isEqualTo("prod | production");
			assertThat(source.getProperty("server.reactive.session.cookie.secure"))
					.isEqualTo(true);
		});
	}

	private static GatewayAuthProperties properties(
			String policy, long absolute, Duration tokenTtl, Duration refreshAhead) {
		return new GatewayAuthProperties(
				URI.create("http://localhost:8081"), Duration.ofSeconds(5), policy, absolute,
				tokenTtl, refreshAhead, List.of("http://localhost:5173"), 10);
	}
}
