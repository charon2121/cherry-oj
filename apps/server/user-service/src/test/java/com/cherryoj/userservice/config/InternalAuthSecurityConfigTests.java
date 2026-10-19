package com.cherryoj.userservice.config;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.cherryoj.userservice.security.SigningKeys;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

class InternalAuthSecurityConfigTests {

    private static final String ISSUER = "cherry-oj-user-service";
    private static final RSAKey KEY = generate();

    @RestController
    static class StubAuthEndpoint {
        @PostMapping("/internal/auth/validate")
        ResponseEntity<String> validate() {
            return ResponseEntity.ok("{}");
        }

        @PostMapping("/internal/service-tokens")
        ResponseEntity<String> issue() {
            return ResponseEntity.ok("{}");
        }
    }

    @Configuration
    @EnableWebSecurity
    @EnableWebMvc
    @Import({InternalAuthSecurityConfig.class, SecurityConfig.class})
    static class Config {
        @Bean
        org.springframework.security.oauth2.jwt.JwtDecoder userJwtDecoder() throws Exception {
            return org.springframework.security.oauth2.jwt.NimbusJwtDecoder.withPublicKey(KEY.toRSAPublicKey())
                    .build();
        }

        @Bean
        SigningKeys signingKeys() throws Exception {
            return new SigningKeys(KEY, new JWKSet(KEY.toPublicJWK()), KEY.toRSAPublicKey(), KEY.toRSAPrivateKey());
        }

        @Bean
        AuthProperties authProperties() {
            return new AuthProperties(
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
        }

        @Bean
        StubAuthEndpoint stub() {
            return new StubAuthEndpoint();
        }
    }

    @Test
    void loginAndSessionEndpointsAcceptOnlyTheGatewayServiceToken() throws Exception {
        try (var context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(Config.class);
            context.refresh();
            var mvc = MockMvcBuilders.webAppContextSetup(context)
                    .apply(springSecurity())
                    .build();
            String path = "/internal/auth/validate";

            // 此前这些端点对任何能连到 user-service 的进程开放。
            mvc.perform(post(path)).andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("Authorization", "Bearer " + userToken()))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path)
                            .header("Authorization", "Bearer " + serviceToken("gateway-service", "problem-service")))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(path).header("Authorization", "Bearer " + serviceToken("judging-service", "user-service")))
                    .andExpect(status().isForbidden());
            mvc.perform(post(path).header("Authorization", "Bearer " + serviceToken("gateway-service", "user-service")))
                    .andExpect(status().isOk());

            // 换令牌入口带的是客户端断言：不能被主链的 Bearer 过滤器当成无效用户令牌提前 401。
            mvc.perform(post("/internal/service-tokens").header("Authorization", "Bearer not.a.user-jwt"))
                    .andExpect(status().isOk());
        }
    }

    private static String serviceToken(String caller, String audience) throws Exception {
        return sign(
                new JWTClaimsSet.Builder().subject(caller).audience(audience).claim("token_use", "service"));
    }

    private static String userToken() throws Exception {
        return sign(new JWTClaimsSet.Builder()
                .subject(UUID.randomUUID().toString())
                .audience("cherry-oj-internal")
                .claim("roles", List.of("ADMIN"))
                .claim("sv", 0)
                .claim("pwd", false));
    }

    private static String sign(JWTClaimsSet.Builder claims) throws Exception {
        Instant now = Instant.now();
        var jwt = new SignedJWT(
                new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(KEY.getKeyID()).build(),
                claims.issuer(ISSUER)
                        .issueTime(Date.from(now))
                        .expirationTime(Date.from(now.plusSeconds(900)))
                        .jwtID(UUID.randomUUID().toString())
                        .build());
        jwt.sign(new RSASSASigner(KEY));
        return jwt.serialize();
    }

    private static RSAKey generate() {
        try {
            return new RSAKeyGenerator(2048).keyID("issuer-key").generate();
        } catch (Exception error) {
            throw new ExceptionInInitializerError(error);
        }
    }
}
