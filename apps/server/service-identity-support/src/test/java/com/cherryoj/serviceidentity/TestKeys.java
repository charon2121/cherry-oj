package com.cherryoj.serviceidentity;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

final class TestKeys {

    private TestKeys() {}

    static KeyPair rsa(int bits) {
        try {
            var generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(bits);
            return generator.generateKeyPair();
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    static Path writePem(Path directory, String file, String type, byte[] der) throws IOException {
        String body = Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(der);
        return Files.writeString(
                directory.resolve(file), "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----\n");
    }
}
