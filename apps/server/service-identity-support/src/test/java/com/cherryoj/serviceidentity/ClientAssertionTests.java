package com.cherryoj.serviceidentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClientAssertionTests {

    private static final String ISSUER = "cherry-oj-user-service";
    private static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");
    private static final Duration SKEW = Duration.ofSeconds(30);
    private static final java.security.KeyPair JUDGING = TestKeys.rsa(2048);
    private static final java.security.KeyPair OTHER = TestKeys.rsa(2048);

    private static RSAPublicKey judgingKey(String caller) {
        return Map.of("judging-service", (RSAPublicKey) JUDGING.getPublic()).get(caller);
    }

    @Test
    void verifiesTheCallerItWasSignedFor() {
        String token = ClientAssertion.sign("judging-service", (RSAPrivateKey) JUDGING.getPrivate(), ISSUER, NOW);

        var verified =
                ClientAssertion.verify(token, ClientAssertionTests::judgingKey, ISSUER, NOW.plusSeconds(10), SKEW);

        assertThat(verified.caller()).isEqualTo("judging-service");
        assertThat(verified.jti()).isNotBlank();
        assertThat(verified.expiresAt()).isEqualTo(NOW.plus(ServiceTokenClaims.ASSERTION_LIFETIME));
    }

    @Test
    void rejectsAnotherServicesKeyAnUnknownCallerTheWrongIssuerAndExpiry() {
        String forged = ClientAssertion.sign("judging-service", (RSAPrivateKey) OTHER.getPrivate(), ISSUER, NOW);
        String unknown = ClientAssertion.sign("rogue-service", (RSAPrivateKey) OTHER.getPrivate(), ISSUER, NOW);
        String elsewhere =
                ClientAssertion.sign("judging-service", (RSAPrivateKey) JUDGING.getPrivate(), "someone-else", NOW);
        String valid = ClientAssertion.sign("judging-service", (RSAPrivateKey) JUDGING.getPrivate(), ISSUER, NOW);

        assertReason(forged, NOW, "BAD_SIGNATURE");
        assertReason(unknown, NOW, "UNKNOWN_CALLER");
        assertReason(elsewhere, NOW, "WRONG_AUDIENCE");
        assertReason(valid, NOW.plus(Duration.ofMinutes(5)), "EXPIRED_ASSERTION");
        assertReason("not-a-jwt", NOW, "MALFORMED_ASSERTION");
    }

    @Test
    void rejectsLongLivedAssertionsAndMismatchedIssuerSubject() throws Exception {
        assertReason(
                custom("judging-service", "judging-service", NOW, NOW.plus(Duration.ofHours(1))),
                NOW,
                "ASSERTION_TOO_LONG");
        assertReason(
                custom("judging-service", "problem-service", NOW, NOW.plusSeconds(60)), NOW, "MALFORMED_ASSERTION");
    }

    private static String custom(String issuer, String subject, Instant issuedAt, Instant expiresAt) throws Exception {
        var claims = new JWTClaimsSet.Builder()
                .issuer(issuer)
                .subject(subject)
                .audience(ISSUER)
                .issueTime(Date.from(issuedAt))
                .expirationTime(Date.from(expiresAt))
                .jwtID("j-1")
                .build();
        var jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.RS256), claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) JUDGING.getPrivate()));
        return jwt.serialize();
    }

    private static void assertReason(String token, Instant now, String reason) {
        assertThatThrownBy(() -> ClientAssertion.verify(token, ClientAssertionTests::judgingKey, ISSUER, now, SKEW))
                .isInstanceOfSatisfying(
                        ServiceIdentityException.class,
                        error -> assertThat(error.reason()).isEqualTo(reason));
    }
}
