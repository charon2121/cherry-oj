package com.cherryoj.problemservice.security;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/** 测试用的签发方：用一把临时密钥模拟 user-service 签发服务令牌，供内部端点的鉴权测试使用。 */
final class ServiceTokenFixture {

	static final String ISSUER = "cherry-oj-user-service";
	private static final RSAKey KEY = generate();

	private ServiceTokenFixture() {
	}

	static ServiceCallVerifier verifier(String self) {
		try {
			NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey())
					.signatureAlgorithm(SignatureAlgorithm.RS256).build();
			decoder.setJwtValidator(ServiceCallVerifier.validator(ISSUER, self, Duration.ofSeconds(30)));
			return new ServiceCallVerifier(decoder, self);
		}
		catch (com.nimbusds.jose.JOSEException error) {
			throw new IllegalStateException(error);
		}
	}

	/** 合法的服务令牌：caller 调 audience。 */
	static String token(String caller, String audience) {
		return sign(caller, audience, "service", Instant.now().plus(Duration.ofMinutes(15)));
	}

	/** 结构像服务令牌但缺 token_use=service（例如把用户委托 JWT 拿来冒用）。 */
	static String withoutServiceUse(String caller, String audience) {
		return sign(caller, audience, null, Instant.now().plus(Duration.ofMinutes(15)));
	}

	static String expired(String caller, String audience) {
		return sign(caller, audience, "service", Instant.now().minus(Duration.ofMinutes(5)));
	}

	private static String sign(String caller, String audience, String tokenUse, Instant expiresAt) {
		try {
			var claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject(caller).audience(audience)
					.issueTime(Date.from(expiresAt.minus(Duration.ofMinutes(15)))).expirationTime(Date.from(expiresAt))
					.jwtID(UUID.randomUUID().toString());
			if (tokenUse != null) claims.claim("token_use", tokenUse);
			var jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(), claims.build());
			jwt.sign(new RSASSASigner(KEY));
			return jwt.serialize();
		}
		catch (com.nimbusds.jose.JOSEException error) {
			throw new IllegalStateException(error);
		}
	}

	private static RSAKey generate() {
		try {
			return new RSAKeyGenerator(2048).keyID("test-issuer-key").generate();
		}
		catch (com.nimbusds.jose.JOSEException error) {
			throw new IllegalStateException(error);
		}
	}
}
