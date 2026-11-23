package com.cherryoj.problemservice.storage;

import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.storage.TestDataArchiveReader.ValidatedArchive;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.FileChannel;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 把上传的 ZIP 校验后解成按测试数据协议组织的目录：
 *
 * <pre>
 * &lt;root&gt;/tmp/                          暂存上传的 ZIP，私有
 * &lt;root&gt;/.store/published/&lt;problemId&gt;-&lt;uploadId&gt;/  每次上传的独立目录
 * &lt;root&gt;/&lt;problemId&gt;                    旧安装保留的固定地址，仍指向 .store 下的旧目录
 * </pre>
 *
 * 新上传只交付独立目录。当前数据由题目行发布，旧地址可以继续读取自己的内容。
 */
@Component
public final class FileTestDataStore implements TestDataStore {

    private static final Logger LOG = LoggerFactory.getLogger(FileTestDataStore.class);
    private static final String UUID_FORMAT = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern PROBLEM_ID = Pattern.compile(UUID_FORMAT);
    private static final Pattern PUBLISHED_GENERATION = Pattern.compile("(" + UUID_FORMAT + ")-" + UUID_FORMAT);
    private static final Pattern LEGACY_GENERATION = Pattern.compile("(" + UUID_FORMAT + ")-[0-9a-f]{16}");
    private static final String STORE_DIRECTORY = ".store";
    private static final String TEMPORARY_DIRECTORY = "tmp";
    // 覆盖正式判题和标定的最长调用预算。计时始于在题目行锁内首次确认地址已退役。
    private static final Duration RETIRED_RETENTION = Duration.ofHours(24);
    private static final int MAX_METADATA_BYTES = 1 << 20;
    // judge 与 problem-service 通常不是同一个系统用户，所以测试数据目录要让其他用户可读；
    // 暂存的 ZIP 只有本服务自己读，保持私有。
    private static final Set<PosixFilePermission> SHARED_DIRECTORY = PosixFilePermissions.fromString("rwxr-xr-x");
    private static final Set<PosixFilePermission> SHARED_FILE = PosixFilePermissions.fromString("rw-r--r--");
    private static final Set<PosixFilePermission> PRIVATE_DIRECTORY = PosixFilePermissions.fromString("rwx------");
    private static final Set<PosixFilePermission> PRIVATE_FILE = PosixFilePermissions.fromString("r--------");

    private final TestDataStorageProperties properties;
    private final TestDataArchiveReader archives;
    private final Clock clock;
    private final ObjectMapper json;
    private Path root;
    private Path temporaryDirectory;
    private Path storeDirectory;
    private Path publishedDirectory;

    public FileTestDataStore(TestDataStorageProperties properties, Clock clock, ObjectMapper json) {
        this.properties = properties;
        this.archives = new TestDataArchiveReader(properties);
        this.clock = clock;
        this.json = json;
    }

