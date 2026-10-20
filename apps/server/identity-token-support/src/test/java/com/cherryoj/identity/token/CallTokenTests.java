package com.cherryoj.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

class CallTokenTests {
    private static final KeyPair GATEWAY = rsa();
    private static final KeyPair OTHER = rsa();
    private static final PublicKeyDirectory KEYS = PublicKeyDirectory.of(Map.of(
            ServiceNames.GATEWAY, (RSAPublicKey) GATEWAY.getPublic(),
            ServiceNames.JUDGING, (RSAPublicKey) OTHER.getPublic()));
    private static final Instant NOW = Instant.parse("2026-10-20T08:00:00Z");
    private static final CallUser ALICE = new CallUser("d0e35399-6487-4ac8-8138-8d5bd60eb003", "USER");

    @Test
    void aSignedTokenVerifiesWithAndWithoutUser() {
        var withUser = CallToken.verify(sign(ALICE), KEYS, ServiceNames.SUBMISSION, NOW);
        assertThat(withUser.caller()).isEqualTo(ServiceNames.GATEWAY);
        assertThat(withUser.user()).contains(ALICE);
        assertThat(withUser.jti()).isNotBlank();

        var withoutUser = CallToken.verify(sign(null), KEYS, ServiceNames.SUBMISSION, NOW);
        assertThat(withoutUser.user()).isEmpty();
    }

    /** 协议 §3.1：aud 是单个字符串，user 只有 id 与 role，exp = iat + 60。 */
    @Test
    void theWireFormatMatchesTheProtocol() throws Exception {
        String token = sign(ALICE);
        String[] parts = token.split("\\.");
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
        String payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        assertThat(header).contains("\"alg\":\"RS256\"", "\"typ\":\"JWT\"", "\"kid\":\"gateway-service\"");
        assertThat(payload).contains("\"aud\":\"submission-service\"").doesNotContain("\"aud\":[");
        var claims = SignedJWT.parse(token).getJWTClaimsSet();
        assertThat(claims.getJSONObjectClaim("user")).containsOnlyKeys("id", "role");
        assertThat(claims.getExpirationTime().getTime() - claims.getIssueTime().getTime())
                .isEqualTo(60_000);
    }

    @Test
    void everyRuleInSectionThreeTwoRejects() {
        rejects(
                "UNKNOWN_CALLER",
                custom(
                        GATEWAY,
                        "problem-service",
                        c -> c.issuer("problem-service").subject("problem-service")));
        rejects("BAD_SIGNATURE", custom(OTHER, ServiceNames.GATEWAY, c -> {}));
        rejects("CALLER_MISMATCH", custom(GATEWAY, ServiceNames.GATEWAY, c -> c.subject("judging-service")));
        rejects("CALLER_MISMATCH", custom(GATEWAY, ServiceNames.GATEWAY, c -> c.issuer("judging-service")));
        rejects("WRONG_AUDIENCE", custom(GATEWAY, ServiceNames.GATEWAY, c -> c.audience("problem-service")));
        rejects(
                "WRONG_AUDIENCE",
                custom(GATEWAY, ServiceNames.GATEWAY, c -> c.audience(List.of("submission-service", "x-service"))));
        rejects(
                "EXPIRED",
                custom(GATEWAY, ServiceNames.GATEWAY, c -> c.issueTime(at(-120)).expirationTime(at(-61))));
        rejects(
                "NOT_YET_VALID",
                custom(GATEWAY, ServiceNames.GATEWAY, c -> c.issueTime(at(31)).expirationTime(at(60))));
        rejects("TOO_LONG", custom(GATEWAY, ServiceNames.GATEWAY, c -> c.expirationTime(at(61))));
        rejects("MALFORMED", custom(GATEWAY, ServiceNames.GATEWAY, c -> c.jwtID(null)));
        rejects("MALFORMED", custom(GATEWAY, ServiceNames.GATEWAY, c -> c.issueTime(null)));
        rejects(
                "BAD_USER",
                custom(GATEWAY, ServiceNames.GATEWAY, c -> c.claim("user", Map.of("id", ALICE.id(), "role", "ROOT"))));
        rejects(
                "BAD_USER",
                custom(
                        GATEWAY,
                        ServiceNames.GATEWAY,
                        c -> c.claim("user", Map.of("id", "not-a-uuid", "role", "USER"))));
        rejects(
                "BAD_USER",
                custom(
                        GATEWAY,
                        ServiceNames.GATEWAY,
                        c -> c.claim("user", Map.of("id", ALICE.id(), "role", "USER", "name", "alice"))));
        rejects("MALFORMED", "not-a-jwt");
    }

    @Test
    void theClockSkewIsThirtySecondsAndUnknownTopLevelClaimsAreIgnored() {
        var stale =
                custom(GATEWAY, ServiceNames.GATEWAY, c -> c.issueTime(at(-89)).expirationTime(at(-29)));
        assertThat(CallToken.verify(stale, KEYS, ServiceNames.SUBMISSION, NOW).caller())
                .isEqualTo(ServiceNames.GATEWAY);
        var early =
                custom(GATEWAY, ServiceNames.GATEWAY, c -> c.issueTime(at(30)).expirationTime(at(90)));
        assertThat(CallToken.verify(early, KEYS, ServiceNames.SUBMISSION, NOW).caller())
                .isEqualTo(ServiceNames.GATEWAY);
        var extra = custom(GATEWAY, ServiceNames.GATEWAY, c -> c.claim("traceHint", "abc"));
        assertThat(CallToken.verify(extra, KEYS, ServiceNames.SUBMISSION, NOW).user())
                .isEmpty();
    }

    private static void rejects(String reason, String token) {
        assertThatThrownBy(() -> CallToken.verify(token, KEYS, ServiceNames.SUBMISSION, NOW))
                .as(reason)
                .isInstanceOfSatisfying(
                        CallTokenException.class,
                        error -> assertThat(error.reason()).isEqualTo(reason));
    }

    private static String sign(CallUser user) {
        return CallToken.sign(
                ServiceNames.GATEWAY, (RSAPrivateKey) GATEWAY.getPrivate(), ServiceNames.SUBMISSION, user, NOW);
    }

    /** 从一个合法令牌出发改动若干声明后，用 signer 的私钥重签；kid 取 kid 参数。 */
    private static String custom(KeyPair signer, String kid, Consumer<JWTClaimsSet.Builder> change) {
        var claims = new JWTClaimsSet.Builder()
                .issuer(ServiceNames.GATEWAY)
                .subject(ServiceNames.GATEWAY)
                .audience(ServiceNames.SUBMISSION)
                .issueTime(at(0))
                .expirationTime(at(60))
                .jwtID("5b1e9c2a-0f8e-4b7d-9a51-3c2f4e6d8a10");
        change.accept(claims);
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256)
                        .type(JOSEObjectType.JWT)
                        .keyID(kid)
                        .build(),
                claims.build());
        try {
            jwt.sign(new RSASSASigner(signer.getPrivate()));
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
        return jwt.serialize();
    }

    private static Date at(long secondsFromNow) {
        return Date.from(NOW.plusSeconds(secondsFromNow));
    }

    static KeyPair rsa() {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            return generator.generateKeyPair();
        } catch (Exception error) {
            throw new IllegalStateException(error);
        }
    }
}
