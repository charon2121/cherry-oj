package com.cherryoj.problemservice.storage;

import com.cherryoj.problemservice.api.TestDataDtos.Manifest;
import com.cherryoj.problemservice.api.TestDataDtos.ManifestFile;
import com.cherryoj.problemservice.config.TestDataStorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
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
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.stereotype.Component;

@Component
public final class FileTestDataAssetStore implements TestDataAssetStore {

    private static final Pattern FILE_NAME = Pattern.compile(
            "^([A-Za-z0-9][A-Za-z0-9._-]{0,127})\\.(in|out)$");
    private static final Pattern STORAGE_REF = Pattern.compile("^assets/([0-9a-f-]{36})\\.zip$");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ);

    private final TestDataStorageProperties properties;
    private final Clock clock;
    private Path root;
    private Path temporaryDirectory;
    private Path assetDirectory;

    public FileTestDataAssetStore(TestDataStorageProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @PostConstruct
    void initialize() {
        try {
            root = properties.root().toAbsolutePath().normalize();
            if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
                throw new IllegalStateException("Test data root must not be a symbolic link");
            }
            temporaryDirectory = root.resolve("tmp");
            assetDirectory = root.resolve("assets");
            createPrivateDirectory(root);
            createPrivateDirectory(temporaryDirectory);
            createPrivateDirectory(assetDirectory);
        }
        catch (IOException error) {
            throw new IllegalStateException("Could not initialize test data storage", error);
        }
    }

    @Override
    public StagedAsset stage(String assetId, InputStream source) throws AssetException {
        requireUuid(assetId);
        Path temporary = temporaryPath(assetId);
        try {
            Files.deleteIfExists(temporary);
            MessageDigest archiveDigest = sha256();
            long archiveBytes;
            try (var output = Files.newOutputStream(temporary)) {
                archiveBytes = copyLimited(source, output, archiveDigest, properties.maxArchiveSize().toBytes());
            }
            setOwnerOnly(temporary, false);
            Validation validation = validateArchive(temporary);
            return new StagedAsset(
                    assetId,
                    relative(temporary),
                    "assets/" + assetId + ".zip",
                    HexFormat.of().formatHex(archiveDigest.digest()),
                    archiveBytes,
                    validation.caseCount(),
                    validation.totalBytes(),
                    validation.manifest());
        }
        catch (AssetException error) {
            deletePath(temporary);
            throw error;
        }
        catch (IOException error) {
            deletePath(temporary);
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_WRITE_FAILED", error);
        }
    }

    @Override
    public void seal(StagedAsset staged) throws AssetException {
        Path source = temporaryPath(staged.id());
        Path target = resolveStorageRef(staged.storageRef());
        try {
            if (!Files.isRegularFile(source, LinkOption.NOFOLLOW_LINKS) || Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_ASSET_STATE_CONFLICT");
            }
            try {
                Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException error) {
                throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_ATOMIC_MOVE_UNSUPPORTED", error);
            }
            setOwnerOnly(target, false);
        }
        catch (AssetException error) {
            throw error;
        }
        catch (IOException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_SEAL_FAILED", error);
        }
    }

    @Override
    public void discard(StagedAsset staged) {
        deletePath(temporaryPath(staged.id()));
    }

    @Override
    public void delete(String storageRef) {
        try {
            deletePath(resolveStorageRef(storageRef));
        }
        catch (AssetException ignored) {
            // storageRef came from the database; an invalid value is never broadened into another path.
        }
    }

    @Override
    public Asset open(String storageRef) throws AssetException {
        Path path = resolveStorageRef(storageRef);
        try {
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
                throw new AssetException(Kind.NOT_FOUND, "TEST_DATA_ASSET_NOT_FOUND");
            }
            return new Asset(Files.newInputStream(path), Files.size(path));
        }
        catch (AssetException error) {
            throw error;
        }
        catch (IOException error) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_READ_FAILED", error);
        }
    }

    @Override
    public RecoveryResult recover(Set<String> readyStorageRefs) {
        Instant cutoff = clock.instant().minus(properties.staleAge());
        Set<Path> readyPaths = new HashSet<>();
        for (String storageRef : readyStorageRefs) {
            try {
                readyPaths.add(resolveStorageRef(storageRef));
            }
            catch (AssetException ignored) {
                // Never resolve or delete outside the configured root.
            }
        }
        int temporaryDeleted = deleteStaleChildren(temporaryDirectory, cutoff, Set.of(), ".upload");
        int orphanDeleted = deleteStaleChildren(assetDirectory, cutoff, readyPaths, ".zip");
        return new RecoveryResult(temporaryDeleted, orphanDeleted);
    }

    private Validation validateArchive(Path path) throws AssetException {
        List<ManifestFile> files = new ArrayList<>();
        Map<String, Set<String>> pairs = new HashMap<>();
        Set<String> names = new HashSet<>();
        long totalBytes = 0;
        try (ZipFile archive = ZipFile.builder().setPath(path).setCharset(StandardCharsets.UTF_8).get()) {
            Enumeration<ZipArchiveEntry> entries = archive.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (files.size() >= properties.maxFiles()) {
                    throw invalid("TEST_DATA_TOO_MANY_FILES");
                }
                String name = entry.getName();
                Matcher matcher = FILE_NAME.matcher(name == null ? "" : name);
                if (!matcher.matches() || !names.add(name) || !isRegularFile(entry) || !archive.canReadEntryData(entry)) {
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
                files.add(new ManifestFile(name, digest.size(), digest.sha256()));
                pairs.computeIfAbsent(matcher.group(1), ignored -> new HashSet<>()).add(matcher.group(2));
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
        if (files.isEmpty() || pairs.values().stream().anyMatch(extensions -> !extensions.equals(Set.of("in", "out")))) {
            throw invalid("TEST_DATA_CASE_PAIR_REQUIRED");
        }
        files.sort(Comparator.comparing(ManifestFile::name));
        Manifest manifest = new Manifest(pairs.size(), totalBytes, List.copyOf(files));
        return new Validation(pairs.size(), totalBytes, manifest);
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

    private static long copyLimited(InputStream input, java.io.OutputStream output, MessageDigest digest, long limit)
            throws IOException, AssetException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > limit) throw tooLarge("TEST_DATA_ARCHIVE_TOO_LARGE");
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        return total;
    }

    private int deleteStaleChildren(Path directory, Instant cutoff, Set<Path> protectedPaths, String suffix) {
        int deleted = 0;
        try (var children = Files.list(directory)) {
            for (Path child : children.toList()) {
                if (!child.getFileName().toString().endsWith(suffix)
                        || protectedPaths.contains(child)
                        || !Files.isRegularFile(child, LinkOption.NOFOLLOW_LINKS)
                        || Files.isSymbolicLink(child)) continue;
                FileTime modified = Files.getLastModifiedTime(child, LinkOption.NOFOLLOW_LINKS);
                if (modified.toInstant().isBefore(cutoff) && Files.deleteIfExists(child)) deleted++;
            }
        }
        catch (IOException ignored) {
            // Recovery is best effort and never broadens deletion outside the two private child directories.
        }
        return deleted;
    }

    private Path temporaryPath(String id) {
        requireUuid(id);
        return temporaryDirectory.resolve(id + ".upload");
    }

    private Path resolveStorageRef(String storageRef) throws AssetException {
        Matcher matcher = STORAGE_REF.matcher(storageRef == null ? "" : storageRef);
        if (!matcher.matches()) throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_REF_INVALID");
        requireUuid(matcher.group(1));
        Path resolved = root.resolve(storageRef).normalize();
        if (!resolved.startsWith(assetDirectory)) {
            throw new AssetException(Kind.STORAGE_UNAVAILABLE, "TEST_DATA_STORAGE_REF_INVALID");
        }
        return resolved;
    }

    private String relative(Path path) {
        return root.relativize(path).toString().replace('\\', '/');
    }

    private static void requireUuid(String value) {
        UUID.fromString(value);
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static void createPrivateDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IOException("Not a private directory: " + path);
        }
        setOwnerOnly(path, true);
    }

    private static void setOwnerOnly(Path path, boolean directory) throws IOException {
        try {
            Files.setPosixFilePermissions(path, directory ? DIRECTORY_PERMISSIONS : FILE_PERMISSIONS);
        }
        catch (UnsupportedOperationException ignored) {
            // Non-POSIX platforms still rely on the deployment directory ACL.
        }
    }

    private static void deletePath(Path path) {
        try {
            Files.deleteIfExists(path);
        }
        catch (IOException ignored) {
            // A later bounded recovery pass retries exact known files.
        }
    }

    private static AssetException invalid(String code) {
        return new AssetException(Kind.INVALID_ARCHIVE, code);
    }

    private static AssetException tooLarge(String code) {
        return new AssetException(Kind.PAYLOAD_TOO_LARGE, code);
    }

    private record Validation(int caseCount, long totalBytes, Manifest manifest) {
    }

    private record EntryDigest(long size, String sha256) {
    }
}
