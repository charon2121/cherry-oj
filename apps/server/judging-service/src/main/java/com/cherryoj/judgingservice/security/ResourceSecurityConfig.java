package com.cherryoj.judgingservice.security;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtIssuedAtValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
class ResourceSecurityConfig {
	private static final OAuth2Error INVALID_TOKEN =
			new OAuth2Error("invalid_token", "Required identity claims are invalid", null);

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemWriter problems) throws Exception {
		return http.addFilterBefore(new IdentityKeyFailureFilter(problems), BearerTokenAuthenticationFilter.class)
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers("/internal/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(resource -> resource
						.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
						.authenticationEntryPoint(problems))
				.exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(problems)
						.accessDeniedHandler(problems))
				.httpBasic(basic -> basic.disable()).build();
	}

	@Bean
	NimbusJwtDecoder jwtDecoder(IdentityProperties properties) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(2));
		NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri().toString())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.restOperations(new RestTemplate(requestFactory)).build();
		JwtTimestampValidator timestamp = new JwtTimestampValidator(properties.clockSkew());
		timestamp.setAllowEmptyExpiryClaim(false);
		JwtIssuedAtValidator issuedAt = new JwtIssuedAtValidator(true);
		issuedAt.setClockSkew(properties.clockSkew());
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamp, issuedAt,
				new JwtIssuerValidator(properties.issuer()), new JwtAudienceValidator(properties.audience()),
				requiredClaims()));
		return decoder;
	}

	private static JwtAuthenticationConverter jwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setPrincipalClaimName("sub");
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	private static OAuth2TokenValidator<Jwt> requiredClaims() {
		return jwt -> validSubject(jwt.getSubject()) && validRoles(jwt.getClaimAsStringList("roles"))
				&& jwt.getClaim("sv") instanceof Number
				&& jwt.getClaim("pwd") instanceof Boolean passwordChangeRequired
				&& !passwordChangeRequired && jwt.getId() != null && !jwt.getId().isBlank()
				&& jwt.getHeaders().get("kid") instanceof String kid && !kid.isBlank()
				? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
	}

	private static boolean validSubject(String subject) {
		try { UUID.fromString(subject); return true; }
		catch (RuntimeException error) { return false; }
	}

	private static boolean validRoles(List<String> roles) {
		return roles != null && !roles.isEmpty() && Set.of("USER", "ADMIN").containsAll(roles);
	}
}
