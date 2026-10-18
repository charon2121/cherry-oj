package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.storage.TestDataStore;
import com.cherryoj.problemservice.storage.TestDataStore.AssetException;
import com.cherryoj.problemservice.storage.TestDataStore.Info;
import com.cherryoj.problemservice.storage.TestDataStore.Prepared;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

/**
 * 题目的测试数据：上传 ZIP 后按协议（docs/protocols/testdata.md）写成目录，数据库只记目录的地址。
 * 数据的指纹、测试点数和文件清单都从地址下的 testdata.json 读，不在数据库里再存一份。
 */
@Service
public class TestDataService {

    private final AdminProblemMapper problems;
    private final TestDataStore store;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final TestDataStorageProperties storageProperties;

    public TestDataService(
            AdminProblemMapper problems,
            TestDataStore store,
            UuidV7 ids,
            Clock clock,
            ObjectMapper json,
            TestDataStorageProperties storageProperties,
            PlatformTransactionManager transactionManager) {
        this.problems = problems;
        this.store = store;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.storageProperties = storageProperties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public TestDataDtos.TestData get(String problemId) {
        ProblemRow problem = requireProblem(problemId, false);
        Info info = describe(problem);
        return new TestDataDtos.TestData(
                info.digest(), info.testcaseCount(), info.totalBytes(), problem.testDataUpdatedAt(), info.manifest());
    }

    /** 上传并替换题目的测试数据。慢的部分（读 ZIP、写文件）在事务之外，事务里只有一次原子切换。 */
    public TestDataDtos.TestData upload(String problemId, MultipartFile file, String actorUserId) {
        if (file == null || file.isEmpty()) {
            throw new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEST_DATA_EMPTY", "测试数据 ZIP 不能为空。");
        }
        if (file.getSize() > storageProperties.maxArchiveSize().toBytes()) {
            throw new ProblemApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE",
                    "ZIP 超过 " + TestDataStorageProperties.readable(storageProperties.maxArchiveSize()) + " 的上传上限。");
        }
        transactions.executeWithoutResult(status -> requireActive(requireProblem(problemId, false)));

        Prepared prepared;
        try (InputStream source = file.getInputStream()) {
            prepared = store.prepare(problemId, source);
        }
        catch (AssetException error) {
            throw assetProblem(error);
        }
        catch (Exception error) {
            throw new ProblemApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TEST_DATA_STORAGE_UNAVAILABLE", "测试数据暂时无法保存。");
        }

        TestDataDtos.TestData result;
        try {
            result = transactions.execute(status -> {
                requireActive(requireProblem(problemId, true));
                String location;
                try {
                    location = store.activate(problemId, prepared);
                }
                catch (AssetException error) {
                    throw new AssetRuntimeException(error);
                }
                LocalDateTime now = now();
                if (problems.updateTestData(problemId, location, now) != 1) {
                    throw state("题目状态已改变。");
                }
                audit(problemId, actorUserId, Map.of(
                        "digest", prepared.info().digest(),
                        "testcaseCount", prepared.info().testcaseCount(),
                        "totalBytes", prepared.info().totalBytes()));
                return new TestDataDtos.TestData(prepared.info().digest(), prepared.info().testcaseCount(),
                        prepared.info().totalBytes(), now, prepared.info().manifest());
            });
        }
        catch (RuntimeException error) {
            // 没能切换时目录没有被任何地址指向，可以丢掉；已经切换成功的目录 discard 不会动。
            store.discard(prepared);
            if (error instanceof AssetRuntimeException asset) throw assetProblem(asset.cause());
            if (error instanceof ProblemApiException problem) throw problem;
            throw new ProblemApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TEST_DATA_STORAGE_UNAVAILABLE", "测试数据暂时无法保存。");
        }
        store.prune(problemId);
        return result;
    }

    /** judging-service 判题与标定前来取题目当前的测试数据地址；每次都读 testdata.json，拿到的就是最新的。 */
    public TestDataDtos.ProblemTestData forJudging(String problemId) {
        ProblemRow problem = requireProblem(problemId, false);
        Info info = describe(problem);
        return new TestDataDtos.ProblemTestData(
                problem.testDataLocation(), info.digest(), info.testcaseCount(), info.totalBytes());
    }

