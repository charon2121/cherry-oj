package com.cherryoj.problemservice.security;

import com.cherryoj.identitysecurity.IdentityVerifierConfiguration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;

@Configuration(proxyBeanMethods = false)
@Import(IdentityVerifierConfiguration.class)
class ResourceSecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, SecurityProblemWriter problems,
			JwtAuthenticationConverter identityJwtAuthenticationConverter) throws Exception {
		return http
				.addFilterBefore(new IdentityKeyFailureFilter(problems), BearerTokenAuthenticationFilter.class)
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.authorizeHttpRequests(requests -> requests
						.requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
						.requestMatchers(HttpMethod.GET, "/internal/public/problems", "/internal/public/problems/*")
								.permitAll()
						.requestMatchers("/internal/admin/**").hasRole("ADMIN")
						.anyRequest().authenticated())
				.oauth2ResourceServer(resource -> resource
						.jwt(jwt -> jwt.jwtAuthenticationConverter(identityJwtAuthenticationConverter))
						.authenticationEntryPoint(problems))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(problems)
						.accessDeniedHandler(problems))
				.httpBasic(basic -> basic.disable())
				.build();
	}
}
