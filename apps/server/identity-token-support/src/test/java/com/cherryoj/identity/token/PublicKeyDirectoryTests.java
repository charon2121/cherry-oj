package com.cherryoj.identity.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PublicKeyDirectoryTests {
    @TempDir
    Path directory;

    @Test
    void loadsOneKeyPerServiceAndRefusesAnIncompleteDirectory() throws Exception {
        for (String service : ServiceNames.ALL) write(service);
        var keys = PublicKeyDirectory.load(directory.toString());
        for (String service : ServiceNames.ALL) assertThat(keys.key(service)).isNotNull();
        assertThat(keys.key("unknown-service")).isNull();

        Files.delete(directory.resolve(ServiceNames.PROBLEM + "-public.pem"));
        assertThatThrownBy(() -> PublicKeyDirectory.load(directory.toString()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ServiceNames.PROBLEM);
        assertThat(PublicKeyDirectory.load("classpath:test-service-identity").key(ServiceNames.JUDGING))
                .isNotNull();
        assertThatThrownBy(() ->
                        PublicKeyDirectory.load(directory.resolve("missing").toString()))
                .isInstanceOf(IllegalStateException.class);
    }

    private void write(String service) throws Exception {
        var pair = CallTokenTests.rsa();
        String body = Base64.getMimeEncoder(64, "\n".getBytes())
                .encodeToString(pair.getPublic().getEncoded());
        Files.writeString(
                directory.resolve(service + "-public.pem"),
                "-----BEGIN PUBLIC KEY-----\n" + body + "\n-----END PUBLIC KEY-----\n");
    }
}
