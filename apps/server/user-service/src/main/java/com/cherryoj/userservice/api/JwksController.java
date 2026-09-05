package com.cherryoj.userservice.api;

import com.cherryoj.userservice.security.SigningKeys;
import com.cherryoj.userservice.config.AuthProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class JwksController {

    private final SigningKeys keys;
    private final AuthProperties properties;

    public JwksController(SigningKeys keys, AuthProperties properties) {
        this.keys = keys;
        this.properties = properties;
    }

    @GetMapping("/.well-known/jwks.json")
    ResponseEntity<Map<String, Object>> jwks() {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .eTag(etag())
                .body(keys.publicJwkSet().toJSONObject());
    }

    @GetMapping("/internal/identity/metadata")
    ResponseEntity<Map<String, Object>> metadata() {
        List<String> publishedKids = keys.publicJwkSet().getKeys().stream()
                .map(key -> key.getKeyID())
                .sorted()
                .toList();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Map.of(
                        "activeKid", keys.activeKid(),
                        "publishedKids", publishedKids,
                        "algorithm", "RS256",
                        "accessTokenTtlSeconds", properties.accessTokenTtl().toSeconds(),
                        "generation", etag().replace("\"", "")));
    }

    private String etag() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                    keys.publicJwkSet().toString().getBytes(StandardCharsets.UTF_8));
            return "\"" + Base64.getUrlEncoder().withoutPadding().encodeToString(digest) + "\"";
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }
}
