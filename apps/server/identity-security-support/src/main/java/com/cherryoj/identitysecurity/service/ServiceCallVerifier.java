package com.cherryoj.identitysecurity.service;

import com.cherryoj.serviceidentity.ServiceNames;
import com.cherryoj.serviceidentity.ServiceTokenClaims;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 被调方使用：只接受 user-service 签发、aud 是本服务、sub 在允许名单里的服务令牌。
 *
 * <p>允许名单写在各内部端点的安全配置里（例如只允许 judging-service），是明文服务名，评审时能直接
 * 看到"谁能调谁"。用户委托 JWT 没有 token_use=service、aud 也不是服务名，进不了这些端点；反过来，
 * 服务令牌没有 roles，也进不了面向用户的端点。
 *
 * <p>它刻意不是 JwtDecoder 类型的 bean：服务主链按类型取用户委托 JWT 的解码器，两个同类型 bean 会冲突。
 */
public final class ServiceCallVerifier {

	private static final Logger LOGGER = LoggerFactory.getLogger(ServiceCallVerifier.class);
	private static final OAuth2Error INVALID_SERVICE_TOKEN =
			new OAuth2Error("invalid_token", "Service token claims are invalid", null);
	private static final String AUTHORITY_PREFIX = "SERVICE_";

	private final JwtDecoder decoder;
	private final String self;

	public ServiceCallVerifier(JwtDecoder decoder, String self) {
		this.decoder = java.util.Objects.requireNonNull(decoder);
		this.self = ServiceNames.require(self, "service name");
	}

	public String self() {
		return self;
	}

	public JwtDecoder decoder() {
		return decoder;
	}

	/** 服务令牌的校验规则：签发方、寿命、aud 必须是本服务、token_use=service、sub 是合法服务名。 */
	public static OAuth2TokenValidator<Jwt> validator(String issuer, String self, Duration clockSkew) {
		JwtTimestampValidator timestamp = new JwtTimestampValidator(clockSkew);
		timestamp.setAllowEmptyExpiryClaim(false);
		return new DelegatingOAuth2TokenValidator<>(timestamp, new JwtIssuerValidator(issuer), jwt -> {
			boolean valid = jwt.getAudience() != null && jwt.getAudience().contains(self)
					&& ServiceTokenClaims.SERVICE.equals(jwt.getClaimAsString(ServiceTokenClaims.TOKEN_USE))
					&& ServiceNames.valid(jwt.getSubject())
					&& jwt.getIssuedAt() != null
					&& jwt.getId() != null && !jwt.getId().isBlank()
					&& jwt.getHeaders().get("kid") instanceof String kid && !kid.isBlank();
			return valid ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(INVALID_SERVICE_TOKEN);
		});
	}

	/**
	 * 把调用前 http.securityMatcher(...) 圈定的内部端点，限定为只允许 callers 这些服务调用。
	 * 未带令牌或令牌无效返回 401 SERVICE_UNAUTHORIZED；令牌有效但调用方不在名单返回 403 SERVICE_FORBIDDEN。
	 */
	public SecurityFilterChain restrictTo(HttpSecurity http, String... callers) throws Exception {
		Set<String> allowed = Set.copyOf(Arrays.stream(callers).map(caller -> ServiceNames.require(caller, "caller")).toList());
		String[] authorities = allowed.stream().map(caller -> AUTHORITY_PREFIX + caller).toArray(String[]::new);
		return http
				.csrf(csrf -> csrf.disable())
				.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
				.httpBasic(basic -> basic.disable())
				.oauth2ResourceServer(resource -> resource
						.jwt(jwt -> jwt.decoder(decoder).jwtAuthenticationConverter(token ->
								new JwtAuthenticationToken(token,
										List.of(new SimpleGrantedAuthority(AUTHORITY_PREFIX + token.getSubject())),
										token.getSubject())))
						.authenticationEntryPoint((request, response, error) -> {
							LOGGER.atWarn().addKeyValue("event", "service_call_unauthenticated")
									.addKeyValue("path", request.getRequestURI())
									.addKeyValue("reason", error.getClass().getSimpleName())
									.log("service_call_unauthenticated");
							write(response, 401, "SERVICE_UNAUTHORIZED", "Service authentication required");
						}))
				.exceptionHandling(exceptions -> exceptions.accessDeniedHandler((request, response, error) -> {
					LOGGER.atWarn().addKeyValue("event", "service_call_forbidden")
							.addKeyValue("path", request.getRequestURI())
							.addKeyValue("caller", request.getUserPrincipal() == null ? "" : request.getUserPrincipal().getName())
							.log("service_call_forbidden");
					write(response, 403, "SERVICE_FORBIDDEN", "Caller is not allowed to use this endpoint");
				}))
				.authorizeHttpRequests(requests -> requests.anyRequest().hasAnyAuthority(authorities))
				.build();
	}

	private static void write(jakarta.servlet.http.HttpServletResponse response, int status, String code, String title)
			throws java.io.IOException {
		response.setStatus(status);
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setHeader("Cache-Control", "no-store");
		response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
				+ ",\"code\":\"" + code + "\"}");
	}
}
