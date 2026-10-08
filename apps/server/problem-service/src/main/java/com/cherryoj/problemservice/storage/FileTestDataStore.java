package com.cherryoj.problemservice.storage;

import com.cherryoj.problemservice.api.TestDataDtos.Manifest;
import com.cherryoj.problemservice.api.TestDataDtos.ManifestFile;
import com.cherryoj.problemservice.config.TestDataStorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
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

    private static final Pattern FILE_NAME = Pattern.compile(
            "^([A-Za-z0-9][A-Za-z0-9._-]{0,123})\\.(in|out)$");
    private static final Pattern WRAPPER_NAME = Pattern.compile(
            "^[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,127}$");
    private static final Pattern PROBLEM_ID = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
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
    private final Clock clock;
    private final ObjectMapper json;
    private Path root;
    private Path temporaryDirectory;
    private Path storeDirectory;

    public FileTestDataStore(TestDataStorageProperties properties, Clock clock, ObjectMapper json) {
        this.properties = properties;
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
        }
        catch (IOException error) {
            throw new IllegalStateException("Could not initialize test data storage", error);
        }
    }

    @Override
    public Prepared prepare(String problemId, InputStream zip) throws AssetException {
        requireProblemId(problemId);
        Path staged = temporaryDirectory.resolve(UUID.randomUUID() + ".upload");
        try {
            try (var output = Files.newOutputStream(staged)) {
                copyLimited(zip, output, properties.maxArchiveSize().toBytes());
            }
            setPermissions(staged, PRIVATE_FILE);
            Validation validation = validateArchive(staged);
            TestDataMetadata.Document document = documentFor(validation);
            String generation = problemId + "-" + document.digest().substring(0, 16);
            materialize(staged, validation, document, generation);
            return new Prepared(problemId, generation, info(document));
        }
        catch (AssetException error) {
            throw error;
        }
        catch (IOException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_WRITE_FAILED", error);
        }
        finally {
            deletePath(staged);
        }
    }

    @Override
    public String activate(String problemId, Prepared prepared) throws AssetException {
        requireProblemId(problemId);
        Path target = storeDirectory.resolve(prepared.generation()).normalize();
        if (!storeDirectory.equals(target.getParent()) || !prepared.generation().startsWith(problemId + "-")
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
        }
        catch (IOException | UnsupportedOperationException error) {
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
            List<Path> others = children
                    .filter(path -> path.getFileName().toString().startsWith(problemId + "-")
                            && !path.getFileName().toString().equals(current)
                            && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .sorted(Comparator.comparing(FileTestDataStore::modified).reversed())
                    .toList();
            others.stream().skip(1).forEach(FileTestDataStore::deleteTree);
        }
        catch (IOException ignored) {
            // 清理是尽力而为，下一次上传还会再清理。
        }
    }

    @Override
    public Info describe(String location) throws AssetException {
        Path directory;
        try {
            directory = Path.of(location).normalize();
        }
        catch (InvalidPathException | NullPointerException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_LOCATION_INVALID");
        }
        // 地址都是本服务自己写出的；读取之前确认它没有指到存储根之外。
        if (!directory.isAbsolute() || !directory.startsWith(root)) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_LOCATION_INVALID");
        }
        byte[] bytes;
        try (InputStream input = Files.newInputStream(directory.resolve(TestDataMetadata.FILE_NAME))) {
            bytes = input.readNBytes(MAX_METADATA_BYTES + 1);
        }
        catch (NoSuchFileException error) {
            throw new AssetException(Kind.NOT_FOUND, "TEST_DATA_NOT_FOUND");
        }
        catch (IOException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_READ_FAILED", error);
        }
        if (bytes.length > MAX_METADATA_BYTES) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_METADATA_TOO_LARGE");
        }
        TestDataMetadata.Document document;
        try {
            document = json.readValue(bytes, TestDataMetadata.Document.class);
        }
        catch (RuntimeException error) {
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
        }
        catch (IOException ignored) {
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

    private void materialize(Path archive, Validation validation, TestDataMetadata.Document document,
            String generation) throws AssetException, IOException {
        Path target = storeDirectory.resolve(generation);
        // 内容寻址：同样的数据已经写好就直接复用。
        if (Files.isRegularFile(target.resolve(TestDataMetadata.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        Path building = storeDirectory.resolve("." + generation + "." + UUID.randomUUID() + ".tmp");
        Files.createDirectory(building);
        setPermissions(building, SHARED_DIRECTORY);
        try {
            extract(archive, validation, building);
            // testdata.json 最后写：读取方把它当作「这份数据已完整」的标记。
            Path metadata = building.resolve(TestDataMetadata.FILE_NAME);
            Files.write(metadata, json.writeValueAsBytes(document), StandardOpenOption.CREATE_NEW);
            setPermissions(metadata, SHARED_FILE);
            try {
                Files.move(building, target, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (IOException error) {
                // 另一个上传同时写好了同样的内容：目录已经在了，丢掉自己这份。
                if (!Files.isRegularFile(target.resolve(TestDataMetadata.FILE_NAME), LinkOption.NOFOLLOW_LINKS)) {
                    throw error;
                }
            }
        }
        finally {
            deleteTree(building);
        }
    }

    private static void extract(Path archivePath, Validation validation, Path directory)
            throws AssetException, IOException {
        try (ZipFile archive = ZipFile.builder().setPath(archivePath).setCharset(StandardCharsets.UTF_8).get()) {
            for (ValidatedFile file : validation.files()) {
                ZipArchiveEntry entry = archive.getEntry(file.entryName());
                if (entry == null) {
                    throw new AssetException(Kind.INVALID_ARCHIVE, "TEST_DATA_ARCHIVE_CHANGED");
                }
                Path output = directory.resolve(file.name());
                MessageDigest digest = sha256();
                long size = 0;
                try (InputStream input = archive.getInputStream(entry);
                        OutputStream out = Files.newOutputStream(output, StandardOpenOption.CREATE_NEW)) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = input.read(buffer)) != -1) {
                        size += read;
                        digest.update(buffer, 0, read);
                        out.write(buffer, 0, read);
                    }
                }
                // 校验时读到的内容与这里写出的必须一致，否则不能当作已校验的数据。
                if (size != file.size() || !HexFormat.of().formatHex(digest.digest()).equals(file.sha256())) {
                    throw new AssetException(Kind.INVALID_ARCHIVE, "TEST_DATA_ARCHIVE_CHANGED");
                }
                setPermissions(output, SHARED_FILE);
            }
        }
    }

    private static TestDataMetadata.Document documentFor(Validation validation) {
        Map<String, Map<String, ValidatedFile>> byTestcase = new HashMap<>();
        for (ValidatedFile file : validation.files()) {
            int dot = file.name().lastIndexOf('.');
            byTestcase.computeIfAbsent(file.name().substring(0, dot), ignored -> new HashMap<>())
                    .put(file.name().substring(dot + 1), file);
        }
        List<TestDataMetadata.TestcaseEntry> testcases = byTestcase.keySet().stream()
                .sorted(TestDataMetadata.defaultOrder())
                .map(name -> new TestDataMetadata.TestcaseEntry(name,
                        entry(byTestcase.get(name).get("in")), entry(byTestcase.get(name).get("out"))))
                .toList();
        return TestDataMetadata.Document.of(testcases);
    }

    private static TestDataMetadata.FileEntry entry(ValidatedFile file) {
        return new TestDataMetadata.FileEntry(file.size(), file.sha256());
    }

    private static Info info(TestDataMetadata.Document document) {
        List<ManifestFile> files = new ArrayList<>();
        for (TestDataMetadata.TestcaseEntry entry : document.testcases()) {
            files.add(new ManifestFile(entry.name() + ".in", entry.input().sizeBytes(), entry.input().sha256()));
            files.add(new ManifestFile(entry.name() + ".out", entry.output().sizeBytes(), entry.output().sha256()));
        }
        return new Info(document.digest(), document.testcaseCount(), document.totalBytes(),
                new Manifest(document.testcaseCount(), document.totalBytes(), List.copyOf(files)));
    }

    private String currentGeneration(String problemId) {
        try {
            return Files.readSymbolicLink(root.resolve(problemId)).getFileName().toString();
        }
        catch (IOException | UnsupportedOperationException error) {
            return "";
        }
    }

    private boolean isReferenced(Path generation) {
        String name = generation.getFileName().toString();
        int problemEnd = name.length() - GENERATION_SUFFIX;
        return problemEnd > 0 && currentGeneration(name.substring(0, problemEnd)).equals(name);
    }

    private Validation validateArchive(Path path) throws AssetException {
        List<ValidatedFile> files = new ArrayList<>();
        Map<String, Set<String>> pairs = new HashMap<>();
        Set<String> physicalNames = new HashSet<>();
        Set<String> logicalNames = new HashSet<>();
        Set<String> directoryMarkers = new HashSet<>();
        Set<String> metadataPrefixes = new HashSet<>();
        long totalBytes = 0;
        int entryCount = 0;
        String wrapper = null;
        boolean logicalRootSelected = false;
        try (ZipFile archive = ZipFile.builder().setPath(path).setCharset(StandardCharsets.UTF_8).get()) {
            Enumeration<ZipArchiveEntry> entries = archive.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (++entryCount > properties.maxFiles()) {
                    throw invalid("TEST_DATA_TOO_MANY_FILES");
                }
                String name = entry.getName();
                if (!isSafeEntryPath(name) || !physicalNames.add(name)) {
                    throw invalid("TEST_DATA_INVALID_ZIP_ENTRY");
                }
                MetadataPath metadata = metadataPath(name);
                if (metadata != null) {
                    if ((!entry.isDirectory() && (!isRegularFile(entry) || !archive.canReadEntryData(entry)))
                            || entry.isUnixSymlink()) {
                        throw invalid("TEST_DATA_INVALID_ZIP_ENTRY");
                    }
                    if (metadata.wrapper() != null) metadataPrefixes.add(metadata.wrapper());
                    continue;
                }
                if (entry.isDirectory()) {
                    if (entry.isUnixSymlink()) throw invalid("TEST_DATA_INVALID_ZIP_ENTRY");
                    directoryMarkers.add(trimTrailingSlash(name));
                    continue;
                }
                TestcasePath testcasePath = testcasePath(name);
                if (testcasePath == null || !isRegularFile(entry) || !archive.canReadEntryData(entry)) {
                    throw invalid("TEST_DATA_INVALID_ZIP_ENTRY");
                }
                if (!logicalRootSelected) {
                    wrapper = testcasePath.wrapper();
                    logicalRootSelected = true;
                }
                else if (!Objects.equals(wrapper, testcasePath.wrapper())) {
                    throw invalid("TEST_DATA_INVALID_ZIP_ENTRY");
                }
                if (!logicalNames.add(testcasePath.logicalName())) {
                    throw invalid("TEST_DATA_INVALID_ZIP_ENTRY");
                }
                if (entry.getSize() > properties.maxEntrySize().toBytes()) {
                    throw tooLarge("TEST_DATA_ENTRY_TOO_LARGE");
                }
                EntryDigest digest = digestEntry(archive, entry);
                totalBytes = Math.addExact(totalBytes, digest.size());
                if (totalBytes > properties.maxExpandedSize().toBytes()) {
                    throw tooLarge("TEST_DATA_EXPANDED_SIZE_EXCEEDED");
                }
                long compressed = entry.getCompressedSize();
                if (digest.size() > 0 && (compressed <= 0
                        || (double) digest.size() / compressed > properties.maxCompressionRatio())) {
                    throw tooLarge("TEST_DATA_COMPRESSION_RATIO_EXCEEDED");
                }
                files.add(new ValidatedFile(testcasePath.logicalName(), name, digest.size(), digest.sha256()));
                pairs.computeIfAbsent(testcasePath.testcaseName(), ignored -> new HashSet<>()).add(testcasePath.extension());
            }
        }
        catch (AssetException error) {
            throw error;
        }
        catch (ArithmeticException error) {
            throw tooLarge("TEST_DATA_EXPANDED_SIZE_EXCEEDED");
        }
        catch (IOException | RuntimeException error) {
            throw new AssetException(Kind.INVALID_ARCHIVE, "TEST_DATA_INVALID_ZIP", error);
        }
        String selectedWrapper = wrapper;
        if (files.isEmpty()
                || directoryMarkers.stream().anyMatch(directory -> !Objects.equals(directory, selectedWrapper))
                || metadataPrefixes.stream()
                        .anyMatch(prefix -> !prefix.isEmpty() && !Objects.equals(prefix, selectedWrapper))) {
            throw invalid("TEST_DATA_INVALID_ZIP_ENTRY");
        }
        if (pairs.values().stream().anyMatch(extensions -> !extensions.equals(Set.of("in", "out")))) {
            throw invalid("TEST_DATA_CASE_PAIR_REQUIRED");
        }
        if (pairs.size() > TestDataMetadata.MAX_CASES) {
            throw invalid("TEST_DATA_TOO_MANY_FILES");
        }
        return new Validation(totalBytes, List.copyOf(files));
    }

    private EntryDigest digestEntry(ZipFile archive, ZipArchiveEntry entry) throws IOException, AssetException {
        MessageDigest digest = sha256();
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        byte[] bytes = new byte[8192];
        ByteBuffer encoded = ByteBuffer.allocate(16384);
        CharBuffer decoded = CharBuffer.allocate(8192);
        long size = 0;
        try (InputStream input = archive.getInputStream(entry)) {
            int read;
            while ((read = input.read(bytes)) != -1) {
                size += read;
                if (size > properties.maxEntrySize().toBytes()) {
                    throw tooLarge("TEST_DATA_ENTRY_TOO_LARGE");
                }
                digest.update(bytes, 0, read);
                if (encoded.remaining() < read) {
                    decode(decoder, encoded, decoded, false);
                }
                encoded.put(bytes, 0, read);
                decode(decoder, encoded, decoded, false);
            }
            decode(decoder, encoded, decoded, true);
            decoder.flush(decoded.clear());
        }
        catch (CharacterCodingException error) {
            throw invalid("TEST_DATA_FILE_NOT_UTF8");
        }
        return new EntryDigest(size, HexFormat.of().formatHex(digest.digest()));
    }

    private static void decode(
            java.nio.charset.CharsetDecoder decoder, ByteBuffer encoded, CharBuffer decoded, boolean end)
            throws CharacterCodingException {
        encoded.flip();
        while (true) {
            var result = decoder.decode(encoded, decoded.clear(), end);
            if (result.isError()) result.throwException();
            if (!result.isOverflow()) break;
        }
        encoded.compact();
    }

    private static boolean isRegularFile(ZipArchiveEntry entry) {
        if (entry.isDirectory() || entry.isUnixSymlink()) return false;
        int mode = entry.getUnixMode();
        return mode == 0 || (mode & UnixStat.FILE_TYPE_FLAG) == UnixStat.FILE_FLAG;
    }

    private static boolean isSafeEntryPath(String name) {
        if (name == null || name.isBlank() || name.length() > 512 || name.startsWith("/")
                || name.indexOf('\\') >= 0 || name.indexOf('\0') >= 0) {
            return false;
        }
        String candidate = trimTrailingSlash(name);
        if (candidate.isEmpty()) return false;
        for (String component : candidate.split("/", -1)) {
            if (component.isEmpty() || component.equals(".") || component.equals("..")
                    || component.chars().anyMatch(Character::isISOControl)) {
                return false;
            }
        }
        return true;
    }

    private static MetadataPath metadataPath(String name) {
        String candidate = trimTrailingSlash(name);
        if (candidate.equals("__MACOSX") || candidate.startsWith("__MACOSX/")) {
            return new MetadataPath(null);
        }
        int slash = candidate.indexOf('/');
        if (slash >= 0 && candidate.indexOf('/', slash + 1) >= 0) return null;
        String leaf = slash < 0 ? candidate : candidate.substring(slash + 1);
        if (!leaf.equals(".DS_Store") && !leaf.startsWith("._")) return null;
        return new MetadataPath(slash < 0 ? "" : candidate.substring(0, slash));
    }

    private static TestcasePath testcasePath(String name) {
        int slash = name.indexOf('/');
        if (slash >= 0 && name.indexOf('/', slash + 1) >= 0) return null;
        String wrapper = slash < 0 ? null : name.substring(0, slash);
        String logicalName = slash < 0 ? name : name.substring(slash + 1);
        if (wrapper != null && !WRAPPER_NAME.matcher(wrapper).matches()) return null;
        Matcher matcher = FILE_NAME.matcher(logicalName);
        return matcher.matches()
                ? new TestcasePath(wrapper, logicalName, matcher.group(1), matcher.group(2))
                : null;
    }

    private static String trimTrailingSlash(String name) {
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static void copyLimited(InputStream input, OutputStream output, long limit)
            throws IOException, AssetException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw tooLarge("TEST_DATA_ARCHIVE_TOO_LARGE");
            output.write(buffer, 0, read);
        }
    }

    private int deleteStale(Path directory, Instant cutoff, String suffix, boolean directories) {
        int deleted = 0;
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) {
                String name = child.getFileName().toString();
                boolean right = directories
                        ? Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS)
                        : !Files.isDirectory(child, LinkOption.NOFOLLOW_LINKS);
                if (!name.endsWith(suffix) || !right
                        || !modified(child).toInstant().isBefore(cutoff)) continue;
                if (directories) {
                    deleteTree(child);
                    deleted++;
                }
                else if (deletePath(child)) {
                    deleted++;
                }
            }
        }
        catch (IOException ignored) {
            // 恢复是尽力而为，只会删上面三种明确命名的临时项，不会超出这几个目录。
        }
        return deleted;
    }

    private static FileTime modified(Path path) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS);
        }
        catch (IOException error) {
            return FileTime.fromMillis(0);
        }
    }

    private static void requireProblemId(String problemId) throws AssetException {
        if (problemId == null || !PROBLEM_ID.matcher(problemId).matches()) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_PROBLEM_ID_INVALID");
        }
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
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
        }
        catch (UnsupportedOperationException ignored) {
            // 非 POSIX 平台依靠部署目录的 ACL。
        }
    }

    private static boolean deletePath(Path path) {
        try {
            return Files.deleteIfExists(path);
        }
        catch (IOException ignored) {
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
        }
        catch (IOException ignored) {
            // 残留的临时目录由 recover 清理。
        }
    }

    private static AssetException invalid(String code) {
        return new AssetException(Kind.INVALID_ARCHIVE, code);
    }

    private static AssetException tooLarge(String code) {
        return new AssetException(Kind.PAYLOAD_TOO_LARGE, code);
    }

    private record Validation(long totalBytes, List<ValidatedFile> files) {
    }

    /** name 是协议里的文件名（不含包装目录），entryName 是它在 ZIP 里的完整条目名。 */
    private record ValidatedFile(String name, String entryName, long size, String sha256) {
    }

    private record EntryDigest(long size, String sha256) {
    }

    private record MetadataPath(String wrapper) {
    }

    private record TestcasePath(String wrapper, String logicalName, String testcaseName, String extension) {
    }
}
