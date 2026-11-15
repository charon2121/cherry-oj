package com.cherryoj.judgingservice.judge;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class LanguageCatalogTests {
    private final ObjectMapper json = new ObjectMapper();

    @Test
    void packagedCatalogIsTheContractAndUnknownLanguagesAreRejected() throws Exception {
        var contract = json.readTree(Files.readString(Path.of("../../../contracts/languages.json")));
        try (var resource = LanguageCatalog.class.getResourceAsStream("/contracts/languages.json")) {
            assertNotNull(resource);
            assertEquals(contract, json.readTree(resource));
        }
        var catalog = new LanguageCatalog(json);
        for (var entry : contract.path("languages").properties())
            assertEquals(
                    entry.getValue().path("requiresCompilation").booleanValue(),
                    catalog.requiresCompilation(entry.getKey()));
        assertThrows(IllegalArgumentException.class, () -> catalog.requiresCompilation("unknown"));
    }

    @Test
    void unknownVersionAndEmptyCatalogCannotStart() {
        for (String version : new String[] {"1", "3", "2.5", "\"2\""})
            assertThrows(
                    IllegalStateException.class,
                    () -> LanguageCatalog.read(json.readTree("{\"x-contract-version\":" + version
                            + ",\"languages\":{\"c\":{\"requiresCompilation\":true}}}")));
        assertThrows(
                IllegalStateException.class,
                () -> LanguageCatalog.read(json.readTree("{\"x-contract-version\":2,\"languages\":{}}")));
    }

    @Test
    void interpretedAndMissingCompilationDeclarationsAreDifferent() {
        assertFalse(LanguageCatalog.read(json.readTree("""
                {"x-contract-version":2,"languages":{"interpreted":{"requiresCompilation":false}}}
                """)).get("interpreted"));
        for (String value : new String[] {"null", "0", "\"false\""})
            assertThrows(
                    IllegalStateException.class,
                    () -> LanguageCatalog.read(json.readTree(
                            "{\"x-contract-version\":2,\"languages\":{\"invalid\":{\"requiresCompilation\":" + value
                                    + "}}}")));
        assertThrows(
                IllegalStateException.class,
                () -> LanguageCatalog.read(json.readTree("{\"x-contract-version\":2,\"languages\":{\"missing\":{}}}")));
    }
}
