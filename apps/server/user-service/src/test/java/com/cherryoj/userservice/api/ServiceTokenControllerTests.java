package com.cherryoj.userservice.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.cherryoj.identitysecurity.service.ServiceCallVerifier;
import com.cherryoj.serviceidentity.ClientAssertion;
import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.config.ServiceClientsProperties;
import com.cherryoj.userservice.security.AssertionReplayGuard;
import com.cherryoj.userservice.security.ServiceClientRegistry;
import com.cherryoj.userservice.security.SigningKeys;
import com.cherryoj.userservice.security.TokenService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.SecurityContext;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

class ServiceTokenControllerTests {

    private static final String ISSUER = "cherry-oj-user-service";
    private static final Instant NOW = Instant.parse("2026-10-16T08:00:00Z");

    @TempDir
    Path directory;

    private KeyPair issuer;
    private KeyPair judging;
    private ServiceTokenController controller;

    @BeforeEach
    void setUp() throws Exception {
        issuer = rsa();
        judging = rsa();
        Path judgingPublic = directory.resolve("judging-service-public.pem");
        Files.writeString(
                judgingPublic,
                "-----BEGIN PUBLIC KEY-----\n"
                        + Base64.getMimeEncoder(64, "\n".getBytes())
                                .encodeToString(judging.getPublic().getEncoded())
                        + "\n-----END PUBLIC KEY-----\n");
        var rsa = new RSAKey.Builder((RSAPublicKey) issuer.getPublic())
                .privateKey((RSAPrivateKey) issuer.getPrivate())
                .keyID("issuer-key")
                .algorithm(JWSAlgorithm.RS256)
                .build();
        var keys =
                new SigningKeys(rsa, new JWKSet(rsa.toPublicJWK()), (RSAPublicKey) issuer.getPublic(), (RSAPrivateKey)
                        issuer.getPrivate());
        var properties = new AuthProperties(
                "server",
                ISSUER,
                "cherry-oj-internal",
                null,
                "unused",
                "unused",
                Map.of(),
                Duration.ofHours(2),
                Duration.ofSeconds(30),
                "fixed-absolute",
                2_592_000);
        var clock = Clock.fixed(NOW, ZoneOffset.UTC);
        var tokens = new TokenService(
                new NimbusJwtEncoder(new ImmutableJWKSet<SecurityContext>(new JWKSet(rsa))), keys, properties, clock);
        var registry = new ServiceClientRegistry(
                new ServiceClientsProperties(Map.of("judging-service", "file:" + judgingPublic)));
        controller = new ServiceTokenController(registry, new AssertionReplayGuard(clock), tokens, properties, clock);
    }

    @Test
    void exchangesASignedAssertionForAServiceTokenAddressedToTheTarget() {
        String assertion = ClientAssertion.sign("judging-service", (RSAPrivateKey) judging.getPrivate(), ISSUER, NOW);

        var response = controller.issue("Bearer " + assertion, new ServiceTokenController.Request("problem-service"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        var body = (ServiceTokenController.Response) response.getBody();
        assertThat(body.expiresAt()).isEqualTo(NOW.plus(Duration.ofMinutes(15)));
        var decoder = NimbusJwtDecoder.withPublicKey((RSAPublicKey) issuer.getPublic())
                .build();
        decoder.setJwtValidator(ServiceCallVerifier.validator(ISSUER, "problem-service", Duration.ofDays(36500)));
        var jwt = decoder.decode(body.accessToken());
        assertThat(jwt.getSubject()).isEqualTo("judging-service");
        assertThat(jwt.getAudience()).containsExactly("problem-service");
        assertThat(jwt.getClaimAsString("token_use")).isEqualTo("service");
        assertThat(jwt.getClaims()).doesNotContainKeys("roles", "sv", "pwd");
    }

    @Test
    void rejectsReplayForgeryMissingAssertionAndUnknownAudiences() throws Exception {
        String assertion = ClientAssertion.sign("judging-service", (RSAPrivateKey) judging.getPrivate(), ISSUER, NOW);
        controller.issue("Bearer " + assertion, new ServiceTokenController.Request("problem-service"));

        assertThat(controller
                        .issue("Bearer " + assertion, new ServiceTokenController.Request("problem-service"))
                        .getStatusCode())
                .as("replayed assertion")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        String forged = ClientAssertion.sign("judging-service", (RSAPrivateKey) rsa().getPrivate(), ISSUER, NOW);
        assertThat(controller
                        .issue("Bearer " + forged, new ServiceTokenController.Request("problem-service"))
                        .getStatusCode())
                .as("signed by another key")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(controller
                        .issue(null, new ServiceTokenController.Request("problem-service"))
                        .getStatusCode())
                .as("no assertion")
                .isEqualTo(HttpStatus.UNAUTHORIZED);
        for (String audience : new String[] {"gateway-service", "judging-service", "unknown-service", "../x"}) {
            String fresh = ClientAssertion.sign("judging-service", (RSAPrivateKey) judging.getPrivate(), ISSUER, NOW);
            assertThat(controller
                            .issue("Bearer " + fresh, new ServiceTokenController.Request(audience))
                            .getStatusCode())
                    .as("audience " + audience)
                    .isEqualTo(HttpStatus.BAD_REQUEST);
        }
    }

    private static KeyPair rsa() throws Exception {
        var generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        return generator.generateKeyPair();
    }
}