    @PostConstruct
    void initialize() {
        try {
            root = properties.root().toAbsolutePath().normalize();
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
                throw new IllegalStateException("Test data root must not be a symbolic link");
            }
            temporaryDirectory = root.resolve(TEMPORARY_DIRECTORY);
            storeDirectory = root.resolve(STORE_DIRECTORY);
            publishedDirectory = storeDirectory.resolve("published");
            createStorageRoot(root);
            createDirectory(temporaryDirectory, PRIVATE_DIRECTORY);
            createDirectory(storeDirectory, SHARED_DIRECTORY);
            createDirectory(publishedDirectory, SHARED_DIRECTORY);
            forceDirectory(storeDirectory);
            forceDirectory(root);
        } catch (IOException error) {
            throw new IllegalStateException("Could not initialize test data storage", error);
        }
    }

    @Override
    public Prepared prepare(String problemId, InputStream zip) throws AssetException {
        requireProblemId(problemId);
        Path staged = temporaryDirectory.resolve(UUID.randomUUID() + ".upload");
        String generation = problemId + "-" + UUID.randomUUID();
        Path building = stagingDirectory(generation);
        boolean prepared = false;
        try {
            archives.stage(zip, staged);
            setPermissions(staged, PRIVATE_FILE);
            ValidatedArchive archive = archives.read(staged);
            TestDataMetadata.Document document = archive.document();
            materialize(archive, document, building);
            prepared = true;
            return new Prepared(problemId, generation, info(document));
        } catch (AssetException error) {
            throw error;
        } catch (IOException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_WRITE_FAILED", error);
        } finally {
            deletePath(staged);
            if (!prepared) deleteTree(building);
        }
    }

    @Override
    public String seal(Prepared prepared) throws AssetException {
        if (!belongsTo(prepared.problemId(), prepared.generation(), PUBLISHED_GENERATION)) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_GENERATION_INVALID");
        }
        Path building = stagingDirectory(prepared.generation());
        Path target = publishedDirectory.resolve(prepared.generation());
        try {
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isDirectory(building, LinkOption.NOFOLLOW_LINKS)
                    || !Files.isRegularFile(building.resolve(TestDataMetadata.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
                throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_GENERATION_UNAVAILABLE");
            }
            setPermissions(building, SHARED_DIRECTORY);
            forceDirectory(building);
            Files.move(building, target, StandardCopyOption.ATOMIC_MOVE);
            forceDirectory(publishedDirectory);
        } catch (IOException | UnsupportedOperationException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_SEAL_FAILED", error);
        }
        return target.toString();
    }

    @Override
    public void discard(Prepared prepared) {
        if (belongsTo(prepared.problemId(), prepared.generation(), PUBLISHED_GENERATION)) {
            deleteTree(stagingDirectory(prepared.generation()));
        }
    }

    @Override
    public List<StoredDirectory> storedDirectories() {
        var directories = new ArrayList<StoredDirectory>();
        scanDirectories(publishedDirectory, false, directories);
        scanDirectories(storeDirectory, true, directories);
        return directories;
    }

    private void scanDirectories(Path parent, boolean legacy, List<StoredDirectory> directories) {
        Pattern pattern = legacy ? LEGACY_GENERATION : PUBLISHED_GENERATION;
        try (var children = Files.list(parent)) {
            children.filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .forEach(path -> {
                        String name = path.getFileName().toString();
                        var match = pattern.matcher(name);
                        if (match.matches()) directories.add(new StoredDirectory(match.group(1), name, legacy));
                    });
        } catch (IOException error) {
            LOG.warn("testdata.cleanup.scan_failed legacy={}", legacy);
        }
    }

    @Override
    public void reclaim(StoredDirectory directory, Predicate<String> isReferenced) {
        boolean legacy = directory.legacy();
        String generation = directory.generation();
        if (!belongsTo(directory.problemId(), generation, legacy ? LEGACY_GENERATION : PUBLISHED_GENERATION)) return;
        Path path = (legacy ? storeDirectory : publishedDirectory).resolve(generation);
        Path marker = temporaryDirectory.resolve(generation + ".retired");
        Path alias = root.resolve(directory.problemId());
        try {
            boolean hasAlias = legacy
                    && Files.isSymbolicLink(alias)
                    && Files.readSymbolicLink(alias).equals(Path.of(STORE_DIRECTORY, generation));
            if (isReferenced.test(path.toString()) || (hasAlias && isReferenced.test(alias.toString()))) {
                Files.deleteIfExists(marker);
                return;
            }
            // 有的目录刚被替换或提交结果曾不明确。第一次看到无引用时只记时间，保留期后才删除。
            try {
                Files.createFile(marker);
                Files.setLastModifiedTime(marker, FileTime.from(clock.instant()));
                return;
            } catch (FileAlreadyExistsException ignored) {
                // 使用之前记录的时间，重启后也按同一保留期处理。
            }
            Instant retiredAt =
                    Files.getLastModifiedTime(marker, LinkOption.NOFOLLOW_LINKS).toInstant();
            if (clock.instant().isBefore(retiredAt.plus(RETIRED_RETENTION))) return;
            if (hasAlias) Files.deleteIfExists(alias);
            deleteTree(path);
            if (Files.notExists(path, LinkOption.NOFOLLOW_LINKS)) Files.deleteIfExists(marker);
        } catch (IOException error) {
            LOG.warn("testdata.cleanup.reclaim_failed problemId={}", directory.problemId());
        }
    }

    @Override
    public Info describe(String location) throws AssetException {
        Path directory;
        try {
            directory = Path.of(location).normalize();
        } catch (InvalidPathException | NullPointerException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_LOCATION_INVALID");
        }
        // 地址都是本服务自己写出的；读取之前确认它没有指到存储根之外。
        if (!directory.isAbsolute() || !directory.startsWith(root)) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_LOCATION_INVALID");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(directory.resolve(TestDataMetadata.FILE_NAME))) {
            bytes = input.readNBytes(MAX_METADATA_BYTES + 1);
        } catch (NoSuchFileException error) {
            throw new AssetException(Kind.NOT_FOUND, "TEST_DATA_NOT_FOUND");
        } catch (IOException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_READ_FAILED", error);
        }
        if (bytes.length > MAX_METADATA_BYTES) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_METADATA_TOO_LARGE");
        }
        TestDataMetadata.Document document;
        try {
            document = json.readValue(bytes, TestDataMetadata.Document.class);
        } catch (RuntimeException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_METADATA_INVALID", error);
        }
        if (document.problem() != null) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_METADATA_INVALID");
        }
        return info(document);
    }

    @Override
    public RecoveryResult recover() {
        Instant cutoff = clock.instant().minus(properties.staleAge());
        int files = deleteStale(temporaryDirectory, cutoff, ".upload", false);
        files += deleteStale(root, cutoff, ".link", false);
        int directories = deleteStale(storeDirectory, cutoff, ".tmp", true)
                + deleteStale(publishedDirectory, cutoff, ".tmp", true);
        return new RecoveryResult(files, directories);
    }

    private void materialize(ValidatedArchive archive, TestDataMetadata.Document document, Path building)
            throws IOException, AssetException {
        Files.createDirectory(building);
        setPermissions(building, PRIVATE_DIRECTORY);
        archive.extractTo(building);
        for (TestDataMetadata.TestcaseEntry testcase : document.testcases()) {
            sealFile(building.resolve(testcase.name() + ".in"));
            sealFile(building.resolve(testcase.name() + ".out"));
        }
        // 元数据最后写；数据与目录都落盘后，数据库才能发布这个地址。
        Path metadata = building.resolve(TestDataMetadata.FILE_NAME);
        Files.write(metadata, json.writeValueAsBytes(document), StandardOpenOption.CREATE_NEW);
        sealFile(metadata);
    }

    private static void sealFile(Path path) throws IOException {
        setPermissions(path, SHARED_FILE);
        try (var channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
            channel.force(true);
        }
    }

    private static void forceDirectory(Path path) throws IOException {
        try (var channel = FileChannel.open(path, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    private Path stagingDirectory(String generation) {
        return publishedDirectory.resolve("." + generation + ".tmp");
    }

    private static boolean belongsTo(String problemId, String generation, Pattern pattern) {
        return problemId != null
                && generation != null
                && pattern.matcher(generation).matches()
                && generation.startsWith(problemId + "-");
    }

    private static Info info(TestDataMetadata.Document document) {
        long maxTestcaseIoBytes = document.testcases().stream()
                .mapToLong(testcase ->
                        testcase.input().sizeBytes() + testcase.output().sizeBytes())
                .max()
                .orElse(0L);
        return new Info(
                document.digest(),
                document.testcaseCount(),
                document.totalBytes(),
                maxTestcaseIoBytes,
                document.testcases());
    }

    private int deleteStale(Path directory, Instant cutoff, String suffix, boolean directories) {
        int deleted = 0;
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                boolean right = directories
                        ? Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        : !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
                if (!name.endsWith(suffix)
                        || !right
                        || !modified(child).toInstant().isBefore(cutoff)) continue;
                if (directories) {
                    deleteTree(child);
                    deleted++;
                } else if (deletePath(child)) {
                    deleted++;
                }
            }
        } catch (IOException ignored) {
            // 恢复只处理明确命名的临时项，失败时下次再试。
        }
        return deleted;
    }

    private static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
        } catch (IOException error) {
            return FileTime.fromMillis(0);
        }
    }

    private static void requireProblemId(String problemId) throws AssetException {
        if (problemId == null || !PROBLEM_ID.matcher(problemId).matches()) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_PROBLEM_ID_INVALID");
        }
    }

    private static void createStorageRoot(Path directory) throws IOException {
        var parentsOfNewDirectories = new ArrayList<Path>();
        for (Path path = directory;
                path.getParent() != null && Files.notExists(path, LinkOption.NOFOLLOW_LINKS);
                path = path.getParent()) {
            parentsOfNewDirectories.add(path.getParent());
        }
        createDirectory(directory, SHARED_DIRECTORY);
        // 首次启动可能创建多级目录。父目录也要落盘，才能在断电后找到已发布的数据。
        for (Path parent : parentsOfNewDirectories) forceDirectory(parent);
    }

    private static void createDirectory(Path path, Set<PosixFilePermission> permissions) throws IOException {
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Not a directory: " + path);
        }
        setPermissions(path, permissions);
    }

    private static void setPermissions(Path path, Set<PosixFilePermission> permissions) throws IOException {
        try {
            Files.setPosixFilePermissions(path, permissions);
        } catch (UnsupportedOperationException ignored) {
            // 非 POSIX 平台依靠部署目录的 ACL。
        }
    }

    private static boolean deletePath(Path path) {
        try {
            return Files.deleteIfExists(path);
        } catch (IOException ignored) {
            return false;
        }
    }

    /** 递归删除，不跟随符号链接。 */
    private static void deleteTree(Path path) {
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.deleteIfExists(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException error) throws IOException {
                    Files.deleteIfExists(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {
            // 残留的临时目录由 recover 清理。
        }
    }
}
