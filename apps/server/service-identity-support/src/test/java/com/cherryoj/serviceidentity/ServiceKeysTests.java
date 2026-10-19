package com.cherryoj.serviceidentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceKeysTests {

    @TempDir
    Path directory;

    @Test
    void readsOpensslStylePkcs8PrivateAndX509PublicKeys() throws Exception {
        var pair = TestKeys.rsa(2048);
        var privatePem = TestKeys.writePem(
                directory,
                "judging-service-private.pem",
                "PRIVATE KEY",
                pair.getPrivate().getEncoded());
        var publicPem = TestKeys.writePem(
                directory,
                "judging-service-public.pem",
                "PUBLIC KEY",
                pair.getPublic().getEncoded());

        assertThat(ServiceKeys.privateKey("file:" + privatePem).getModulus())
                .isEqualTo(((java.security.interfaces.RSAPublicKey) pair.getPublic()).getModulus());
        assertThat(ServiceKeys.publicKey(publicPem.toString())).isEqualTo(pair.getPublic());
    }

    @Test
    void rejectsWeakKeysMissingFilesAndWrongPemTypes() throws Exception {
        var weak = TestKeys.rsa(1024);
        var weakPem = TestKeys.writePem(
                directory, "weak.pem", "PUBLIC KEY", weak.getPublic().getEncoded());
        var pair = TestKeys.rsa(2048);
        var publicPem = TestKeys.writePem(
                directory, "public.pem", "PUBLIC KEY", pair.getPublic().getEncoded());

        assertThatThrownBy(() -> ServiceKeys.publicKey("file:" + weakPem)).hasMessageContaining("at least 2048");
        assertThatThrownBy(() -> ServiceKeys.privateKey("file:" + directory.resolve("missing.pem")))
                .hasMessageContaining("cannot read");
        assertThatThrownBy(() -> ServiceKeys.privateKey("file:" + publicPem)).hasMessageContaining("BEGIN PRIVATE KEY");
        assertThatThrownBy(() -> ServiceKeys.privateKey(" ")).hasMessageContaining("not configured");
    }
}
