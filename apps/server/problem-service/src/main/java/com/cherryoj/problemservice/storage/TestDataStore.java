package com.cherryoj.problemservice.storage;

import java.io.InputStream;
import java.util.List;
import java.util.function.Predicate;

/**
 * 题目的测试数据目录，布局见 docs/protocols/testdata.md。
 *
 * {@link #prepare} 校验 ZIP 并写入暂存目录，{@link #seal} 交付完整的独立目录。
 * 当前地址由数据库选定。交付和回收同一道题的数据时，调用方持有相同的题目行锁。
 */
public interface TestDataStore {

    Prepared prepare(String problemId, InputStream zip) throws AssetException;

    /** 写好目录后返回其绝对地址，供调用方写入数据库。 */
    String seal(Prepared prepared) throws AssetException;

    /** 丢弃暂存目录。已交付目录由回收流程在确认数据库引用后处理。 */
    void discard(Prepared prepared);

    List<StoredDirectory> storedDirectories();

    /** 标记未引用目录，或回收已过保留期的目录及其旧地址。 */
    void reclaim(StoredDirectory directory, Predicate<String> isReferenced);

    /** 读地址下的 testdata.json。 */
    Info describe(String location) throws AssetException;

    /** 清理中断的上传留下的临时文件。 */
    RecoveryResult recover();

    record Info(
            String digest,
            int testcaseCount,
            long totalBytes,
            long maxTestcaseIoBytes,
            List<TestDataMetadata.TestcaseEntry> testcases) {}

    record Prepared(String problemId, String generation, Info info) {}

    record StoredDirectory(String problemId, String generation, boolean legacy) {}

    record RecoveryResult(int temporaryFilesDeleted, int temporaryDirectoriesDeleted) {}

    /**
     * message 是原因代码（如 TEST_DATA_COMPRESSION_RATIO_EXCEEDED）；entry 是出问题的 ZIP 条目名，limit 是触发的上限
     * （已格式化，如"64 MiB"），都可能为 null。面向管理员的文字由 TestDataService 按代码拼出。
     */
    final class AssetException extends Exception {
        private final Kind kind;
        private final String entry;
        private final String limit;

        public AssetException(Kind kind, String message) {
            this(kind, message, null, null);
        }

        public AssetException(Kind kind, String message, String entry, String limit) {
            super(message);
            this.kind = kind;
            this.entry = entry;
            this.limit = limit;
        }

        public AssetException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
            this.entry = null;
            this.limit = null;
        }

        public Kind kind() {
            return kind;
        }

        public String entry() {
            return entry;
        }

        public String limit() {
            return limit;
        }
    }

    enum Kind {
        PAYLOAD_TOO_LARGE,
        INVALID_ARCHIVE,
        STORAGE_UNAVAILABLE,
        NOT_FOUND
    }
}
