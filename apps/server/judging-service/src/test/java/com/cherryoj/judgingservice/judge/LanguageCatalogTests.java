package com.cherryoj.judgingservice.judge;

import static org.junit.jupiter.api.Assertions.*;

import com.cherryoj.judgingservice.limits.LimitModel;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

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
        assertNull(catalog.coreFiles("unknown"));
        assertNull(catalog.limitModel("unknown"));
        assertEquals(new LanguageFiles("Main.c", "solution.c"), catalog.coreFiles("c"));
        assertEquals(new LanguageFiles("Main.cpp", "solution.cpp"), catalog.coreFiles("cpp"));
        assertEquals(new LanguageFiles("Main.java", "Solution.java"), catalog.coreFiles("java"));
        assertEquals(new LimitModel(1000, null, 0, 0, 1000, 0, true), catalog.limitModel("cpp"));
        assertEquals(new LimitModel(3200, null, 280_000_000, 0, 1250, 40L << 20, true), catalog.limitModel("java"));
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
        assertFalse(LanguageCatalog.read(fixture()).get("sample").requiresCompilation());
        for (String value : new String[] {"null", "0", "\"false\""}) {
            var catalog = fixture();
            ((ObjectNode) catalog.path("languages").path("sample")).set("requiresCompilation", json.readTree(value));
            assertTrue(assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(catalog))
                    .getMessage()
                    .contains("requiresCompilation"));
        }
        var missing = fixture();
        ((ObjectNode) missing.path("languages").path("sample")).remove("requiresCompilation");
        assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(missing));
    }

    @Test
    void aNewEntryProvidesEveryMetadataFieldWithoutAnotherLanguageTable() {
        var entry = LanguageCatalog.read(fixture()).get("sample");
        assertFalse(entry.requiresCompilation());
        assertEquals(new LanguageFiles("Runner.src", "answer.src"), entry.files());
        assertEquals(new LimitModel(3200, 5000L, 170_000_000, 7, 1250, 41_943_040, false), entry.limitModel());
    }

    @Test
    void explicitLackOfCoreSupportAndAbsentModelDoNotBecomeDefaults() {
        var catalog = fixture();
        var entry = (ObjectNode) catalog.path("languages").path("sample");
        ((ObjectNode) entry.path("files")).putNull("solutionFile");
        entry.remove("limitModel");
        var parsed = LanguageCatalog.read(catalog).get("sample");
        assertNull(parsed.files().solutionFile());
        assertNull(parsed.limitModel());
    }

    @Test
    void malformedFileDeclarationsCannotStart() {
        for (String field : new String[] {"driverFile", "solutionFile"}) {
            for (String value : new String[] {"../Main.src", "/Main.src", ".", "", "a/b", "a\\b", "a\n"}) {
                var catalog = fixture();
                ((ObjectNode) catalog.path("languages").path("sample").path("files")).put(field, value);
                assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(catalog), field + "=" + value);
            }
            var catalog = fixture();
            ((ObjectNode) catalog.path("languages").path("sample").path("files")).remove(field);
            assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(catalog), field);
        }
        var catalog = fixture();
        ((ObjectNode) catalog.path("languages").path("sample").path("files")).put("solutionFile", "Runner.src");
        assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(catalog));
    }

    @Test
    void missingAndCoercedModelFieldsCannotStart() {
        for (String field : new String[] {
            "cpuFactorPermille",
            "cpuFactorCapPermille",
            "cpuOverheadNs",
            "ioPicosPerByte",
            "memoryFactorPermille",
            "memoryOverheadBytes",
            "baseEligible"
        }) {
            var missing = fixture();
            ((ObjectNode) missing.path("languages").path("sample").path("limitModel")).remove(field);
            assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(missing), field);
            for (String value : new String[] {"\"1000\"", "1.5", "9223372036854775808"}) {
                var invalid = fixture();
                ((ObjectNode) invalid.path("languages").path("sample").path("limitModel"))
                        .set(field, json.readTree(value));
                assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(invalid), field + "=" + value);
            }
        }
    }

    @Test
    void outOfRangeModelsAndNonIdentityCppCannotStart() {
        for (String field : new String[] {
            "cpuFactorPermille",
            "cpuFactorCapPermille",
            "cpuOverheadNs",
            "ioPicosPerByte",
            "memoryFactorPermille",
            "memoryOverheadBytes"
        }) {
            var catalog = fixture();
            ((ObjectNode) catalog.path("languages").path("sample").path("limitModel")).put(field, -1);
            assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(catalog), field);
        }
        var catalog = fixture();
        var entries = (ObjectNode) catalog.path("languages");
        entries.set("cpp", entries.remove("sample"));
        assertThrows(IllegalStateException.class, () -> LanguageCatalog.read(catalog));
    }

    private ObjectNode fixture() {
        return (ObjectNode) json.readTree("""
                {"x-contract-version":2,"languages":{"sample":{
                  "requiresCompilation":false,
                  "files":{"driverFile":"Runner.src","solutionFile":"answer.src"},
                  "limitModel":{"cpuFactorPermille":3200,"cpuFactorCapPermille":5000,
                    "cpuOverheadNs":170000000,"ioPicosPerByte":7,"memoryFactorPermille":1250,
                    "memoryOverheadBytes":41943040,"baseEligible":false}
                }}}
                """);
    }
}
