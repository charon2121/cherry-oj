package com.cherryoj.judgingservice.storage;

import com.cherryoj.judgingservice.api.JudgingDtos.Manifest;
import com.cherryoj.judgingservice.api.JudgingDtos.ManifestFile;
import com.cherryoj.judgingservice.config.JudgingProperties;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public final class FileTestDataDeploymentStore implements TestDataDeploymentStore {
    private static final Pattern FILE = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]{0,127})\\.(in|out)$");
    private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);
    private static final Set<PosixFilePermission> FILE_PERMISSIONS = Set.of(PosixFilePermission.OWNER_READ);

    private final JudgingProperties properties;
    private final OutputStreamFactory outputs;
    private Path root;
    private Path stagingRoot;

    @Autowired
    public FileTestDataDeploymentStore(JudgingProperties properties) {
        this(properties, Files::newOutputStream);
    }

    FileTestDataDeploymentStore(JudgingProperties properties, OutputStreamFactory outputs) {
        this.properties = properties;
        this.outputs = outputs;
    }

    @PostConstruct
    void initialize() {
        try {
            root = properties.testdataRoot().toAbsolutePath().normalize();
            if (Files.isSymbolicLink(root)) throw new IllegalStateException("judge testdata root is a symlink");
            stagingRoot = root.resolve(".staging");
            createPrivateDirectory(root);
            createPrivateDirectory(stagingRoot);
        }
        catch (IOException error) {
            throw new IllegalStateException("could not initialize judge testdata root", error);
        }
    }

    @Override
    public Installed deploy(String versionId, String expectedSha, Manifest manifest, InputStream archive)
            throws DeploymentException {
        requireUuid(versionId);
        Path work = stagingRoot.resolve(versionId + "-" + UUID.randomUUID()).normalize();
        Path zip = work.resolve("asset.zip");
        Path extracted = work.resolve("data");
        Path target = root.resolve(versionId).normalize();
        ensureChild(work, stagingRoot);
        ensureChild(target, root);
        try {
            createPrivateDirectory(work);
            createPrivateDirectory(extracted);
            MessageDigest digest = sha256();
            try (var output = outputs.open(zip)) {
                copyLimited(archive, output, digest, properties.maxArchiveBytes());
            }
            setOwnerOnly(zip, false);
            String actualSha = HexFormat.of().formatHex(digest.digest());
            if (!actualSha.equals(expectedSha)) throw invalid("DEPLOYMENT_ARCHIVE_HASH_MISMATCH");
            validateAndExtract(zip, extracted, manifest);
            Files.delete(zip);
            if (Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
                throw new DeploymentException(Kind.CONFLICT, "DEPLOYMENT_DIRECTORY_EXISTS");
            }
            try {
                Files.move(extracted, target, StandardCopyOption.ATOMIC_MOVE);
            }
            catch (AtomicMoveNotSupportedException error) {
                throw new DeploymentException(Kind.STORAGE, "DEPLOYMENT_ATOMIC_MOVE_UNSUPPORTED", error);
            }
            setOwnerOnly(target, true);
            deleteTree(work);
            return new Installed(versionId, actualSha);
        }
        catch (DeploymentException error) {
            deleteTree(work);
            throw error;
        }
        catch (IOException | RuntimeException error) {
            deleteTree(work);
            throw new DeploymentException(Kind.STORAGE, "DEPLOYMENT_STORAGE_FAILED", error);
        }
    }

    @Override
    public void rollback(Installed installed) {
        try {
            requireUuid(installed.testDataVersionId());
            Path target = root.resolve(installed.testDataVersionId()).normalize();
            ensureChild(target, root);
            deleteTree(target);
        }
        catch (RuntimeException ignored) {
            // A malformed identifier never broadens cleanup beyond the configured root.
        }
    }

    @Override
    public RecoveryResult recover(Set<String> readyIds) {
        Instant cutoff = Instant.now().minus(properties.staleAge());
        int staging = deleteStaleChildren(stagingRoot, cutoff, Set.of(), false);
        int orphans = deleteStaleChildren(root, cutoff, readyIds, true);
        return new RecoveryResult(staging, orphans);
    }

    private void validateAndExtract(Path zip, Path extracted, Manifest manifest) throws DeploymentException {
        Map<String, ManifestFile> expected = new HashMap<>();
        for (ManifestFile file : manifest.files()) {
            if (expected.put(file.name(), file) != null || !FILE.matcher(file.name()).matches()) {
                throw invalid("DEPLOYMENT_INVALID_MANIFEST");
            }
        }
        Set<String> seen = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        long total = 0;
        int count = 0;
        try (ZipFile archive = ZipFile.builder().setPath(zip).setCharset(StandardCharsets.UTF_8).get()) {
            Enumeration<ZipArchiveEntry> entries = archive.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (++count > properties.maxFiles()) throw tooLarge("DEPLOYMENT_TOO_MANY_FILES");
                String name = entry.getName();
                Matcher matcher = FILE.matcher(name == null ? "" : name);
                if (!matcher.matches() || !seen.add(name) || !regular(entry) || !archive.canReadEntryData(entry)) {
                    throw invalid("DEPLOYMENT_INVALID_ZIP_ENTRY");
                }
                ManifestFile expectedFile = expected.get(name);
                if (expectedFile == null) throw invalid("DEPLOYMENT_MANIFEST_MISMATCH");
                if (entry.getSize() > properties.maxEntryBytes()) throw tooLarge("DEPLOYMENT_ENTRY_TOO_LARGE");
                Path output = extracted.resolve(name).normalize();
                ensureChild(output, extracted);
                EntryDigest actual;
                try (InputStream input = archive.getInputStream(entry); var file = outputs.open(output)) {
                    actual = copyEntry(input, file);
                }
                setOwnerOnly(output, false);
                if (actual.size() != expectedFile.sizeBytes() || !actual.sha256().equals(expectedFile.sha256())) {
                    throw invalid("DEPLOYMENT_MANIFEST_MISMATCH");
                }
                total = Math.addExact(total, actual.size());
                if (total > properties.maxExpandedBytes()) throw tooLarge("DEPLOYMENT_EXPANDED_SIZE_EXCEEDED");
                long compressed = entry.getCompressedSize();
                if (actual.size() > 0 && (compressed <= 0
                        || (double) actual.size() / compressed > properties.maxCompressionRatio())) {
                    throw tooLarge("DEPLOYMENT_COMPRESSION_RATIO_EXCEEDED");
                }
                pairs.add(matcher.group(1));
            }
        }
        catch (DeploymentException error) { throw error; }
        catch (ArithmeticException error) { throw tooLarge("DEPLOYMENT_EXPANDED_SIZE_EXCEEDED"); }
        catch (IOException | RuntimeException error) {
            throw new DeploymentException(Kind.INVALID, "DEPLOYMENT_INVALID_ZIP", error);
        }
        if (!seen.equals(expected.keySet()) || total != manifest.totalBytes()
                || pairs.size() != manifest.caseCount() || seen.size() != pairs.size() * 2) {
            throw invalid("DEPLOYMENT_MANIFEST_MISMATCH");
        }
        for (String pair : pairs) {
            if (!seen.contains(pair + ".in") || !seen.contains(pair + ".out")) {
                throw invalid("DEPLOYMENT_CASE_PAIR_REQUIRED");
            }
        }
    }

    private EntryDigest copyEntry(InputStream input, java.io.OutputStream output)
            throws IOException, DeploymentException {
        MessageDigest digest = sha256();
        var decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
        ByteBuffer encoded = ByteBuffer.allocate(16384);
        CharBuffer decoded = CharBuffer.allocate(8192);
        byte[] buffer = new byte[8192];
        long size = 0;
        try {
            int read;
            while ((read = input.read(buffer)) != -1) {
                size = Math.addExact(size, read);
                if (size > properties.maxEntryBytes()) throw tooLarge("DEPLOYMENT_ENTRY_TOO_LARGE");
                digest.update(buffer, 0, read);
                output.write(buffer, 0, read);
                if (encoded.remaining() < read) decode(decoder, encoded, decoded, false);
                encoded.put(buffer, 0, read);
                decode(decoder, encoded, decoded, false);
            }
            decode(decoder, encoded, decoded, true);
            decoder.flush(decoded.clear());
        }
        catch (CharacterCodingException error) { throw invalid("DEPLOYMENT_FILE_NOT_UTF8"); }
        catch (ArithmeticException error) { throw tooLarge("DEPLOYMENT_ENTRY_TOO_LARGE"); }
        return new EntryDigest(size, HexFormat.of().formatHex(digest.digest()));
    }

    private static void decode(java.nio.charset.CharsetDecoder decoder, ByteBuffer encoded,
                               CharBuffer decoded, boolean end) throws CharacterCodingException {
        encoded.flip();
        while (true) {
            var result = decoder.decode(encoded, decoded.clear(), end);
            if (result.isError()) result.throwException();
            if (!result.isOverflow()) break;
        }
        encoded.compact();
    }

    private static long copyLimited(InputStream input, java.io.OutputStream output,
                                    MessageDigest digest, long limit) throws IOException, DeploymentException {
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total = Math.addExact(total, read);
            if (total > limit) throw tooLarge("DEPLOYMENT_ARCHIVE_TOO_LARGE");
            digest.update(buffer, 0, read);
            output.write(buffer, 0, read);
        }
        return total;
    }

    private static boolean regular(ZipArchiveEntry entry) {
        if (entry.isDirectory() || entry.isUnixSymlink()) return false;
        int mode = entry.getUnixMode();
        return mode == 0 || (mode & UnixStat.FILE_TYPE_FLAG) == UnixStat.FILE_FLAG;
    }

    private static void createPrivateDirectory(Path path) throws IOException {
        Files.createDirectories(path);
        setOwnerOnly(path, true);
    }

    private static void setOwnerOnly(Path path, boolean directory) {
        try { Files.setPosixFilePermissions(path, directory ? DIRECTORY_PERMISSIONS : FILE_PERMISSIONS); }
        catch (UnsupportedOperationException | IOException ignored) { }
    }

    private static void deleteTree(Path path) {
        if (path == null || !Files.exists(path, LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(path)) {
            for (Path child : paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(child);
        }
        catch (IOException ignored) { }
    }

    private int deleteStaleChildren(Path parent, Instant cutoff, Set<String> protectedIds, boolean uuidOnly) {
        int deleted = 0;
        try (var children = Files.list(parent)) {
            for (Path child : children.limit(properties.maxFiles()).toList()) {
                String name = child.getFileName().toString();
                if (uuidOnly && (!isUuid(name) || protectedIds.contains(name))) continue;
                if (child.equals(stagingRoot) || Files.getLastModifiedTime(child, LinkOption.NOFOLLOW_LINKS)
                        .toInstant().isAfter(cutoff)) continue;
                deleteTree(child);
                if (!Files.exists(child, LinkOption.NOFOLLOW_LINKS)) deleted++;
            }
        }
        catch (IOException ignored) { }
        return deleted;
    }

    private static void ensureChild(Path child, Path parent) {
        if (!child.normalize().startsWith(parent.normalize()) || child.normalize().equals(parent.normalize())) {
            throw new IllegalArgumentException("path escapes controlled root");
        }
    }

    private static void requireUuid(String value) {
        if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("invalid UUID");
    }

    private static boolean isUuid(String value) {
        try { requireUuid(value); return true; }
        catch (RuntimeException ignored) { return false; }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static DeploymentException invalid(String code) {
        return new DeploymentException(Kind.INVALID, code);
    }
    private static DeploymentException tooLarge(String code) {
        return new DeploymentException(Kind.TOO_LARGE, code);
    }
    private record EntryDigest(long size, String sha256) {}

    @FunctionalInterface
    interface OutputStreamFactory {
        OutputStream open(Path path) throws IOException;
    }
}
