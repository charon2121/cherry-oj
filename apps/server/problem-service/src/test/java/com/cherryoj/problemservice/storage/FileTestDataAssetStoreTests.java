package com.cherryoj.problemservice.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.storage.TestDataAssetStore.AssetException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class FileTestDataAssetStoreTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path temporary;

    @Test
    void validArchiveIsHashedManifestedSealedAndReadByteForByte() throws Exception {
        FileTestDataAssetStore store = store(temporary.resolve("store"), 100);
        byte[] archive = zip(Map.of("2.out", "9\n".getBytes(), "1.in", "1 2\n".getBytes(),
                "2.in", "4 5\n".getBytes(), "1.out", "3\n".getBytes()));
        String id = UUID.randomUUID().toString();

        var staged = store.stage(id, new ByteArrayInputStream(archive));

        assertThat(staged.archiveBytes()).isEqualTo(archive.length);
        assertThat(staged.contentSha256()).matches("[a-f0-9]{64}");
        assertThat(staged.caseCount()).isEqualTo(2);
        assertThat(staged.totalBytes()).isEqualTo(12);
        assertThat(staged.manifest().files()).extracting(file -> file.name())
                .containsExactly("1.in", "1.out", "2.in", "2.out");

        store.seal(staged);
        try (var opened = store.open(staged.storageRef())) {
            assertThat(opened.stream().readAllBytes()).isEqualTo(archive);
        }
        assertThatThrownBy(() -> store.seal(staged)).isInstanceOf(AssetException.class);
    }

    @Test
    void rejectsUnsafeEntriesBrokenUtf8MissingPairsAndCorruptZipWithoutResidue() throws Exception {
        FileTestDataAssetStore store = store(temporary.resolve("unsafe"), 100);
        Map<String, byte[]> archives = new LinkedHashMap<>();
        archives.put("parent", zip(Map.of("../1.in", bytes("1"), "1.out", bytes("1"))));
        archives.put("directory", zipWithMode("folder/", new byte[0], UnixStat.DIR_FLAG | 0700));
        archives.put("symlink", zipWithMode("1.in", bytes("target"), UnixStat.LINK_FLAG | 0700));
        archives.put("unicode", zip(Map.of("题.in", bytes("1"), "题.out", bytes("1"))));
        archives.put("orphan", zip(Map.of("1.in", bytes("1"))));
        archives.put("duplicate", zipEntries("1.in", "1.in", "1.out"));
        archives.put("invalid-utf8", zip(Map.of("1.in", new byte[] {(byte) 0xc3, 0x28}, "1.out", bytes("1"))));
        archives.put("corrupt", new byte[] {1, 2, 3, 4});

        for (var candidate : archives.entrySet()) {
            String id = UUID.randomUUID().toString();
            assertThatThrownBy(() -> store.stage(id, new ByteArrayInputStream(candidate.getValue())))
                    .as(candidate.getKey())
                    .isInstanceOf(AssetException.class);
            assertThat(Files.exists(temporary.resolve("unsafe/tmp/" + id + ".upload"))).isFalse();
        }
    }

    @Test
    void enforcesActualArchiveExpandedEntryFileCountAndCompressionRatioLimits() throws Exception {
        Path root = temporary.resolve("limits");
        var properties = new TestDataStorageProperties(
                root, DataSize.ofBytes(300), DataSize.ofBytes(30), DataSize.ofBytes(20),
                2, 2, Duration.ofHours(1));
        var store = new FileTestDataAssetStore(properties, CLOCK);
        store.initialize();

        assertThatThrownBy(() -> store.stage(UUID.randomUUID().toString(),
                new ByteArrayInputStream(new byte[301])))
                .isInstanceOfSatisfying(AssetException.class,
                        error -> assertThat(error.kind()).isEqualTo(TestDataAssetStore.Kind.PAYLOAD_TOO_LARGE));

        byte[] compressed = zip(Map.of("1.in", "a".repeat(100).getBytes(), "1.out", bytes("1")));
        assertThatThrownBy(() -> store.stage(UUID.randomUUID().toString(), new ByteArrayInputStream(compressed)))
                .isInstanceOfSatisfying(AssetException.class,
                        error -> assertThat(error.kind()).isEqualTo(TestDataAssetStore.Kind.PAYLOAD_TOO_LARGE));

        byte[] tooMany = zip(Map.of("1.in", bytes("1"), "1.out", bytes("1"), "2.in", bytes("2")));
        assertThatThrownBy(() -> store.stage(UUID.randomUUID().toString(), new ByteArrayInputStream(tooMany)))
                .isInstanceOf(AssetException.class);
    }

    @Test
    void recoveryDeletesOnlyOldBoundedTemporaryAndOrphanFiles() throws Exception {
        Path root = temporary.resolve("recovery");
        FileTestDataAssetStore store = store(root, 100);
        byte[] archive = zip(Map.of("1.in", bytes("1"), "1.out", bytes("1")));
        var ready = store.stage(UUID.randomUUID().toString(), new ByteArrayInputStream(archive));
        store.seal(ready);
        Path readyPath = root.resolve(ready.storageRef());
        Files.setLastModifiedTime(readyPath, FileTime.from(Instant.parse("2026-08-29T00:00:00Z")));

        Path staleTemporary = root.resolve("tmp/" + UUID.randomUUID() + ".upload");
        Path orphan = root.resolve("assets/" + UUID.randomUUID() + ".zip");
        Files.write(staleTemporary, bytes("temporary"));
        Files.write(orphan, bytes("orphan"));
        FileTime old = FileTime.from(Instant.parse("2026-08-29T00:00:00Z"));
        Files.setLastModifiedTime(staleTemporary, old);
        Files.setLastModifiedTime(orphan, old);
        Path unrelated = root.resolve("assets/do-not-touch.txt");
        Files.write(unrelated, bytes("unrelated"));
        Files.setLastModifiedTime(unrelated, old);

        var result = store.recover(Set.of(ready.storageRef()));

        assertThat(result.temporaryFilesDeleted()).isEqualTo(1);
        assertThat(result.orphanAssetsDeleted()).isEqualTo(1);
        assertThat(readyPath).exists();
        assertThat(unrelated).exists();
    }

    private FileTestDataAssetStore store(Path root, double ratio) {
        var properties = new TestDataStorageProperties(
                root, DataSize.ofMegabytes(1), DataSize.ofMegabytes(1), DataSize.ofKilobytes(512),
                20, ratio, Duration.ofHours(1));
        var store = new FileTestDataAssetStore(properties, CLOCK);
        store.initialize();
        return store;
    }

    private static byte[] zip(Map<String, byte[]> entries) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(output)) {
            for (var value : entries.entrySet()) {
                archive.putArchiveEntry(new ZipArchiveEntry(value.getKey()));
                archive.write(value.getValue());
                archive.closeArchiveEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] zipWithMode(String name, byte[] content, int mode) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(output)) {
            ZipArchiveEntry entry = new ZipArchiveEntry(name);
            entry.setUnixMode(mode);
            archive.putArchiveEntry(entry);
            archive.write(content);
            archive.closeArchiveEntry();
        }
        return output.toByteArray();
    }

    private static byte[] zipEntries(String... names) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipArchiveOutputStream archive = new ZipArchiveOutputStream(output)) {
            for (String name : names) {
                archive.putArchiveEntry(new ZipArchiveEntry(name));
                archive.write(bytes("1"));
                archive.closeArchiveEntry();
            }
        }
        return output.toByteArray();
    }

    private static byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
