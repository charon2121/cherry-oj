package com.cherryoj.gatewayservice.auth;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.security.web.server.csrf.WebSessionServerCsrfTokenRepository;
import org.springframework.security.web.server.savedrequest.NoOpServerRequestCache;
import org.springframework.session.config.ReactiveSessionRepositoryCustomizer;
import org.springframework.session.data.redis.ReactiveRedisSessionRepository;
import org.springframework.session.data.redis.config.annotation.web.server.EnableRedisWebSession;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;

@Configuration(proxyBeanMethods = false)
@EnableRedisWebSession(redisNamespace = "cherry:gateway:sessions")
class SecurityConfig {

	@Bean
	ReactiveSessionRepositoryCustomizer<ReactiveRedisSessionRepository> sessionRepositoryCustomizer(
			GatewayAuthProperties properties) {
		return repository -> repository.setDefaultMaxInactiveInterval(properties.sessionAbsoluteTimeout());
	}

	@Bean
	WebSessionServerCsrfTokenRepository csrfTokenRepository() {
		WebSessionServerCsrfTokenRepository repository = new WebSessionServerCsrfTokenRepository();
		repository.setHeaderName("X-CSRF-Token");
		return repository;
	}

	@Bean
	SecurityWebFilterChain securityWebFilterChain(
			ServerHttpSecurity http,
			WebSessionServerCsrfTokenRepository csrfTokenRepository,
			CsrfProblemWriter csrfProblemWriter) {
		return http
				.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository)
						.accessDeniedHandler(csrfProblemWriter))
				.cors(cors -> { })
				.httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
				.formLogin(ServerHttpSecurity.FormLoginSpec::disable)
				.logout(ServerHttpSecurity.LogoutSpec::disable)
				.securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
				.requestCache(cache -> cache.requestCache(NoOpServerRequestCache.getInstance()))
				.authorizeExchange(exchanges -> exchanges.anyExchange().permitAll())
				.build();
	}

	@Bean
	CorsConfigurationSource corsConfigurationSource(GatewayAuthProperties properties) {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOrigins(properties.trustedOrigins());
		configuration.setAllowedMethods(List.of(
				HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(), HttpMethod.PATCH.name(),
				HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
		configuration.setAllowedHeaders(List.of(HttpHeaders.CONTENT_TYPE, "X-CSRF-Token", "X-Request-Id"));
		configuration.setExposedHeaders(List.of(
				"X-Request-Id", HttpHeaders.LOCATION, HttpHeaders.CONTENT_DISPOSITION,
				HttpHeaders.CONTENT_LENGTH, HttpHeaders.CACHE_CONTROL));
		configuration.setAllowCredentials(true);
		configuration.setMaxAge(600L);
		return exchange -> configuration;
	}
}
