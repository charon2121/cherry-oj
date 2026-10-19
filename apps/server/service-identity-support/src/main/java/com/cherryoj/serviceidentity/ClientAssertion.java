package com.cherryoj.serviceidentity;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import java.util.function.Function;

/**
 * 客户端断言（RFC 7523 的 private_key_jwt 形态）：调用方用自己的私钥签一个短期 JWT，
 * 向 user-service 证明"我是 judging-service"，换取服务令牌。iss = sub = 调用方服务名，
 * aud = 签发方（cherry.identity.issuer）。断言只在换令牌这一步使用，不发给业务服务。
 */
public final class ClientAssertion {

    private ClientAssertion() {}

    public static String sign(String caller, RSAPrivateKey key, String issuer, Instant now) {
        ServiceNames.require(caller, "caller");
        var claims = new JWTClaimsSet.Builder()
                .issuer(caller)
                .subject(caller)
                .audience(issuer)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ServiceTokenClaims.ASSERTION_LIFETIME)))
                .jwtID(UUID.randomUUID().toString())
                .build();
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(caller)
                .type(JOSEObjectType.JWT)
                .build();
        var jwt = new SignedJWT(header, claims);
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException error) {
            throw new IllegalStateException("cannot sign client assertion for " + caller, error);
        }
        return jwt.serialize();
    }

    public record Verified(String caller, String jti, Instant expiresAt) {}

    /** 校验断言：签名属于声明的调用方、aud 是本签发方、未过期且寿命不超过上限。 */
    public static Verified verify(
            String token, Function<String, RSAPublicKey> keyOf, String issuer, Instant now, Duration clockSkew) {
        SignedJWT jwt;
        JWTClaimsSet claims;
        try {
            jwt = SignedJWT.parse(token);
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException error) {
            throw new ServiceIdentityException("MALFORMED_ASSERTION", "client assertion is not a signed JWT");
        }
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new ServiceIdentityException("MALFORMED_ASSERTION", "client assertion must use RS256");
        }
        String caller = claims.getIssuer();
        if (!ServiceNames.valid(caller) || !caller.equals(claims.getSubject())) {
            throw new ServiceIdentityException(
                    "MALFORMED_ASSERTION", "client assertion iss and sub must be the same service");
        }
        RSAPublicKey key = keyOf.apply(caller);
        if (key == null) {
            throw new ServiceIdentityException("UNKNOWN_CALLER", "no public key registered for " + caller);
        }
        try {
            if (!jwt.verify(new RSASSAVerifier(key))) {
                throw new ServiceIdentityException(
                        "BAD_SIGNATURE", "client assertion signature does not match " + caller);
            }
        } catch (JOSEException error) {
            throw new ServiceIdentityException(
                    "BAD_SIGNATURE", "client assertion cannot be verified for " + caller, error);
        }
        if (claims.getAudience() == null || !claims.getAudience().contains(issuer)) {
            throw new ServiceIdentityException("WRONG_AUDIENCE", "client assertion is not addressed to " + issuer);
        }
        Date issuedAt = claims.getIssueTime();
        Date expiresAt = claims.getExpirationTime();
        String jti = claims.getJWTID();
        if (issuedAt == null || expiresAt == null || jti == null || jti.isBlank()) {
            throw new ServiceIdentityException("MALFORMED_ASSERTION", "client assertion needs iat, exp and jti");
        }
        Instant expiry = expiresAt.toInstant();
        if (!expiry.isAfter(now.minus(clockSkew))) {
            throw new ServiceIdentityException("EXPIRED_ASSERTION", "client assertion from " + caller + " has expired");
        }
        if (issuedAt.toInstant().isAfter(now.plus(clockSkew))) {
            throw new ServiceIdentityException(
                    "MALFORMED_ASSERTION", "client assertion from " + caller + " is issued in the future");
        }
        if (Duration.between(issuedAt.toInstant(), expiry).compareTo(ServiceTokenClaims.MAX_ASSERTION_LIFETIME) > 0) {
            throw new ServiceIdentityException(
                    "ASSERTION_TOO_LONG", "client assertion from " + caller + " lives longer than allowed");
        }
        return new Verified(caller, jti, expiry);
    }
}
