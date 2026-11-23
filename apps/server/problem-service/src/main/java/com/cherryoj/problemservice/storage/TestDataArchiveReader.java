package com.cherryoj.problemservice.storage;

import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.storage.TestDataStore.AssetException;
import com.cherryoj.problemservice.storage.TestDataStore.Kind;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.springframework.util.unit.DataSize;

/** 读取上传的 ZIP，产出已校验的测试点。目录发布由存储负责。 */
final class TestDataArchiveReader {
    private static final Pattern FILE_NAME = Pattern.compile("^([A-Za-z0-9][A-Za-z0-9._-]{0,123})\\.(in|out)$");
    private static final Pattern WRAPPER_NAME = Pattern.compile("^[\\p{L}\\p{N}][\\p{L}\\p{N} ._-]{0,127}$");

    private final TestDataStorageProperties properties;

    TestDataArchiveReader(TestDataStorageProperties properties) {
        this.properties = properties;
    }

    ValidatedArchive read(Path path) throws AssetException {
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
        try (ZipFile archive = ZipFile.builder()
                .setPath(path)
                .setCharset(StandardCharsets.UTF_8)
                .get()) {
            Enumeration<ZipArchiveEntry> entries = archive.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (++entryCount > properties.maxFiles()) {
                    throw invalid("TEST_DATA_TOO_MANY_FILES", null, String.valueOf(properties.maxFiles()));
                }
                String name = entry.getName();
                if (!isSafeEntryPath(name)) {
                    throw invalid("TEST_DATA_UNSAFE_ENTRY_PATH", null, null);
                }
                if (!physicalNames.add(name)) {
                    throw invalid("TEST_DATA_DUPLICATE_ENTRY", name, null);
                }
                MetadataPath metadata = metadataPath(name);
                if (metadata != null) {
                    if ((!entry.isDirectory() && (!isRegularFile(entry) || !archive.canReadEntryData(entry)))
                            || entry.isUnixSymlink()) {
                        throw invalid("TEST_DATA_ENTRY_NOT_REGULAR_FILE", name, null);
                    }
                    if (metadata.wrapper() != null) metadataPrefixes.add(metadata.wrapper());
                    continue;
                }
                if (entry.isDirectory()) {
                    if (entry.isUnixSymlink()) throw invalid("TEST_DATA_ENTRY_NOT_REGULAR_FILE", name, null);
                    directoryMarkers.add(trimTrailingSlash(name));
                    continue;
                }
                TestcasePath testcasePath = testcasePath(name);
                if (testcasePath == null) {
                    throw invalid("TEST_DATA_UNEXPECTED_ENTRY", name, null);
                }
                if (!isRegularFile(entry) || !archive.canReadEntryData(entry)) {
                    throw invalid("TEST_DATA_ENTRY_NOT_REGULAR_FILE", name, null);
                }
                if (!logicalRootSelected) {
                    wrapper = testcasePath.wrapper();
                    logicalRootSelected = true;
                } else if (!Objects.equals(wrapper, testcasePath.wrapper())) {
                    throw invalid("TEST_DATA_MIXED_ROOTS", name, null);
                }
                if (!logicalNames.add(testcasePath.logicalName())) {
                    throw invalid("TEST_DATA_DUPLICATE_ENTRY", testcasePath.logicalName(), null);
                }
                if (entry.getSize() > properties.maxEntrySize().toBytes()) {
                    throw tooLarge("TEST_DATA_ENTRY_TOO_LARGE", name, size(properties.maxEntrySize()));
                }
                EntryDigest digest = digestEntry(archive, entry);
                totalBytes = Math.addExact(totalBytes, digest.size());
                if (totalBytes > properties.maxExpandedSize().toBytes()) {
                    throw tooLarge("TEST_DATA_EXPANDED_SIZE_EXCEEDED", null, size(properties.maxExpandedSize()));
                }
                long compressed = entry.getCompressedSize();
                if (digest.size() > 0
                        && (compressed <= 0
                                || (double) digest.size() / compressed > properties.maxCompressionRatio())) {
                    throw tooLarge(
                            "TEST_DATA_COMPRESSION_RATIO_EXCEEDED",
                            name,
                            new java.math.BigDecimal(String.valueOf(properties.maxCompressionRatio()))
                                    .stripTrailingZeros()
                                    .toPlainString());
                }
                files.add(new ValidatedFile(testcasePath.logicalName(), name, digest.size(), digest.sha256()));
                pairs.computeIfAbsent(testcasePath.testcaseName(), ignored -> new HashSet<>())
                        .add(testcasePath.extension());
            }
        } catch (AssetException error) {
            throw error;
        } catch (ArithmeticException error) {
            throw tooLarge("TEST_DATA_EXPANDED_SIZE_EXCEEDED", null, size(properties.maxExpandedSize()));
        } catch (IOException | RuntimeException error) {
            throw new AssetException(Kind.INVALID_ARCHIVE, "TEST_DATA_INVALID_ZIP", error);
        }
        String selectedWrapper = wrapper;
        if (files.isEmpty()) {
            throw invalid("TEST_DATA_NO_TESTCASES", null, null);
        }
        var strayDirectory = directoryMarkers.stream()
                .filter(directory -> !Objects.equals(directory, selectedWrapper))
                .sorted()
                .findFirst();
        if (strayDirectory.isPresent()) {
            throw invalid("TEST_DATA_MIXED_ROOTS", strayDirectory.get() + "/", null);
        }
        var strayMetadata = metadataPrefixes.stream()
                .filter(prefix -> !prefix.isEmpty() && !Objects.equals(prefix, selectedWrapper))
                .sorted()
                .findFirst();
        if (strayMetadata.isPresent()) {
            throw invalid("TEST_DATA_MIXED_ROOTS", strayMetadata.get() + "/", null);
        }
        // 报出排在最前的那个缺失文件，例如只有 3.in 时是 3.out。
        var missing = pairs.entrySet().stream()
                .filter(pair -> !pair.getValue().equals(Set.of("in", "out")))
                .map(pair -> pair.getKey() + (pair.getValue().contains("in") ? ".out" : ".in"))
                .sorted()
                .findFirst();
        if (missing.isPresent()) {
            throw invalid("TEST_DATA_CASE_PAIR_REQUIRED", missing.get(), null);
        }
        if (pairs.size() > TestDataMetadata.MAX_CASES) {
            throw invalid("TEST_DATA_TOO_MANY_CASES", null, String.valueOf(TestDataMetadata.MAX_CASES));
        }
        return new ValidatedArchive(path, List.copyOf(files));
    }

    private EntryDigest digestEntry(ZipFile archive, ZipArchiveEntry entry) throws IOException, AssetException {
        MessageDigest digest = sha256();
        var decoder = StandardCharsets.UTF_8
                .newDecoder()
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
                    throw tooLarge("TEST_DATA_ENTRY_TOO_LARGE", entry.getName(), size(properties.maxEntrySize()));
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
        } catch (CharacterCodingException error) {
            throw invalid("TEST_DATA_FILE_NOT_UTF8", entry.getName(), null);
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
        if (name == null
                || name.isBlank()
                || name.length() > 512
                || name.startsWith("/")
                || name.indexOf('\\') >= 0
                || name.indexOf('\0') >= 0) {
            return false;
        }
        String candidate = trimTrailingSlash(name);
        if (candidate.isEmpty()) return false;
        for (String component : candidate.split("/", -1)) {
            if (component.isEmpty()
                    || component.equals(".")
                    || component.equals("..")
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
        return matcher.matches() ? new TestcasePath(wrapper, logicalName, matcher.group(1), matcher.group(2)) : null;
    }

    private static String trimTrailingSlash(String name) {
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    void stage(InputStream input, Path staged) throws IOException, AssetException {
        long limit = properties.maxArchiveSize().toBytes();
        try (OutputStream output = Files.newOutputStream(staged)) {
            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                totalBytes += read;
                if (totalBytes > limit)
                    throw tooLarge("TEST_DATA_ARCHIVE_TOO_LARGE", null, size(properties.maxArchiveSize()));
                output.write(buffer, 0, read);
            }
        }
    }

    private static void extract(Path archivePath, List<ValidatedFile> files, Path directory)
            throws AssetException, IOException {
        try (ZipFile archive = ZipFile.builder()
                .setPath(archivePath)
                .setCharset(StandardCharsets.UTF_8)
                .get()) {
            for (ValidatedFile file : files) {
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
                if (size != file.size()
                        || !HexFormat.of().formatHex(digest.digest()).equals(file.sha256())) {
                    throw new AssetException(Kind.INVALID_ARCHIVE, "TEST_DATA_ARCHIVE_CHANGED");
                }
            }
        }
    }

    private static TestDataMetadata.Document documentFor(List<ValidatedFile> files) {
        Map<String, Map<String, ValidatedFile>> byTestcase = new HashMap<>();
        for (ValidatedFile file : files) {
            int dot = file.name().lastIndexOf('.');
            byTestcase
                    .computeIfAbsent(file.name().substring(0, dot), ignored -> new HashMap<>())
                    .put(file.name().substring(dot + 1), file);
        }
        List<TestDataMetadata.TestcaseEntry> testcases = byTestcase.keySet().stream()
                .sorted(TestDataMetadata.defaultOrder())
                .map(name -> new TestDataMetadata.TestcaseEntry(
                        name,
                        entry(byTestcase.get(name).get("in")),
                        entry(byTestcase.get(name).get("out"))))
                .toList();
        return TestDataMetadata.Document.of(testcases);
    }

    private static TestDataMetadata.FileEntry entry(ValidatedFile file) {
        return new TestDataMetadata.FileEntry(file.size(), file.sha256());
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    private static AssetException invalid(String code, String entry, String limit) {
        return new AssetException(Kind.INVALID_ARCHIVE, code, entry, limit);
    }

    private static AssetException tooLarge(String code, String entry, String limit) {
        return new AssetException(Kind.PAYLOAD_TOO_LARGE, code, entry, limit);
    }

    private static String size(DataSize size) {
        return TestDataStorageProperties.readable(size);
    }

    record ValidatedArchive(Path path, List<ValidatedFile> files) {
        TestDataMetadata.Document document() {
            return documentFor(files);
        }

        void extractTo(Path directory) throws AssetException, IOException {
            extract(path, files, directory);
        }
    }

    /** name 是协议里的文件名（不含包装目录），entryName 是它在 ZIP 里的完整条目名。 */
    private record ValidatedFile(String name, String entryName, long size, String sha256) {}

    private record EntryDigest(long size, String sha256) {}

    private record MetadataPath(String wrapper) {}

    private record TestcasePath(String wrapper, String logicalName, String testcaseName, String extension) {}
}
