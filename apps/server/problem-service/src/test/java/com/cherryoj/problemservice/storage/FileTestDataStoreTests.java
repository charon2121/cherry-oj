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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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

    /** docs/testdata-protocol.md 的 digest 算法对下面这份数据的结果；Go 与 Python 的测试钉着同一个值。 */
    private static final String GOLDEN_DIGEST = "6c67e6d15542f93808352ac2b692f3772e1243d09bd34b2366b9b212345a07e4";

    @TempDir
    Path temporary;

    @Test
    void anUploadBecomesAProtocolDirectoryBehindAnAtomicAddress() throws Exception {
        Path root = temporary.resolve("store");
        FileTestDataStore store = store(root, 100);
        byte[] archive = zip(goldenEntries());

        var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(archive));
        // 此时还没有任何地址指向它
        assertThat(root.resolve(PROBLEM)).doesNotExist();
        String location = store.activate(PROBLEM, prepared);

        assertThat(location).isEqualTo(root.toAbsolutePath().normalize().resolve(PROBLEM).toString());
        assertThat(prepared.info().digest()).isEqualTo(GOLDEN_DIGEST);
        assertThat(prepared.info().caseCount()).isEqualTo(2);
        assertThat(prepared.info().totalBytes()).isEqualTo(16);
        assertThat(prepared.generation()).isEqualTo(PROBLEM + "-" + GOLDEN_DIGEST.substring(0, 16));

        // 地址是指向真实目录的相对符号链接：整个存储根按同一路径挂进容器后依然有效
        Path address = Path.of(location);
        assertThat(Files.isSymbolicLink(address)).isTrue();
        assertThat(Files.readSymbolicLink(address)).isEqualTo(Path.of(".store", prepared.generation()));
        assertThat(Files.readString(address.resolve("1.in"))).isEqualTo("1 2\n");
        assertThat(Files.readString(address.resolve("2.out"))).isEqualTo("93\n");
        assertThat(Files.readString(address.resolve("testdata.json")))
                .contains("\"schemaVersion\":1", "\"digest\":\"" + GOLDEN_DIGEST + "\"");

        // judge 通常是另一个系统用户，所以数据要让其他用户可读；暂存的 ZIP 已经删掉
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(address.resolve("1.in"))))
                .isEqualTo("rw-r--r--");
        assertThat(PosixFilePermissions.toString(Files.getPosixFilePermissions(
                root.resolve(".store").resolve(prepared.generation())))).isEqualTo("rwxr-xr-x");
        try (var staged = Files.list(root.resolve("tmp"))) {
            assertThat(staged).isEmpty();
        }

        var described = store.describe(location);
        assertThat(described).isEqualTo(prepared.info());
        assertThat(described.manifest().files()).extracting(file -> file.name())
                .containsExactly("1.in", "1.out", "2.in", "2.out");
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

        assertThat(prepared.info().manifest().files()).extracting(file -> file.name())
                .containsExactly("1.in", "1.out", "2.in", "2.out", "10.in", "10.out",
                        "big-1.in", "big-1.out", "small.in", "small.out");
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
        String location = store.activate(PROBLEM, prepared);

        assertThat(prepared.info().caseCount()).isOne();
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
        Map<String, byte[]> archives = new LinkedHashMap<>();
        archives.put("parent", zip(Map.of("../1.in", bytes("1"), "1.out", bytes("1"))));
        archives.put("directory", zipWithMode("folder/", new byte[0], UnixStat.DIR_FLAG | 0700));
        archives.put("symlink", zipWithMode("1.in", bytes("target"), UnixStat.LINK_FLAG | 0700));
        archives.put("unicode", zip(Map.of("题.in", bytes("1"), "题.out", bytes("1"))));
        archives.put("orphan", zip(Map.of("1.in", bytes("1"))));
        archives.put("duplicate", zipEntries("1.in", "1.in", "1.out"));
        archives.put("invalid-utf8", zip(Map.of("1.in", new byte[] {(byte) 0xc3, 0x28}, "1.out", bytes("1"))));
        archives.put("corrupt", new byte[] {1, 2, 3, 4});
        archives.put("multiple-wrappers", zip(Map.of(
                "first/1.in", bytes("1"), "first/1.out", bytes("1"),
                "second/2.in", bytes("2"), "second/2.out", bytes("2"))));
        archives.put("mixed-roots", zip(Map.of(
                "1.in", bytes("1"), "1.out", bytes("1"),
                "wrapped/2.in", bytes("2"), "wrapped/2.out", bytes("2"))));
        archives.put("nested-cases", zip(Map.of(
                "wrapped/cases/1.in", bytes("1"), "wrapped/cases/1.out", bytes("1"))));
        archives.put("unknown-hidden-file", zip(Map.of(
                "wrapped/1.in", bytes("1"), "wrapped/1.out", bytes("1"),
                "wrapped/.Spotlight-V100", bytes("metadata"))));
        archives.put("metadata-from-other-root", zip(Map.of(
                "wrapped/1.in", bytes("1"), "wrapped/1.out", bytes("1"),
                "other/.DS_Store", bytes("metadata"))));
        archives.put("name-too-long-for-the-protocol", zip(Map.of(
                "a".repeat(125) + ".in", bytes("1"), "a".repeat(125) + ".out", bytes("1"))));

        for (var candidate : archives.entrySet()) {
            assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(candidate.getValue())))
                    .as(candidate.getKey())
                    .isInstanceOf(AssetException.class);
        }
        // 失败不留任何残留：没有暂存文件，没有数据目录，没有地址
        try (var staged = Files.list(root.resolve("tmp")); var generations = Files.list(root.resolve(".store"))) {
            assertThat(staged).isEmpty();
            assertThat(generations).isEmpty();
        }
        assertThat(root.resolve(PROBLEM)).doesNotExist();
    }

    @Test
    void enforcesActualArchiveExpandedEntryFileCountAndCompressionRatioLimits() throws Exception {
        Path root = temporary.resolve("limits");
        var properties = new TestDataStorageProperties(
                root, DataSize.ofBytes(300), DataSize.ofBytes(30), DataSize.ofBytes(20),
                2, 2, Duration.ofHours(1));
        var store = new FileTestDataStore(properties, CLOCK, JsonMapper.builder().build());
        store.initialize();

        assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(new byte[301])))
                .isInstanceOfSatisfying(AssetException.class,
                        error -> assertThat(error.kind()).isEqualTo(TestDataStore.Kind.PAYLOAD_TOO_LARGE));

        byte[] compressed = zip(Map.of("1.in", "a".repeat(100).getBytes(), "1.out", bytes("1")));
        assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(compressed)))
                .isInstanceOfSatisfying(AssetException.class,
                        error -> assertThat(error.kind()).isEqualTo(TestDataStore.Kind.PAYLOAD_TOO_LARGE));

        byte[] tooMany = zip(Map.of("1.in", bytes("1"), "1.out", bytes("1"), "2.in", bytes("2")));
        assertThatThrownBy(() -> store.prepare(PROBLEM, new ByteArrayInputStream(tooMany)))
                .isInstanceOf(AssetException.class);

        byte[] metadataCounts = zip(Map.of(
                "1.in", bytes("1"), "1.out", bytes("1"), ".DS_Store", bytes("metadata")));
        var entryLimitProperties = new TestDataStorageProperties(
                root.resolve("entry-limit"), DataSize.ofMegabytes(1), DataSize.ofMegabytes(1),
                DataSize.ofKilobytes(512), 2, 100, Duration.ofHours(1));
        var entryLimitStore = new FileTestDataStore(entryLimitProperties, CLOCK, JsonMapper.builder().build());
        entryLimitStore.initialize();
        assertThatThrownBy(() -> entryLimitStore.prepare(PROBLEM, new ByteArrayInputStream(metadataCounts)))
                .isInstanceOfSatisfying(AssetException.class,
                        error -> assertThat(error.getMessage()).isEqualTo("TEST_DATA_TOO_MANY_FILES"));
    }

    @Test
    void uploadingTheSameDataAgainReusesTheContentAddressedDirectory() throws Exception {
        Path root = temporary.resolve("reuse");
        FileTestDataStore store = store(root, 100);
        byte[] archive = zip(goldenEntries());

        var first = store.prepare(PROBLEM, new ByteArrayInputStream(archive));
        store.activate(PROBLEM, first);
        Path directory = root.resolve(".store").resolve(first.generation());
        Instant created = Files.getLastModifiedTime(directory).toInstant();

        var second = store.prepare(PROBLEM, new ByteArrayInputStream(archive));

        assertThat(second.generation()).isEqualTo(first.generation());
        assertThat(Files.getLastModifiedTime(directory).toInstant()).isEqualTo(created);
        try (var generations = Files.list(root.resolve(".store"))) {
            assertThat(generations).hasSize(1);
        }
    }

    @Test
    void replacingDataKeepsOnlyTheCurrentAndThePreviousGeneration() throws Exception {
        Path root = temporary.resolve("generations");
        FileTestDataStore store = store(root, 100);
        String location = null;
        String lastGeneration = null;
        for (int version = 1; version <= 4; version++) {
            var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(zip(Map.of(
                    "1.in", bytes("in " + version), "1.out", bytes("out " + version)))));
            location = store.activate(PROBLEM, prepared);
            store.prune(PROBLEM);
            lastGeneration = prepared.generation();
            assertThat(Files.readString(Path.of(location).resolve("1.in"))).isEqualTo("in " + version);
            Files.setLastModifiedTime(root.resolve(".store").resolve(lastGeneration),
                    FileTime.from(Instant.parse("2026-08-30T00:00:00Z").plusSeconds(version)));
        }

        try (var generations = Files.list(root.resolve(".store"))) {
            assertThat(generations.map(path -> path.getFileName().toString()).toList())
                    .hasSize(2).contains(lastGeneration);
        }
        assertThat(lastGeneration).endsWith(store.describe(location).digest().substring(0, 16));
    }

    @Test
    void pruningOneProblemNeverTouchesAnotherProblemsData() throws Exception {
        Path root = temporary.resolve("isolation");
        FileTestDataStore store = store(root, 100);
        var other = store.prepare(OTHER_PROBLEM, new ByteArrayInputStream(zip(Map.of(
                "1.in", bytes("x"), "1.out", bytes("y")))));
        String otherLocation = store.activate(OTHER_PROBLEM, other);
        for (int version = 1; version <= 3; version++) {
            var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(zip(Map.of(
                    "1.in", bytes("in " + version), "1.out", bytes("out")))));
            store.activate(PROBLEM, prepared);
            store.prune(PROBLEM);
        }

        assertThat(Files.readString(Path.of(otherLocation).resolve("1.in"))).isEqualTo("x");
    }

    /**
     * 读取方在更新过程中随时读取，必须每次都读到一份完整的数据（新的或旧的），不能读到一半——
     * 这是用符号链接的原子重命名切换地址的全部理由。读取方照协议在出错时重读重试一次：macOS 上读取撞上
     * 符号链接被替换的瞬间偶尔会得到 EINVAL，Linux 上不会，但重试一次对两者都成立；连续两次失败就是真问题。
     * 两个方向都断言：新旧两份都被读到过，才说明切换确实发生了。
     */
    @Test
    void readersNeverSeeAPartialDirectoryWhileTheAddressIsSwitched() throws Exception {
        Path root = temporary.resolve("atomic");
        FileTestDataStore store = store(root, 100);
        var first = store.prepare(PROBLEM, new ByteArrayInputStream(zip(Map.of("1.in", bytes("a"), "1.out", bytes("A")))));
        var second = store.prepare(PROBLEM, new ByteArrayInputStream(zip(Map.of("1.in", bytes("b"), "1.out", bytes("B")))));
        String location = store.activate(PROBLEM, first);

        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        java.util.Set<String> seen = java.util.concurrent.ConcurrentHashMap.newKeySet();
        Thread reader = new Thread(() -> {
            try {
                while (running.get()) {
                    try {
                        seen.add(store.describe(location).digest());
                    }
                    catch (AssetException firstAttempt) {
                        seen.add(store.describe(location).digest());
                    }
                }
            }
            catch (Throwable error) {
                failure.set(error);
            }
        });
        reader.start();
        for (int index = 0; index < 200 && failure.get() == null; index++) {
            store.activate(PROBLEM, index % 2 == 0 ? second : first);
        }
        running.set(false);
        reader.join();

        assertThat(failure.get()).isNull();
        assertThat(seen).containsExactlyInAnyOrder(first.info().digest(), second.info().digest());
    }

    @Test
    void discardRemovesOnlyDirectoriesNoAddressPointsTo() throws Exception {
        Path root = temporary.resolve("discard");
        FileTestDataStore store = store(root, 100);
        var live = store.prepare(PROBLEM, new ByteArrayInputStream(zip(Map.of("1.in", bytes("a"), "1.out", bytes("A")))));
        store.activate(PROBLEM, live);
        var pending = store.prepare(PROBLEM, new ByteArrayInputStream(zip(Map.of("1.in", bytes("b"), "1.out", bytes("B")))));

        store.discard(pending);
        store.discard(live);

        assertThat(root.resolve(".store").resolve(pending.generation())).doesNotExist();
        assertThat(root.resolve(".store").resolve(live.generation())).exists();
    }

    @Test
    void describeRefusesLocationsOutsideTheRootAndBrokenMetadata() throws Exception {
        Path root = temporary.resolve("describe");
        FileTestDataStore store = store(root, 100);
        var prepared = store.prepare(PROBLEM, new ByteArrayInputStream(zip(goldenEntries())));
        String location = store.activate(PROBLEM, prepared);

        for (String bad : List.of("/etc", "relative/path", "", root.resolve("../elsewhere").toString())) {
            assertThatThrownBy(() -> store.describe(bad)).as(bad).isInstanceOf(AssetException.class);
        }
        assertThatThrownBy(() -> store.describe(root.resolve(OTHER_PROBLEM).toString()))
                .isInstanceOfSatisfying(AssetException.class,
                        error -> assertThat(error.kind()).isEqualTo(TestDataStore.Kind.NOT_FOUND));

        Path metadata = Path.of(location).resolve("testdata.json");
        String valid = Files.readString(metadata);
        Path generation = root.resolve(".store").resolve(prepared.generation());
        Files.setPosixFilePermissions(generation.resolve("testdata.json"), PosixFilePermissions.fromString("rw-------"));
        for (String broken : List.of("not json", valid.replace(GOLDEN_DIGEST, "0".repeat(64)),
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
            assertThatThrownBy(() -> store.prepare(bad, new ByteArrayInputStream(archive))).as(bad)
                    .isInstanceOf(AssetException.class);
        }
    }

    @Test
    void removeDeletesTheAddressAndEveryGeneration() throws Exception {
        Path root = temporary.resolve("remove");
        FileTestDataStore store = store(root, 100);
        store.activate(PROBLEM, store.prepare(PROBLEM, new ByteArrayInputStream(zip(goldenEntries()))));
        store.activate(PROBLEM, store.prepare(PROBLEM, new ByteArrayInputStream(zip(Map.of(
                "1.in", bytes("a"), "1.out", bytes("A"))))));
        var other = store.prepare(OTHER_PROBLEM, new ByteArrayInputStream(zip(goldenEntries())));
        store.activate(OTHER_PROBLEM, other);

        store.remove(PROBLEM);

        assertThat(Files.exists(root.resolve(PROBLEM), LinkOption.NOFOLLOW_LINKS)).isFalse();
        try (var generations = Files.list(root.resolve(".store"))) {
            assertThat(generations.map(path -> path.getFileName().toString()).toList())
                    .containsExactly(other.generation());
        }
    }

    @Test
    void recoveryDeletesOnlyOldTemporaryFilesLinksAndDirectories() throws Exception {
        Path root = temporary.resolve("recovery");
        // 时钟拨到两天之后：此刻新建的文件都算「过期」，用将来的修改时间标出需要保留的「新」文件。
        // （macOS 上改不了符号链接自身的时间，所以不能反过来给链接设一个旧时间。）
        Clock later = Clock.fixed(Instant.now().plus(Duration.ofDays(2)), ZoneOffset.UTC);
        var properties = new TestDataStorageProperties(
                root, DataSize.ofMegabytes(1), DataSize.ofMegabytes(1), DataSize.ofKilobytes(512),
                20, 100, Duration.ofHours(1));
        FileTestDataStore store = new FileTestDataStore(properties, later, JsonMapper.builder().build());
        store.initialize();
        var live = store.prepare(PROBLEM, new ByteArrayInputStream(zip(goldenEntries())));
        String location = store.activate(PROBLEM, live);
        FileTime recent = FileTime.from(Instant.now().plus(Duration.ofDays(3)));

        Path staleUpload = root.resolve("tmp/" + java.util.UUID.randomUUID() + ".upload");
        Path freshUpload = root.resolve("tmp/" + java.util.UUID.randomUUID() + ".upload");
        Path staleLink = root.resolve("." + PROBLEM + ".stale.link");
        Path staleBuilding = root.resolve(".store/." + live.generation() + ".stale.tmp");
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
        var properties = new TestDataStorageProperties(
                root, DataSize.ofMegabytes(1), DataSize.ofMegabytes(1), DataSize.ofKilobytes(512),
                20, ratio, Duration.ofHours(1));
        var store = new FileTestDataStore(properties, CLOCK, JsonMapper.builder().build());
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
