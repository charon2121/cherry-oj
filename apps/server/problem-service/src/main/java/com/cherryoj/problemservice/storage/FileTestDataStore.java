package com.cherryoj.problemservice.storage;

import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.storage.TestDataArchiveReader.ValidatedArchive;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
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
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 把上传的 ZIP 校验后解成按测试数据协议组织的目录：
 *
 * <pre>
 * &lt;root&gt;/tmp/                          暂存上传的 ZIP，私有
 * &lt;root&gt;/.store/&lt;problemId&gt;-&lt;digest 前 16 位&gt;/   以内容寻址的真实目录：数据文件加 testdata.json
 * &lt;root&gt;/&lt;problemId&gt;                    题目的地址：指向上面某个目录的相对符号链接
 * </pre>
 *
 * 更新数据时先写出完整的新目录，再用一次重命名换掉符号链接，所以读取方不会读到一半新一半旧的数据。
 */
@Component
public final class FileTestDataStore implements TestDataStore {

    private static final Pattern PROBLEM_ID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final String STORE_DIRECTORY = ".store";
    private static final String TEMPORARY_DIRECTORY = "tmp";
    private static final int GENERATION_SUFFIX = 17; // "-" 加 16 位 digest
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
            createDirectory(root, SHARED_DIRECTORY);
            createDirectory(temporaryDirectory, PRIVATE_DIRECTORY);
            createDirectory(storeDirectory, SHARED_DIRECTORY);
        } catch (IOException error) {
            throw new IllegalStateException("Could not initialize test data storage", error);
        }
    }

    @Override
    public Prepared prepare(String problemId, InputStream zip) throws AssetException {
        requireProblemId(problemId);
        Path staged = temporaryDirectory.resolve(UUID.randomUUID() + ".upload");
        try {
            archives.stage(zip, staged);
            setPermissions(staged, PRIVATE_FILE);
            ValidatedArchive archive = archives.read(staged);
            TestDataMetadata.Document document = archive.document();
            String generation = problemId + "-" + document.digest().substring(0, 16);
            materialize(archive, document, generation);
            return new Prepared(problemId, generation, info(document));
        } catch (AssetException error) {
            throw error;
        } catch (IOException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_WRITE_FAILED", error);
        } finally {
            deletePath(staged);
        }
    }

    @Override
    public String activate(String problemId, Prepared prepared) throws AssetException {
        requireProblemId(problemId);
        Path target = storeDirectory.resolve(prepared.generation()).normalize();
        if (!storeDirectory.equals(target.getParent())
                || !prepared.generation().startsWith(problemId + "-")
                || !Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS)) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_GENERATION_MISSING");
        }
        Path address = root.resolve(problemId);
        if (Files.exists(address, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(address)) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_ADDRESS_NOT_A_LINK");
        }
        // 先建好新链接，再用一次重命名换掉旧链接：读取方任何时刻都能解析到一份完整的数据。
        Path link = root.resolve("." + problemId + "." + UUID.randomUUID() + ".link");
        try {
            Files.createSymbolicLink(link, Path.of(STORE_DIRECTORY, prepared.generation()));
            Files.move(link, address, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | UnsupportedOperationException error) {
            deletePath(link);
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_ACTIVATE_FAILED", error);
        }
        return address.toString();
    }

    @Override
    public void discard(Prepared prepared) {
        Path target = storeDirectory.resolve(prepared.generation()).normalize();
        if (!storeDirectory.equals(target.getParent())) {
            return;
        }
        // 内容寻址：同样的数据可能正被别的上传复用，只删没有被任何地址指向的目录。
        if (!isReferenced(target)) {
            deleteTree(target);
        }
    }

    @Override
    public void prune(String problemId) {
        if (!PROBLEM_ID.matcher(problemId).matches()) {
            return;
        }
        String current = currentGeneration(problemId);
        try (var children = Files.list(storeDirectory)) {
            List<Path> others = children.filter(
                            path -> path.getFileName().toString().startsWith(problemId + "-")
                                    && !path.getFileName().toString().equals(current)
                                    && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(FileTestDataStore::modified).reversed())
                    .toList();
            others.stream().skip(1).forEach(FileTestDataStore::deleteTree);
        } catch (IOException ignored) {
            // 清理是尽力而为，下一次上传还会再清理。
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
    public void remove(String problemId) {
        if (!PROBLEM_ID.matcher(problemId).matches()) {
            return;
        }
        deletePath(root.resolve(problemId));
        try (var children = Files.list(storeDirectory)) {
            children.filter(path -> path.getFileName().toString().startsWith(problemId + "-"))
                    .forEach(FileTestDataStore::deleteTree);
        } catch (IOException ignored) {
            // 残留目录没有任何地址指向，读不到，之后的清理会处理。
        }
    }

    @Override
    public RecoveryResult recover() {
        Instant cutoff = clock.instant().minus(properties.staleAge());
        int files = deleteStale(temporaryDirectory, cutoff, ".upload", false);
        files += deleteStale(root, cutoff, ".link", false);
        int directories = deleteStale(storeDirectory, cutoff, ".tmp", true);
        return new RecoveryResult(files, directories);
    }

    private void materialize(ValidatedArchive archive, TestDataMetadata.Document document, String generation)
            throws AssetException, IOException {
        Path target = storeDirectory.resolve(generation);
        // 内容寻址：同样的数据已经写好就直接复用。
        if (Files.isRegularFile(target.resolve(TestDataMetadata.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path building = storeDirectory.resolve("." + generation + "." + UUID.randomUUID() + ".tmp");
        Files.createDirectory(building);
        setPermissions(building, SHARED_DIRECTORY);
        try {
            archive.extractTo(building);
            for (TestDataMetadata.TestcaseEntry testcase : document.testcases()) {
                setPermissions(building.resolve(testcase.name() + ".in"), SHARED_FILE);
                setPermissions(building.resolve(testcase.name() + ".out"), SHARED_FILE);
            }
            // testdata.json 最后写：读取方把它当作「这份数据已完整」的标记。
            Path metadata = building.resolve(TestDataMetadata.FILE_NAME);
            Files.write(metadata, json.writeValueAsBytes(document), StandardOpenOption.CREATE_NEW);
            setPermissions(metadata, SHARED_FILE);
            try {
                Files.move(building, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException error) {
                // 另一个上传同时写好了同样的内容：目录已经在了，丢掉自己这份。
                if (!Files.isRegularFile(target.resolve(TestDataMetadata.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
                    throw error;
                }
            }
        } finally {
            deleteTree(building);
        }
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

    private String currentGeneration(String problemId) {
        try {
            return Files.readSymbolicLink(root.resolve(problemId)).getFileName().toString();
        } catch (IOException | UnsupportedOperationException error) {
            return "";
        }
    }

    private boolean isReferenced(Path generation) {
        String name = generation.getFileName().toString();
        int problemEnd = name.length() - GENERATION_SUFFIX;
        return problemEnd > 0
                && currentGeneration(name.substring(0, problemEnd)).equals(name);
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
            // 恢复是尽力而为，只会删上面三种明确命名的临时项，不会超出这几个目录。
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
