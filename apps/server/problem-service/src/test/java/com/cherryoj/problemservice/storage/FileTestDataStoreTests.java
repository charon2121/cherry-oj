package com.cherryoj.problemservice.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.storage.TestDataStore.AssetException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;
import tools.jackson.databind.json.JsonMapper;

class FileTestDataStoreTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-30T00:00:00Z"), ZoneOffset.UTC);
    private static final String PROBLEM = "019c8e42-7f70-7000-8000-000000000101";
    private static final String OTHER_PROBLEM = "019c8e42-7f70-7000-8000-000000000202";

    /** docs/protocols/testdata.md 的 digest 算法对下面这份数据的结果；Go 与 Python 的测试钉着同一个值。 */
    private static final String GOLDEN_DIGEST = "6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4";

    @TempDir
    Path temporary;

    @Test
    void anUploadSealsAnIndependentProtocolDirectory() throws Exception {
        Path root = temporary.resolve("fresh/store");
        FileTestDataStore store = store(root, 100);
        byte[] archive = zip(goldenEntries());

        var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(archive));
        Path target = root.resolve(".store/published").resolve(prepared.generation());
        assertThat(target).doesNotExist();
        String location = store.seal(prepared);

        assertThat(location).isEqualTo(target.toString());
        assertThat(prepared.info().digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(prepared.info().testcaseCount()).isEqualTo(2);
        assertThat(prepared.info().totalBytes()).isEqualTo(16);
        Path address = Path.of(location);
        assertThat(Files.isDirectory(address, LinkOption.NOFOLLOW_LINKS)).isTrue();
        assertThat(root.resolve(PROBLEM)).doesNotExist();
        assertThat(Files.readString(address.resolve("1.in"))).isEqualTo("1 2\n");
        assertThat(Files.readString(address.resolve("2.out"))).isEqualTo("93\n");
        assertThat(Files.readString(address.resolve("testdata.json")))
                .contains("\"schemaVersion\":1", "\"digest\":\"" + GOLDEN_DIGEST + "\"");

        // judge 通常是另一个系统用户，所以数据要让其他用户可读；暂存的 ZIP 已经删掉
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(address.resolve("1.in"))))
                .isEqualTo("rw-r--r--");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(
                        root.resolve(".store/published").resolve(prepared.generation()))))
                .isEqualTo("rwxr-xr-x");
        try (var staged = Files.list(root.resolve("tmp"))) {
            assertThat(staged).isEmpty();
        }

        var described = store.describe(location);
        assertThat(described).isEqualTo(prepared.info());
        assertThat(described.testcases())
                .extracting(TestDataMetadata.TestcaseEntry::name)
                .containsExactly("1", "2");
    }

    @Test
    void testPointsKeepTheDefaultOrderNumericThenByName() throws Exception {
        FileTestDataStore store = store(temporary.resolve("order"), 100);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        for (String name : List.of("10", "2", "1", "small", "big-1")) {
            entries.put(name + ".in", bytes("i"));
            entries.put(name + ".out", bytes("o"));
        }

        var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(zip(entries)));

        assertThat(prepared.info().testcases())
                .extracting(TestDataMetadata.TestcaseEntry::name)
                .containsExactly("1", "2", "10", "big-1", "small");
    }

    @Test
    void acceptsSingleWrapperAndFinderMetadataAndWritesOnlyTheTestPoints() throws Exception {
        Path root = temporary.resolve("finder");
        FileTestDataStore store = store(root, 100);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("testin/", new byte[0]);
        entries.put("testin/.DS_Store", bytes("finder metadata"));
        entries.put("__MACOSX/testin/._.DS_Store", bytes("apple double"));
        entries.put("testin/._test1.in", bytes("apple double"));
        entries.put("testin/test1.out", bytes("3\n"));
        entries.put("testin/test1.in", bytes("1 2\n"));

        var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(zip(entries)));
        String location = store.seal(prepared);

        assertThat(prepared.info().testcaseCount()).isOne();
        assertThat(prepared.info().totalBytes()).isEqualTo(6);
        try (var files = Files.list(Path.of(location))) {
            assertThat(files.map(path -> path.getFileName().toString()).sorted().toList())
                    .containsExactly("test1.in", "test1.out", "testdata.json");
        }
    }

    @Test
    void rejectsUnsafeEntriesBrokenUtf8MissingPairsAndCorruptZipWithoutResidue() throws Exception {
        Path root = temporary.resolve("unsafe");
        FileTestDataStore store = store(root, 100);
        // 每种问题报出各自的原因代码；能确定是哪个条目时一并带出（多条目 Map.of 的遍历顺序不定，那些只核对代码）。
        record Rejection(byte[] zip, String code, String entry) {}
        Map<String, Rejection> archives = new LinkedHashMap<>();
        archives.put(
                "parent",
                new Rejection(
                        zip(Map.of("../1.in", bytes("1"), "1.out", bytes("1"))), "TEST_DATA_UNSAFE_ENTRY_PATH", null));
        archives.put(
                "directory",
                new Rejection(
                        zipWithMode("folder/", new byte[0], UnixStat.DIR_FLAG | 0700), "TEST_DATA_NO_TESTCASES", null));
        archives.put(
                "symlink",
                new Rejection(
                        zipWithMode("1.in", bytes("target"), UnixStat.LINK_FLAG | 0700),
                        "TEST_DATA_ENTRY_NOT_REGULAR_FILE",
                        "1.in"));
        // Python zipfile.writestr 直接传文件名时只写权限位 0600、不写文件类型。
        archives.put(
                "permission-bits-without-file-type",
                new Rejection(zipWithMode("1.in", bytes("1"), 0600), "TEST_DATA_ENTRY_NOT_REGULAR_FILE", "1.in"));
        archives.put(
                "unicode",
                new Rejection(
                        zip(Map.of("题.in", bytes("1"), "题.out", bytes("1"))), "TEST_DATA_UNEXPECTED_ENTRY", null));
        archives.put("orphan", new Rejection(zip(Map.of("1.in", bytes("1"))), "TEST_DATA_CASE_PAIR_REQUIRED", "1.out"));
        archives.put(
                "duplicate", new Rejection(zipEntries("1.in", "1.in", "1.out"), "TEST_DATA_DUPLICATE_ENTRY", "1.in"));
        archives.put(
                "invalid-utf8",
                new Rejection(
                        zip(Map.of("1.in", new byte[] {(byte) 0xc3, 0x28}, "1.out", bytes("1"))),
                        "TEST_DATA_FILE_NOT_UTF8",
                        "1.in"));
        archives.put("corrupt", new Rejection(new byte[] {1, 2, 3, 4}, "TEST_DATA_INVALID_ZIP", null));
        archives.put(
                "multiple-wrappers",
                new Rejection(
                        zip(Map.of(
                                "first/1.in",
                                bytes("1"),
                                "first/1.out",
                                bytes("1"),
                                "second/2.in",
                                bytes("2"),
                                "second/2.out",
                                bytes("2"))),
                        "TEST_DATA_MIXED_ROOTS",
                        null));
        archives.put(
                "mixed-roots",
                new Rejection(
                        zip(Map.of(
                                "1.in",
                                bytes("1"),
                                "1.out",
                                bytes("1"),
                                "wrapped/2.in",
                                bytes("2"),
                                "wrapped/2.out",
                                bytes("2"))),
                        "TEST_DATA_MIXED_ROOTS",
                        null));
        archives.put(
                "nested-cases",
                new Rejection(
                        zip(Map.of("wrapped/cases/1.in", bytes("1"), "wrapped/cases/1.out", bytes("1"))),
                        "TEST_DATA_UNEXPECTED_ENTRY",
                        null));
        archives.put(
                "unknown-hidden-file",
                new Rejection(
                        zip(Map.of(
                                "wrapped/1.in",
                                bytes("1"),
                                "wrapped/1.out",
                                bytes("1"),
                                "wrapped/.Spotlight-V100",
                                bytes("metadata"))),
                        "TEST_DATA_UNEXPECTED_ENTRY",
                        "wrapped/.Spotlight-V100"));
        archives.put(
                "metadata-from-other-root",
                new Rejection(
                        zip(Map.of(
                                "wrapped/1.in",
                                bytes("1"),
                                "wrapped/1.out",
                                bytes("1"),
                                "other/.DS_Store",
                                bytes("metadata"))),
                        "TEST_DATA_MIXED_ROOTS",
                        "other/"));
        archives.put(
                "name-too-long-for-the-protocol",
                new Rejection(
                        zip(Map.of("a".repeat(125) + ".in", bytes("1"), "a".repeat(125) + ".out", bytes("1"))),
                        "TEST_DATA_UNEXPECTED_ENTRY",
                        null));

        for (var candidate : archives.entrySet()) {
            Rejection expected = candidate.getValue();
            assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(expected.zip())))
                    .as(candidate.getKey())
                    .isInstanceOfSatisfying(AssetException.class, error -> {
                        assertThat(error.getMessage()).as(candidate.getKey()).isEqualTo(expected.code());
                        if (expected.entry() != null) {
                            assertThat(error.entry()).as(candidate.getKey()).isEqualTo(expected.entry());
                        }
                    });
        }
        // 失败不留任何残留：没有暂存文件，没有数据目录，没有地址
        try (var staged = Files.list(root.resolve("tmp"));
                var generations = Files.list(root.resolve(".store/published"))) {
            assertThat(staged).isEmpty();
            assertThat(generations).isEmpty();
        }
        assertThat(root.resolve(PROBLEM)).doesNotExist();
    }

    @Test
    void enforcesActualArchiveExpandedEntryFileCountAndCompressionRatioLimits() throws Exception {
        Path root = temporary.resolve("limits");
        var properties = new TestDataStorageProperties(
                root, DataSize.ofBytes(300), DataSize.ofBytes(30), DataSize.ofBytes(20), 2, 2, Duration.ofHours(1));
        var store =
                new FileTestDataStore(properties, CLOCK, JsonMapper.builder().build());
        store.initialize();

        assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(new byte[301])))
                .isInstanceOfSatisfying(AssetException.class, error -> {
                    assertThat(error.kind()).isEqualTo(TestDataStore.Kind.PAYLOAD_TOO_LARGE);
                    assertThat(error.getMessage()).isEqualTo("TEST_DATA_ARCHIVE_TOO_LARGE");
                    assertThat(error.limit()).isEqualTo("300 字节");
                });

        // 报出是哪个文件、上限是多少，管理员才知道该改哪一组数据。
        byte[] compressed = zip(Map.of("1.in", "a".repeat(100).getBytes(), "1.out", bytes("1")));
        assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(compressed)))
                .isInstanceOfSatisfying(AssetException.class, error -> {
                    assertThat(error.kind()).isEqualTo(TestDataStore.Kind.PAYLOAD_TOO_LARGE);
                    assertThat(error.getMessage())
                            .isIn("TEST_DATA_COMPRESSION_RATIO_EXCEEDED", "TEST_DATA_ENTRY_TOO_LARGE");
                    assertThat(error.entry()).isEqualTo("1.in");
                });

        // 三个条目的 ZIP 本身就超过这里 300 字节的上传上限，先被那一条拦下；文件数上限由下面的 entryLimitStore 单独验证。
        byte[] tooMany = zip(Map.of("1.in", bytes("1"), "1.out", bytes("1"), "2.in", bytes("2")));
        assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(tooMany)))
                .isInstanceOf(AssetException.class);

        byte[] metadataCounts = zip(Map.of("1.in", bytes("1"), "1.out", bytes("1"), ".DS_Store", bytes("metadata")));
        var entryLimitProperties = new TestDataStorageProperties(
                root.resolve("entry-limit"),
                DataSize.ofMegabytes(1),
                DataSize.ofMegabytes(1),
                DataSize.ofKilobytes(512),
                2,
                100,
                Duration.ofHours(1));
        var entryLimitStore = new FileTestDataStore(
                entryLimitProperties, CLOCK, JsonMapper.builder().build());
        entryLimitStore.initialize();
        assertThatThrownBy(() -> entryLimitStore.prepare(PROBLEM, new ByteArrayInputStream(metadataCounts)))
                .isInstanceOfSatisfying(AssetException.class, error -> {
                    assertThat(error.getMessage()).isEqualTo("TEST_DATA_TOO_MANY_FILES");
                    assertThat(error.limit()).isEqualTo("2");
                });
    }

    @Test
    void identicalUploadsHaveIndependentAddressesAndEqualDigests() throws Exception {
        Path root = temporary.resolve("same-data");
        FileTestDataStore store = store(root, 100);
        byte[] archive = zip(goldenEntries());

        String first = store.seal(store.prepare(PROBLEM, new ByteArrayInputStream(archive)));
        String second = store.seal(store.prepare(PROBLEM, new ByteArrayInputStream(archive)));

        assertThat(first).isNotEqualTo(second);
        assertThat(store.describe(first).digest())
                .isEqualTo(store.describe(second).digest());
    }

    @Test
    void retiredDirectoriesSurviveTheGracePeriodAndCleanupRechecksReferences() throws Exception {
        Path root = temporary.resolve("retention");
        FileTestDataStore store = store(root, 100);
        byte[] archive = zip(goldenEntries());
        String retired = store.seal(store.prepare(PROBLEM, new ByteArrayInputStream(archive)));
        String current = store.seal(store.prepare(PROBLEM, new ByteArrayInputStream(archive)));
        String other = store.seal(store.prepare(OTHER_PROBLEM, new ByteArrayInputStream(archive)));

        store.storedDirectories()
                .forEach(entry -> store.reclaim(entry, location -> location.equals(current) || location.equals(other)));
        FileTestDataStore beforeExpiry = store(root, 100, Clock.offset(CLOCK, Duration.ofHours(23)));
        beforeExpiry
                .storedDirectories()
                .forEach(entry ->
                        beforeExpiry.reclaim(entry, location -> location.equals(current) || location.equals(other)));
        assertThat(Path.of(retired)).isDirectory();

        FileTestDataStore afterExpiry = store(root, 100, Clock.offset(CLOCK, Duration.ofHours(25)));
        afterExpiry
                .storedDirectories()
                .forEach(entry ->
                        afterExpiry.reclaim(entry, location -> location.equals(current) || location.equals(other)));
        assertThat(Path.of(retired)).doesNotExist();
        assertThat(afterExpiry.describe(current).digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(afterExpiry.describe(other).digest()).isEqualTo(GOLDEN_DIGEST);
    }

    @Test
    void legacyAliasAndItsTargetRemainReadableUntilCleanup() throws Exception {
        Path root = temporary.resolve("legacy");
        FileTestDataStore store = store(root, 100);
        Path source = Path.of(store.seal(store.prepare(PROBLEM, new ByteArrayInputStream(zip(goldenEntries())))));
        Path target = root.resolve(".store").resolve(PROBLEM + "-" + GOLDEN_DIGEST.substring(0, 16));
        Files.move(source, target);
        Path legacy = Files.createSymbolicLink(root.resolve(PROBLEM), root.relativize(target));

        store.storedDirectories().forEach(entry -> store.reclaim(entry, legacy.toString()::equals));
        assertThat(store.describe(legacy.toString()).digest()).isEqualTo(GOLDEN_DIGEST);

        store.storedDirectories().forEach(entry -> store.reclaim(entry, ignored -> false));
        assertThat(store.describe(legacy.toString()).digest()).isEqualTo(GOLDEN_DIGEST);
        FileTestDataStore later = store(root, 100, Clock.offset(CLOCK, Duration.ofHours(25)));
        later.storedDirectories().forEach(entry -> later.reclaim(entry, ignored -> false));
        assertThat(Files.exists(legacy, LinkOption.NOFOLLOW_LINKS)).isFalse();
        assertThat(target).doesNotExist();
    }

    @Test
    void discardRemovesOnlyStagingEvenAfterTheDirectoryIsSealed() throws Exception {
        Path root = temporary.resolve("discard");
        FileTestDataStore store = store(root, 100);
        byte[] archive = zip(goldenEntries());
        var live = store.prepare(PROBLEM, new ByteArrayInputStream(archive));
        String location = store.seal(live);
        var pending = store.prepare(PROBLEM, new ByteArrayInputStream(archive));
        Path staging = root.resolve(".store/published/." + pending.generation() + ".tmp");
        assertThat(staging).isDirectory();

        store.discard(pending);
        store.discard(live);

        assertThat(staging).doesNotExist();
        assertThat(store.describe(location).digest()).isEqualTo(GOLDEN_DIGEST);
    }

    @Test
    void describeRefusesLocationsOutsideTheRootAndBrokenMetadata() throws Exception {
        Path root = temporary.resolve("describe");
        FileTestDataStore store = store(root, 100);
        var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(zip(goldenEntries())));
        String location = store.seal(prepared);

        for (String bad : List.of(
                "/etc", "relative/path", "", root.resolve("../elsewhere").toString())) {
            assertThatThrownBy(() -> store.describe(bad)).as(bad).isInstanceOf(AssetException.class);
        }
        assertThatThrownBy(() -> store.describe(root.resolve(OTHER_PROBLEM).toString()))
                .isInstanceOfSatisfying(
                        AssetException.class,
                        error -> assertThat(error.kind()).isEqualTo(TestDataStore.Kind.NOT_FOUND));

        Path metadata = Path.of(location).resolve("testdata.json");
        String valid = Files.readString(metadata);
        Path generation = root.resolve(".store/published").resolve(prepared.generation());
        Files.setPosixFilePermissions(
                generation.resolve("testdata.json"), PosixFilePermissions.fromString("rw-------"));
        for (String broken : List.of(
                "not json",
                valid.replace(GOLDEN_DIGEST, "0".repeat(64)),
                valid.replace("\"schemaVersion\":1", "\"schemaVersion\":2"))) {
            Files.writeString(metadata, broken);
            assertThatThrownBy(() -> store.describe(location)).as(broken).isInstanceOf(AssetException.class);
        }
    }

    @Test
    void theProblemIdMustBeACanonicalLowercaseUuid() throws Exception {
        FileTestDataStore store = store(temporary.resolve("ids"), 100);
        byte[] archive = zip(goldenEntries());
        for (String bad : List.of("../x", "1-1-1-1-1", PROBLEM.toUpperCase(), "", PROBLEM + "/..")) {
            assertThatThrownBy(() -> store.prepare(bad, new ByteArrayInputStream(archive)))
                    .as(bad)
                    .isInstanceOf(AssetException.class);
        }
    }

    @Test
    void recoveryDeletesOnlyOldTemporaryFilesLinksAndDirectories() throws Exception {
        Path root = temporary.resolve("recovery");
        // 时钟拨到两天之后：此刻新建的文件都算「过期」，用将来的修改时间标出需要保留的「新」文件。
        // （macOS 上改不了符号链接自身的时间，所以不能反过来给链接设一个旧时间。）
        Clock later = Clock.fixed(Instant.now().plus(Duration.ofDays(2)), ZoneOffset.UTC);
        var properties = new TestDataStorageProperties(
                root,
                DataSize.ofMegabytes(1),
                DataSize.ofMegabytes(1),
                DataSize.ofKilobytes(512),
                20,
                100,
                Duration.ofHours(1));
        FileTestDataStore store =
                new FileTestDataStore(properties, later, JsonMapper.builder().build());
        store.initialize();
        var live = store.prepare(PROBLEM, new ByteArrayInputStream(zip(goldenEntries())));
        String location = store.seal(live);
        FileTime recent = FileTime.from(Instant.now().plus(Duration.ofDays(3)));

        Path staleUpload = root.resolve("tmp/" + java.util.UUID.randomUUID() + ".upload");
        Path freshUpload = root.resolve("tmp/" + java.util.UUID.randomUUID() + ".upload");
        Path staleLink = root.resolve("." + PROBLEM + ".stale.link");
        Path staleBuilding = root.resolve(".store/published/." + live.generation() + ".stale.tmp");
        Files.write(staleUpload, bytes("temporary"));
        Files.write(freshUpload, bytes("temporary"));
        Files.createSymbolicLink(staleLink, Path.of(".store", live.generation()));
        Files.createDirectory(staleBuilding);
        Files.write(staleBuilding.resolve("1.in"), bytes("half written"));
        Path unrelated = root.resolve("tmp/do-not-touch.txt");
        Files.write(unrelated, bytes("unrelated"));
        Files.setLastModifiedTime(freshUpload, recent);
        // 不相关的文件、正在使用的数据目录也是「新」的：它们不在被清理的命名范围内，与新旧无关
        Files.setLastModifiedTime(unrelated, FileTime.from(Instant.parse("2026-08-29T00:00:00Z")));

        var result = store.recover();

        assertThat(result.temporaryFilesDeleted()).isEqualTo(2);
        assertThat(result.temporaryDirectoriesDeleted()).isEqualTo(1);
        assertThat(staleUpload).doesNotExist();
        assertThat(staleBuilding).doesNotExist();
        assertThat(Files.exists(staleLink, LinkOption.NOFOLLOW_LINKS)).isFalse();
        assertThat(freshUpload).exists();
        assertThat(unrelated).exists();
        assertThat(store.describe(location).digest()).isEqualTo(GOLDEN_DIGEST);
    }

    private FileTestDataStore store(Path root, double ratio) {
        return store(root, ratio, CLOCK);
    }

    private FileTestDataStore store(Path root, double ratio, Clock clock) {
        var properties = new TestDataStorageProperties(
                root,
                DataSize.ofMegabytes(1),
                DataSize.ofMegabytes(1),
                DataSize.ofKilobytes(512),
                20,
                ratio,
                Duration.ofHours(1));
        var store =
                new FileTestDataStore(properties, clock, JsonMapper.builder().build());
        store.initialize();
        return store;
    }

    private static Map<String, byte[]> goldenEntries() {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        entries.put("2.out", bytes("93\n"));
        entries.put("1.in", bytes("1 2\n"));
        entries.put("2.in", bytes("100 -7\n"));
        entries.put("1.out", bytes("3\n"));
        return entries;
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
