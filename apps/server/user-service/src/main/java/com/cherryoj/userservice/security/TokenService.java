package com.cherryoj.userservice.security;

import com.cherryoj.userservice.config.AuthProperties;
import com.cherryoj.userservice.domain.LoginGrant;
import com.cherryoj.userservice.domain.UserAccount;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "cherry.auth", name = "mode", havingValue = "server", matchIfMissing = true)
public class TokenService {

    private final JwtEncoder encoder;
    private final SigningKeys keys;
    private final AuthProperties properties;
    private final Clock clock;

    public TokenService(JwtEncoder encoder, SigningKeys keys, AuthProperties properties, Clock clock) {
        this.encoder = encoder;
        this.keys = keys;
        this.properties = properties;
        this.clock = clock;
    }

    public TokenValue issue(UserAccount account) {
        return issue(account.id(), account.role().name(), account.sessionVersion(), account.passwordChangeRequired());
    }

    public TokenValue issue(LoginGrant grant) {
        return issue(grant.userId(), grant.role().name(), grant.sessionVersion(), grant.passwordChangeRequired());
    }

    private TokenValue issue(String userId, String role, long sessionVersion, boolean passwordChangeRequired) {
        Instant issuedAt = clock.instant();
        Instant expiresAt = issuedAt.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .audience(List.of(properties.audience()))
                .subject(userId)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt)
                .id(UUID.randomUUID().toString())
                .claim("roles", List.of(role))
                .claim("sv", sessionVersion)
                .claim("pwd", passwordChangeRequired)
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .keyId(keys.activeKid())
                .type("JWT")
                .build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new TokenValue(value, expiresAt);
    }
}