    private Info describe(ProblemRow problem) {
        if (problem.testDataLocation() == null) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "TEST_DATA_NOT_FOUND", "题目还没有测试数据。");
        }
        try {
            return store.describe(problem.testDataLocation());
        }
        catch (AssetException error) {
            throw assetProblem(error);
        }
    }

    private ProblemRow requireProblem(String problemId, boolean forUpdate) {
        ProblemRow row = forUpdate ? problems.findProblemForUpdate(problemId) : problems.findProblem(problemId);
        if (row == null) throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        return row;
    }

    private static void requireActive(ProblemRow problem) {
        if (problem.status() != ProblemStatus.ACTIVE) throw state("归档题目不能上传测试数据。");
    }

    private void audit(String problemId, String actorUserId, Map<String, Object> detail) {
        try {
            problems.insertAudit(ids.next().toString(), problemId, actorUserId, "TEST_DATA_UPLOADED", null,
                    json.writeValueAsString(detail), now());
        }
        catch (RuntimeException error) {
            throw new IllegalStateException("Could not write test data audit", error);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ProblemApiException assetProblem(AssetException error) {
        return switch (error.kind()) {
            case PAYLOAD_TOO_LARGE -> new ProblemApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", tooLargeDetail(error));
            case INVALID_ARCHIVE -> new ProblemApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TEST_DATA_ARCHIVE", invalidArchiveDetail(error));
            case NOT_FOUND -> new ProblemApiException(
                    HttpStatus.NOT_FOUND, "TEST_DATA_NOT_FOUND", "题目的测试数据读取不到，请重新上传。");
            case STORAGE_UNAVAILABLE -> new ProblemApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TEST_DATA_STORAGE_UNAVAILABLE", "测试数据暂时不可用。");
        };
    }

    // 每条都说清是哪一个文件、哪一条规则或哪一个上限，以及怎么改；原因代码见 FileTestDataStore。
    private static String invalidArchiveDetail(AssetException error) {
        String entry = error.entry();
        String limit = error.limit();
        return switch (error.getMessage()) {
            case "TEST_DATA_INVALID_ZIP" -> "无法读取该 ZIP，请确认文件没有损坏后重试。";
            case "TEST_DATA_UNSAFE_ENTRY_PATH" ->
                    "ZIP 里有不安全的路径（绝对路径、上级目录 ..、反斜杠或超长名字），请重新打包。";
            case "TEST_DATA_DUPLICATE_ENTRY" -> "「" + entry + "」在 ZIP 里出现了不止一次，请只保留一份。";
            case "TEST_DATA_ENTRY_NOT_REGULAR_FILE" -> "「" + entry
                    + "」不是可读取的普通文件：可能是符号链接、加密条目，或打包工具没有把它标记为普通文件。"
                    + "请用系统自带的压缩功能或 zip 命令重新打包。";
            case "TEST_DATA_UNEXPECTED_ENTRY" -> "「" + entry
                    + "」不是测试数据文件。只接受「名字.in」「名字.out」，名字由英文字母、数字、点、下划线或连字符组成，"
                    + "放在根目录或一个外层文件夹里。";
            case "TEST_DATA_MIXED_ROOTS" -> "测试数据要么都在根目录，要么都在同一个外层文件夹里；「" + entry
                    + "」和其他文件不在同一处。";
            case "TEST_DATA_NO_TESTCASES" -> "ZIP 里没有找到 .in/.out 文件。";
            case "TEST_DATA_CASE_PAIR_REQUIRED" -> "缺少「" + entry + "」：每个测试点都必须同时有同名的 .in 和 .out 文件。";
            case "TEST_DATA_FILE_NOT_UTF8" -> "「" + entry + "」不是 UTF-8 文本，请转换编码后重新打包。";
            case "TEST_DATA_TOO_MANY_FILES" -> "ZIP 里的文件超过 " + limit + " 个的上限。";
            case "TEST_DATA_TOO_MANY_CASES" -> "测试点超过 " + limit + " 个的上限。";
            default -> "测试数据 ZIP 格式无效。";
        };
    }

    private static String tooLargeDetail(AssetException error) {
        String entry = error.entry();
        String limit = error.limit();
        return switch (error.getMessage()) {
            case "TEST_DATA_ARCHIVE_TOO_LARGE" -> "ZIP 超过 " + limit + " 的上传上限。";
            case "TEST_DATA_ENTRY_TOO_LARGE" -> "「" + entry + "」解压后超过单个文件 " + limit + " 的上限。";
            case "TEST_DATA_EXPANDED_SIZE_EXCEEDED" -> "解压后的总大小超过 " + limit + " 的上限。";
            case "TEST_DATA_COMPRESSION_RATIO_EXCEEDED" -> "「" + entry + "」的压缩比超过 " + limit
                    + " 倍的上限：内容重复度太高，会被当作压缩炸弹拦下。请让这组数据不那么重复后重试。";
            default -> "测试数据 ZIP 超过安全限额。";
        };
    }

    private static ProblemApiException state(String message) {
        return new ProblemApiException(HttpStatus.CONFLICT, "RESOURCE_STATE_CONFLICT", message);
    }

    /** 事务回调里不能抛受检异常，包一层带出去。 */
    private static final class AssetRuntimeException extends RuntimeException {
        private final AssetException cause;

        AssetRuntimeException(AssetException cause) {
            super(cause);
            this.cause = cause;
        }

        AssetException cause() {
            return cause;
        }
    }
}
