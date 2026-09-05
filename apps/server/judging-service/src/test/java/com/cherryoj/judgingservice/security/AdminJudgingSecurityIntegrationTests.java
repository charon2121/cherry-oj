package com.cherryoj.judgingservice.security;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.judgingservice.api.JudgingDtos.Readiness;
import com.cherryoj.judgingservice.application.JudgingReadinessService;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.datasource.url=jdbc:mysql://127.0.0.1:1/not-used",
        "spring.datasource.username=none",
        "cherry.judging.recovery-enabled=false",
        "cherry.judging.testdata-root=${java.io.tmpdir}/cherry-judging-security"
})
class AdminJudgingSecurityIntegrationTests {
    private static final String USER_ID = "019c8e42-7f70-7000-8000-000000000001";
    private static final RSAKey KEY = newKey();
    private static final HttpServer JWKS = startServer();

    private final MockMvc mvc;

    @MockitoBean JudgingReadinessService service;

    @Autowired
    AdminJudgingSecurityIntegrationTests(WebApplicationContext context) {
        mvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("cherry.identity.jwks-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/jwks");
        registry.add("cherry.identity.metadata-uri",
                () -> "http://127.0.0.1:" + JWKS.getAddress().getPort() + "/metadata");
    }

    @AfterAll static void stop() { JWKS.stop(0); }

    @Test
    void readinessRejectsAnonymousAndUserButAllowsAdminWithoutCaching() throws Exception {
        String path = "/internal/admin/readiness?problemVersionId=019c8e42-7f70-7000-8000-000000000010"
                + "&testDataVersionId=019c8e42-7f70-7000-8000-000000000011"
                + "&expectedSha256=" + "0".repeat(64) + "&languageId=cpp";
        when(service.readiness(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(new Readiness(false, null, List.of(), null));

        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        mvc.perform(get(path).header("Authorization", "Bearer " + token("USER")))
                .andExpect(status().isForbidden());
        mvc.perform(get(path).header("Authorization", "Bearer " + token("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void calibrationRejectsZeroMissingAndOverflowLimitsBeforeCallingService() throws Exception {
        String prefix = """
                {"problemId":"019c8e42-7f70-7000-8000-000000000010",
                 "problemVersionId":"019c8e42-7f70-7000-8000-000000000011",
                 "testDataVersionId":"019c8e42-7f70-7000-8000-000000000012",
                 "expectedSha256":"%s","languageId":"cpp",
                """.formatted("0".repeat(64));
        String suffix = "\"memoryBytes\":268435456,\"referenceSource\":\"int main(){}\"}";
        String authorization = "Bearer " + token("ADMIN");

        mvc.perform(post("/internal/admin/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization).content(prefix + "\"cpuNs\":0," + suffix))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/admin/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization).content(prefix + suffix))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/admin/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization)
                        .content(prefix + "\"cpuNs\":1,\"clockNs\":0," + suffix))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/internal/admin/calibrations").contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", authorization)
                        .content(prefix + "\"cpuNs\":9223372036854775808," + suffix))
                .andExpect(status().isBadRequest());
    }

    private static String token(String role) {
        try {
            Instant now = Instant.now();
            JWTClaimsSet claims = new JWTClaimsSet.Builder().issuer("cherry-oj-user-service")
                    .audience("cherry-oj-internal").subject(USER_ID).issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plusSeconds(120))).jwtID("jti-" + role)
                    .claim("roles", List.of(role)).claim("sv", 0).claim("pwd", false).build();
            SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256)
                    .keyID(KEY.getKeyID()).build(), claims);
            jwt.sign(new RSASSASigner(KEY));
            return jwt.serialize();
        }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static RSAKey newKey() {
        try { return new RSAKeyGenerator(2048).keyID("judging-admin-key").generate(); }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }

    private static HttpServer startServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/jwks", exchange -> {
                byte[] body = new JWKSet(KEY.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        }
        catch (Exception error) { throw new ExceptionInInitializerError(error); }
    }
}
