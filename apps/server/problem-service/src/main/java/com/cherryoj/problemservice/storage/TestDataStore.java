package com.cherryoj.problemservice.storage;

import com.cherryoj.problemservice.api.TestDataDtos.Manifest;
import java.io.InputStream;

/**
 * 题目的测试数据目录，布局见 docs/testdata-protocol.md。
 *
 * 上传分两步：{@link #prepare} 校验 ZIP 并把内容写成以内容寻址的目录，此时没有任何题目地址指向它；
 * {@link #activate} 再让题目地址原子地切到它。这样慢的部分（读 ZIP、写文件）可以放在数据库事务之外，
 * 事务里只剩一次重命名。
 */
public interface TestDataStore {

    Prepared prepare(String problemId, InputStream zip) throws AssetException;

    /** 让题目的地址指向 prepared 的目录并返回地址（以 / 开头的绝对路径）。读取方要么看到旧的完整数据，要么看到新的。 */
    String activate(String problemId, Prepared prepared) throws AssetException;

    /** prepare 之后没能 activate 时丢弃；已经被地址指向的目录不会被删。 */
    void discard(Prepared prepared);

    /** 只保留题目当前与上一代目录。上一代留给还在读取旧数据的 judge 请求，它们读不到时会重试一次。 */
    void prune(String problemId);

    /** 读地址下的 testdata.json。 */
    Info describe(String location) throws AssetException;

    /** 题目被删除时移除它的地址与全部目录。 */
    void remove(String problemId);

    /** 清理中断的上传留下的临时文件。 */
    RecoveryResult recover();

    record Info(String digest, int testcaseCount, long totalBytes, Manifest manifest) {
    }

    record Prepared(String problemId, String generation, Info info) {
    }

    record RecoveryResult(int temporaryFilesDeleted, int temporaryDirectoriesDeleted) {
    }

    final class AssetException extends Exception {
        private final Kind kind;

        public AssetException(Kind kind, String message) {
            super(message);
            this.kind = kind;
        }

        public AssetException(Kind kind, String message, Throwable cause) {
            super(message, cause);
            this.kind = kind;
        }

        public Kind kind() {
            return kind;
        }
    }

    enum Kind { PAYLOAD_TOO_LARGE, INVALID_ARCHIVE, STORAGE_UNAVAILABLE, NOT_FOUND }
}
