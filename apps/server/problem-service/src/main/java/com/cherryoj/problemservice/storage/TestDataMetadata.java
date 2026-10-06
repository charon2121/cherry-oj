package com.cherryoj.problemservice.storage;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.regex.Pattern;

/** testdata.json 的结构与规则，与 docs/testdata-protocol.md 一一对应。 */
public final class TestDataMetadata {

    public static final String FILE_NAME = "testdata.json";
    public static final int SCHEMA_VERSION = 1;
    public static final int MAX_CASES = 1000;

    /** 测试点名字会拼进路径和 URL；加上 .in / .out 后不超过 128 个字符。 */
    public static final Pattern NAME = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,123}$");

    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

    private TestDataMetadata() {
    }

    public record FileEntry(long sizeBytes, String sha256) {
    }

    public record CaseEntry(String name, FileEntry input, FileEntry output) {
    }

    public record Document(int schemaVersion, int caseCount, long totalBytes, String digest, List<CaseEntry> cases) {

        /** 按给定顺序（即判题顺序）生成文档：总字节数与 digest 都由测试点算出。 */
        public static Document of(List<CaseEntry> cases) {
            long total = cases.stream().mapToLong(c -> c.input().sizeBytes() + c.output().sizeBytes()).sum();
            return new Document(SCHEMA_VERSION, cases.size(), total, digestOf(cases), List.copyOf(cases));
        }

        /** 文件不合规时返回原因，合规返回 null。读取方用它拒绝被改坏或不认识的元数据。 */
        public String problem() {
            if (schemaVersion != SCHEMA_VERSION) {
                return "schemaVersion is " + schemaVersion + ", only " + SCHEMA_VERSION + " is supported";
            }
            if (cases == null || caseCount < 1 || caseCount > MAX_CASES || cases.size() != caseCount) {
                return "caseCount must be between 1 and " + MAX_CASES + " and match cases";
            }
            long total = 0;
            for (CaseEntry entry : cases) {
                if (entry == null || entry.name() == null || !NAME.matcher(entry.name()).matches()
                        || !valid(entry.input()) || !valid(entry.output())) {
                    return "a case has an invalid name or file entry";
                }
                total += entry.input().sizeBytes() + entry.output().sizeBytes();
            }
            if (total != totalBytes) {
                return "totalBytes is " + totalBytes + " but the files add up to " + total;
            }
            if (!digestOf(cases).equals(digest)) {
                return "digest does not match the cases";
            }
            return null;
        }

        private static boolean valid(FileEntry file) {
            return file != null && file.sizeBytes() >= 0 && file.sha256() != null
                    && SHA256.matcher(file.sha256()).matches();
        }
    }

    /**
     * 对每个测试点按顺序生成两行 {@code "<sha256>  <name>.in\n"}、{@code "<sha256>  <name>.out\n"}，拼接后取 SHA-256。
     * 可以用 {@code sha256sum} 的输出格式复现；顺序是内容指纹的一部分。
     */
    public static String digestOf(List<CaseEntry> cases) {
        StringBuilder lines = new StringBuilder();
        for (CaseEntry entry : cases) {
            lines.append(entry.input().sha256()).append("  ").append(entry.name()).append(".in\n");
            lines.append(entry.output().sha256()).append("  ").append(entry.name()).append(".out\n");
        }
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(lines.toString().getBytes(StandardCharsets.UTF_8)));
        }
        catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException(error);
        }
    }

    /** 写入方的默认顺序：能解析成整数的名字按数值升序，其余按字符串序排在后面。 */
    public static Comparator<String> defaultOrder() {
        return (a, b) -> {
            boolean aNumber = a.chars().allMatch(c -> c >= '0' && c <= '9');
            boolean bNumber = b.chars().allMatch(c -> c >= '0' && c <= '9');
            if (aNumber && bNumber) {
                int byValue = new BigInteger(a).compareTo(new BigInteger(b));
                return byValue != 0 ? byValue : a.compareTo(b);
            }
            if (aNumber != bNumber) {
                return aNumber ? -1 : 1;
            }
            return a.compareTo(b);
        };
    }
}
