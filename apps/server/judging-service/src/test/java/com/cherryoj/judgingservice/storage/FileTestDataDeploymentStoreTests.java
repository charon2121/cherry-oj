package com.cherryoj.judgingservice.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.judgingservice.api.JudgingDtos.Manifest;
import com.cherryoj.judgingservice.api.JudgingDtos.ManifestFile;
import com.cherryoj.judgingservice.config.JudgingProperties;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.FilterOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileTestDataDeploymentStoreTests {
    @TempDir Path temporary;

    @Test
    void validatesManifestAndAtomicallyCreatesJudgeDirectory() throws Exception {
        Map<String, byte[]> files = validFiles();
        byte[] zip = zip(files);
        String id = UUID.randomUUID().toString();
        var store = store(temporary.resolve("root"));

        var installed = store.deploy(id, sha(zip), manifest(files), new ByteArrayInputStream(zip));

        assertThat(installed.sha256()).isEqualTo(sha(zip));
        assertThat(Files.readString(temporary.resolve("root/" + id + "/1.in"))).isEqualTo("1 2\n");
        assertThat(Files.readString(temporary.resolve("root/" + id + "/2.out"))).isEqualTo("9\n");
        assertThat(Files.getPosixFilePermissions(temporary.resolve("root/" + id)))
                .containsExactlyInAnyOrder(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                        PosixFilePermission.OWNER_EXECUTE);
        assertThat(Files.getPosixFilePermissions(temporary.resolve("root/" + id + "/1.in")))
                .containsExactly(PosixFilePermission.OWNER_READ);
        try (var staging = Files.list(temporary.resolve("root/.staging"))) {
            assertThat(staging).isEmpty();
        }

        assertThatThrownBy(() -> store.deploy(id, sha(zip), manifest(files), new ByteArrayInputStream(zip)))
                .isInstanceOf(TestDataDeploymentStore.DeploymentException.class);
        assertThat(Files.readString(temporary.resolve("root/" + id + "/1.in"))).isEqualTo("1 2\n");

        store.rollback(installed);
        assertThat(temporary.resolve("root/" + id)).doesNotExist();
    }

    @Test
    void rejectsHashManifestPathSymlinkAndBrokenUtf8WithoutPartialDirectory() throws Exception {
        var store = store(temporary.resolve("unsafe"));
        Map<String, byte[]> valid = validFiles();
        byte[] validZip = zip(valid);
        assertFailure(store, validZip, "0".repeat(64), manifest(valid));

        Manifest wrong = new Manifest(2, 12, List.of(
                new ManifestFile("1.in", 4, "0".repeat(64)),
                new ManifestFile("1.out", 2, sha(bytes("3\n"))),
                new ManifestFile("2.in", 4, sha(bytes("4 5\n"))),
                new ManifestFile("2.out", 2, sha(bytes("9\n")))));
        assertFailure(store, validZip, sha(validZip), wrong);

        byte[] traversal = zip(Map.of("../1.in", bytes("1"), "1.out", bytes("1")));
        assertFailure(store, traversal, sha(traversal), manifestForUnsafe(traversal));

        byte[] symlink = zipWithMode("1.in", bytes("target"), UnixStat.LINK_FLAG | 0700);
        assertFailure(store, symlink, sha(symlink),
                new Manifest(1, 6, List.of(new ManifestFile("1.in", 6, sha(bytes("target"))))));

        Map<String, byte[]> invalidUtf8Files = Map.of("1.in", new byte[] {(byte) 0xc3, 0x28}, "1.out", bytes("1"));
        byte[] invalidUtf8 = zip(invalidUtf8Files);
        assertFailure(store, invalidUtf8, sha(invalidUtf8), manifest(invalidUtf8Files));
    }

    @Test
    void interruptedArchiveAndLimitsLeaveNoStagingOrFinalData() throws Exception {
        var store = store(temporary.resolve("failure"));
        byte[] zip = zip(validFiles());
        String id = UUID.randomUUID().toString();
        InputStream interrupted = new ByteArrayInputStream(zip) {
            private int total;
            @Override public synchronized int read(byte[] buffer, int offset, int length) {
                if (total > 16) throw new RuntimeException(new IOException("disconnect"));
                int read = super.read(buffer, offset, Math.min(length, 8));
                if (read > 0) total += read;
                return read;
            }
        };
        assertThatThrownBy(() -> store.deploy(id, sha(zip), manifest(validFiles()), interrupted))
                .isInstanceOf(TestDataDeploymentStore.DeploymentException.class);
        assertThat(temporary.resolve("failure/" + id)).doesNotExist();
        try (var staging = Files.list(temporary.resolve("failure/.staging"))) {
            assertThat(staging).isEmpty();
        }
    }

    @Test
    void recoveryDeletesOnlyOldStagingAndUnreadyUuidDirectories() throws Exception {
        Path root = temporary.resolve("recovery");
        var store = store(root);
        Map<String, byte[]> files = validFiles();
        byte[] zip = zip(files);
        String ready = UUID.randomUUID().toString();
        store.deploy(ready, sha(zip), manifest(files), new ByteArrayInputStream(zip));
        String orphan = UUID.randomUUID().toString();
        Files.createDirectory(root.resolve(orphan));
        Files.writeString(root.resolve(orphan + "/1.in"), "orphan");
        Path staleStage = root.resolve(".staging/stale-work");
        Files.createDirectory(staleStage);
        FileTime old = FileTime.from(Instant.now().minusSeconds(172_800));
        Files.setLastModifiedTime(root.resolve(orphan), old);
        Files.setLastModifiedTime(staleStage, old);
        Path unrelated = root.resolve("do-not-touch");
        Files.writeString(unrelated, "unrelated");
        Files.setLastModifiedTime(unrelated, old);

        var result = store.recover(java.util.Set.of(ready));

        assertThat(result.stagingDeleted()).isEqualTo(1);
        assertThat(result.orphanDirectoriesDeleted()).isEqualTo(1);
        assertThat(root.resolve(ready)).exists();
        assertThat(unrelated).exists();
    }

    @Test
    void simulatedDiskFullLeavesNeitherFinalNorTemporaryData() throws Exception {
        Path root = temporary.resolve("disk-full");
        var properties = properties(root);
        var store = new FileTestDataDeploymentStore(properties, path -> {
            OutputStream target = Files.newOutputStream(path);
            if (!path.getFileName().toString().equals("asset.zip")) return target;
            return new FilterOutputStream(target) {
                int written;
                @Override public void write(byte[] bytes, int offset, int length) throws IOException {
                    if (written + length > 16) throw new IOException("No space left on device");
                    out.write(bytes, offset, length);
                    written += length;
                }
                @Override public void write(int value) throws IOException {
                    if (++written > 16) throw new IOException("No space left on device");
                    out.write(value);
                }
            };
        });
        store.initialize();
        Map<String, byte[]> files = validFiles();
        byte[] zip = zip(files);
        String id = UUID.randomUUID().toString();

        assertThatThrownBy(() -> store.deploy(id, sha(zip), manifest(files), new ByteArrayInputStream(zip)))
                .isInstanceOfSatisfying(TestDataDeploymentStore.DeploymentException.class,
                        error -> assertThat(error.kind()).isEqualTo(TestDataDeploymentStore.Kind.STORAGE));
        assertThat(root.resolve(id)).doesNotExist();
        try (var staging = Files.list(root.resolve(".staging"))) {
            assertThat(staging).isEmpty();
        }
    }

    private void assertFailure(FileTestDataDeploymentStore store, byte[] zip, String expected, Manifest manifest) {
        String id = UUID.randomUUID().toString();
        assertThatThrownBy(() -> store.deploy(id, expected, manifest, new ByteArrayInputStream(zip)))
                .isInstanceOf(TestDataDeploymentStore.DeploymentException.class);
        assertThat(temporary.resolve("unsafe/" + id)).doesNotExist();
    }

    private FileTestDataDeploymentStore store(Path root) {
        var properties = properties(root);
        var store = new FileTestDataDeploymentStore(properties);
        store.initialize();
        return store;
    }

    private static JudgingProperties properties(Path root) {
        return new JudgingProperties(root, 1024 * 1024, 1024 * 1024, 512 * 1024,
                20, 100, java.time.Duration.ofSeconds(5), java.time.Duration.ofHours(24), false, null);
    }

    private static Map<String, byte[]> validFiles() {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("1.in", bytes("1 2\n"));
        files.put("1.out", bytes("3\n"));
        files.put("2.in", bytes("4 5\n"));
        files.put("2.out", bytes("9\n"));
        return files;
    }

    private static Manifest manifest(Map<String, byte[]> files) {
        long total = files.values().stream().mapToLong(value -> value.length).sum();
        List<ManifestFile> entries = files.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(value -> new ManifestFile(value.getKey(), value.getValue().length, sha(value.getValue())))
                .toList();
        return new Manifest((int) files.keySet().stream().map(name -> name.substring(0, name.lastIndexOf('.')))
                .distinct().count(), total, entries);
    }

    private static Manifest manifestForUnsafe(byte[] ignored) {
        return new Manifest(1, 2, List.of(
                new ManifestFile("1.in", 1, sha(bytes("1"))),
                new ManifestFile("1.out", 1, sha(bytes("1")))));
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

    private static String sha(byte[] value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value)); }
        catch (Exception impossible) { throw new IllegalStateException(impossible); }
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
}
