package com.cherryoj.identity.token;

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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** 调用令牌的签发与校验，规则见 docs/protocols/service-identity.md §3。 */
public final class CallToken {
    /** 寿命上限：exp - iat 不超过 60 秒。调用方按上限签发。 */
    public static final Duration LIFETIME = Duration.ofSeconds(60);
    /** 校验时间时的容差。 */
    public static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    static final String USER = "user";
    private static final Set<String> USER_FIELDS = Set.of("id", "role");

    private CallToken() {}

    public record Verified(String caller, Optional<CallUser> user, String jti) {}

    public static String sign(String caller, RSAPrivateKey key, String audience, CallUser user, Instant now) {
        ServiceNames.require(caller, "caller");
        ServiceNames.require(audience, "audience");
        var claims = new JWTClaimsSet.Builder()
                .issuer(caller)
                .subject(caller)
                .audience(audience)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(LIFETIME)))
                .jwtID(UUID.randomUUID().toString());
        if (user != null) {
            claims.claim(USER, Map.of("id", user.id(), "role", user.role()));
        }
        var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .type(JOSEObjectType.JWT)
                .keyID(caller)
                .build();
        var jwt = new SignedJWT(header, claims.build());
        try {
            jwt.sign(new RSASSASigner(key));
        } catch (JOSEException error) {
            throw new IllegalStateException("cannot sign a call token for " + caller, error);
        }
        return jwt.serialize();
    }

    /** 校验 §3.2 第 1–6 项；端点策略（第 7 项）由被调方另行执行。 */
    public static Verified verify(String token, PublicKeyDirectory keys, String self, Instant now) {
        SignedJWT jwt;
        JWTClaimsSet claims;
        try {
            jwt = SignedJWT.parse(token);
            claims = jwt.getJWTClaimsSet();
        } catch (ParseException | RuntimeException error) {
            throw new CallTokenException("MALFORMED", "call token is not a signed JWT");
        }
        if (!JWSAlgorithm.RS256.equals(jwt.getHeader().getAlgorithm())) {
            throw new CallTokenException("MALFORMED", "call token must use RS256");
        }
        String caller = jwt.getHeader().getKeyID();
        RSAPublicKey key = ServiceNames.valid(caller) ? keys.key(caller) : null;
        if (key == null) {
            throw new CallTokenException("UNKNOWN_CALLER", "no public key is published for " + caller);
        }
        try {
            if (!jwt.verify(new RSASSAVerifier(key))) {
                throw new CallTokenException("BAD_SIGNATURE", "call token signature does not match " + caller);
            }
        } catch (JOSEException error) {
            throw new CallTokenException("BAD_SIGNATURE", "call token cannot be verified for " + caller);
        }
        if (!caller.equals(claims.getIssuer()) || !caller.equals(claims.getSubject())) {
            throw new CallTokenException("CALLER_MISMATCH", "call token iss and sub must equal kid " + caller);
        }
        // 协议要求 aud 是单个字符串；nimbus 解析后总给出列表，所以看原始载荷。
        if (!(jwt.getPayload().toJSONObject().get("aud") instanceof String audience) || !audience.equals(self)) {
            throw new CallTokenException(
                    "WRONG_AUDIENCE", "call token from " + caller + " is not addressed to " + self);
        }
        Date issuedAt = claims.getIssueTime();
        Date expiresAt = claims.getExpirationTime();
        if (issuedAt == null || expiresAt == null) {
            throw new CallTokenException("MALFORMED", "call token needs iat and exp");
        }
        if (now.isAfter(expiresAt.toInstant().plus(CLOCK_SKEW))) {
            throw new CallTokenException("EXPIRED", "call token from " + caller + " has expired");
        }
        if (issuedAt.toInstant().isAfter(now.plus(CLOCK_SKEW))) {
            throw new CallTokenException("NOT_YET_VALID", "call token from " + caller + " is issued in the future");
        }
        if (Duration.between(issuedAt.toInstant(), expiresAt.toInstant()).compareTo(LIFETIME) > 0) {
            throw new CallTokenException("TOO_LONG", "call token from " + caller + " lives longer than allowed");
        }
        String jti = claims.getJWTID();
        if (jti == null || jti.isBlank()) {
            throw new CallTokenException("MALFORMED", "call token needs a jti");
        }
        return new Verified(caller, user(claims.getClaim(USER), caller), jti);
    }

    private static Optional<CallUser> user(Object value, String caller) {
        if (value == null) return Optional.empty();
        if (!(value instanceof Map<?, ?> map)
                || !map.keySet().equals(USER_FIELDS)
                || !(map.get("id") instanceof String id)
                || !(map.get("role") instanceof String role)) {
            throw new CallTokenException("BAD_USER", "call token from " + caller + " has an invalid user");
        }
        try {
            return Optional.of(new CallUser(id, role));
        } catch (IllegalArgumentException error) {
            throw new CallTokenException("BAD_USER", "call token from " + caller + " has an invalid user");
        }
    }
}
