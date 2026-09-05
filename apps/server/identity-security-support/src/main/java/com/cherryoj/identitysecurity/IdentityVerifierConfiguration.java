package com.cherryoj.identitysecurity;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtAudienceValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuedAtValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.web.client.RestTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityVerifierProperties.class)
public class IdentityVerifierConfiguration {

	private static final OAuth2Error INVALID_TOKEN =
			new OAuth2Error("invalid_token", "Required identity claims are invalid", null);

	@Bean
	public IdentityVerifierHealth identityVerifierHealth(IdentityVerifierProperties properties) {
		return new IdentityVerifierHealth(properties);
	}

	@Bean
	public JwtDecoder identityJwtDecoder(IdentityVerifierProperties properties, IdentityVerifierHealth health) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(properties.connectTimeout());
		requestFactory.setReadTimeout(properties.readTimeout());
		NimbusJwtDecoder delegate = NimbusJwtDecoder.withJwkSetUri(properties.jwksUri().toString())
				.jwsAlgorithm(SignatureAlgorithm.RS256)
				.restOperations(new RestTemplate(requestFactory))
				.build();
		JwtTimestampValidator timestamp = new JwtTimestampValidator(properties.clockSkew());
		timestamp.setAllowEmptyExpiryClaim(false);
		JwtIssuedAtValidator issuedAt = new JwtIssuedAtValidator(true);
		issuedAt.setClockSkew(properties.clockSkew());
		delegate.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				timestamp,
				issuedAt,
				new JwtIssuerValidator(properties.issuer()),
				new JwtAudienceValidator(properties.audience()),
				requiredClaims()));
		return token -> {
			try {
				Jwt jwt = delegate.decode(token);
				health.verified();
				return jwt;
			}
			catch (JwtException error) {
				if (IdentityFailureClassifier.classify(null, error)
						== IdentityFailureReason.KEY_SERVICE_UNAVAILABLE) {
					health.keyServiceUnavailable();
				}
				throw error;
			}
		};
	}

	@Bean
	public JwtAuthenticationConverter identityJwtAuthenticationConverter() {
		JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
		authorities.setAuthoritiesClaimName("roles");
		authorities.setAuthorityPrefix("ROLE_");
		JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
		converter.setPrincipalClaimName("sub");
		converter.setJwtGrantedAuthoritiesConverter(authorities);
		return converter;
	}

	private static OAuth2TokenValidator<Jwt> requiredClaims() {
		return jwt -> validSubject(jwt.getSubject())
				&& validRoles(jwt.getClaimAsStringList("roles"))
				&& jwt.getClaim("sv") instanceof Number
				&& jwt.getClaim("pwd") instanceof Boolean passwordChangeRequired
				&& !passwordChangeRequired
				&& jwt.getId() != null && !jwt.getId().isBlank()
				&& jwt.getHeaders().get("kid") instanceof String kid && !kid.isBlank()
				? OAuth2TokenValidatorResult.success()
				: OAuth2TokenValidatorResult.failure(INVALID_TOKEN);
	}

	private static boolean validSubject(String subject) {
		try {
			UUID.fromString(subject);
			return true;
		}
		catch (RuntimeException error) {
			return false;
		}
	}

	private static boolean validRoles(List<String> roles) {
		return roles != null && !roles.isEmpty() && Set.of("USER", "ADMIN").containsAll(roles);
	}
}
