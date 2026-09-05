package com.cherryoj.userservice.security;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

public record SigningKeys(RSAKey current, JWKSet publicJwkSet, RSAPublicKey publicKey, RSAPrivateKey privateKey) {

    public String activeKid() {
        return current.getKeyID();
    }
}
