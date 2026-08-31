package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.VersionStatus;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.config.TestDataStorageProperties;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.VersionRow;
import com.cherryoj.problemservice.persistence.TestDataMapper;
import com.cherryoj.problemservice.persistence.TestDataRows.TestDataRow;
import com.cherryoj.problemservice.storage.TestDataAssetStore;
import com.cherryoj.problemservice.storage.TestDataAssetStore.Asset;
import com.cherryoj.problemservice.storage.TestDataAssetStore.AssetException;
import com.cherryoj.problemservice.storage.TestDataAssetStore.StagedAsset;
import java.io.InputStream;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.databind.ObjectMapper;

@Service
public class TestDataService {

    private final TestDataMapper mapper;
    private final AdminProblemMapper problems;
    private final AdminProblemService adminProblems;
    private final TestDataViewMapper views;
    private final TestDataAssetStore assets;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TransactionTemplate transactions;
    private final TestDataStorageProperties storageProperties;

    public TestDataService(
            TestDataMapper mapper,
            AdminProblemMapper problems,
            AdminProblemService adminProblems,
            TestDataViewMapper views,
            TestDataAssetStore assets,
            UuidV7 ids,
            Clock clock,
            ObjectMapper json,
            TestDataStorageProperties storageProperties,
            PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.problems = problems;
        this.adminProblems = adminProblems;
        this.views = views;
        this.assets = assets;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.storageProperties = storageProperties;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public List<TestDataDtos.TestDataVersion> list(String problemId) {
        requireProblem(problemId, false);
        return mapper.list(problemId).stream().map(views::map).toList();
    }

    public TestDataDtos.TestDataVersion upload(String problemId, MultipartFile file, String actorUserId) {
        if (file == null || file.isEmpty()) {
            throw new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEST_DATA_EMPTY", "测试数据 ZIP 不能为空。");
        }
        if (file.getSize() > storageProperties.maxArchiveSize().toBytes()) {
            throw new ProblemApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "测试数据 ZIP 超过安全限额。");
        }
        String id = ids.next().toString();
        String storageRef = "assets/" + id + ".zip";
        transactions.executeWithoutResult(status -> {
            ProblemRow problem = requireProblem(problemId, true);
            if (problem.status() != ProblemStatus.ACTIVE) throw state("归档题目不能上传测试数据。");
            mapper.insertUploading(id, problemId, storageRef, actorUserId, now());
        });

        StagedAsset staged;
        try (InputStream source = file.getInputStream()) {
            staged = assets.stage(id, source);
        }
        catch (AssetException error) {
            failUpload(id, error.getMessage(), problemId, actorUserId);
            throw assetProblem(error);
        }
        catch (Exception error) {
            failUpload(id, "TEST_DATA_UPLOAD_INTERRUPTED", problemId, actorUserId);
            throw new ProblemApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TEST_DATA_STORAGE_UNAVAILABLE", "测试数据暂时无法保存。");
        }

        var sealed = new boolean[] { false };
        try {
            TestDataRow result = transactions.execute(status -> {
                ProblemRow problem = requireProblem(problemId, true);
                if (problem.status() != ProblemStatus.ACTIVE) throw state("归档题目不能上传测试数据。");
                TestDataRow existing = mapper.findReadyByHash(problemId, staged.contentSha256());
                if (existing != null) {
                    if (mapper.deleteUploading(id) != 1) throw state("上传状态已经改变。");
                    assets.discard(staged);
                    audit(problemId, actorUserId, "TEST_DATA_REUSED", Map.of(
                            "testDataVersionId", existing.id(), "contentSha256", existing.contentSha256()));
                    return existing;
                }
                try {
                    assets.seal(staged);
                    sealed[0] = true;
                }
                catch (AssetException error) {
                    throw new AssetRuntimeException(error);
                }
                if (mapper.markReady(
                        id, staged.contentSha256(), staged.caseCount(), staged.totalBytes(),
                        writeJson(staged.manifest()), now()) != 1) {
                    throw state("上传状态已经改变。");
                }
                audit(problemId, actorUserId, "TEST_DATA_READY", Map.of(
                        "testDataVersionId", id,
                        "contentSha256", staged.contentSha256(),
                        "caseCount", staged.caseCount(),
                        "totalBytes", staged.totalBytes()));
                return mapper.find(problemId, id);
            });
            return views.map(result);
        }
        catch (RuntimeException error) {
            if (sealed[0]) assets.delete(staged.storageRef());
            else assets.discard(staged);
            Throwable cause = error instanceof AssetRuntimeException ? error.getCause() : error;
            String failureCode = cause instanceof AssetException asset ? asset.getMessage() : "TEST_DATA_FINALIZE_FAILED";
            failUpload(id, failureCode, problemId, actorUserId);
            if (cause instanceof AssetException asset) throw assetProblem(asset);
            if (error instanceof ProblemApiException problem) throw problem;
            throw new ProblemApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TEST_DATA_STORAGE_UNAVAILABLE", "测试数据暂时无法封存。");
        }
    }

    public ReadyAsset openReady(String problemId, String testDataVersionId) {
        TestDataRow row = mapper.find(problemId, testDataVersionId);
        if (row == null || row.status() != TestDataDtos.Status.READY) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "TEST_DATA_NOT_FOUND", "测试数据版本不存在。");
        }
        try {
            Asset asset = assets.open(row.storageRef());
            return new ReadyAsset(row.id(), row.contentSha256(), views.map(row).manifest(), asset);
        }
        catch (AssetException error) {
            throw assetProblem(error);
        }
    }

    public AdminProblemDtos.Version bind(
            String problemId,
            String versionId,
            TestDataDtos.BindTestDataRequest request,
            String actorUserId) {
        transactions.executeWithoutResult(status -> {
            ProblemRow problem = requireProblem(problemId, true);
            if (problem.status() != ProblemStatus.ACTIVE) throw state("归档题目不能绑定测试数据。");
            VersionRow version = problems.findVersionForUpdate(problemId, versionId);
            if (version == null) {
                throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_VERSION_NOT_FOUND", "题目版本不存在。");
            }
            if (version.status() != VersionStatus.DRAFT) throw state("只有 DRAFT 版本可以绑定测试数据。");
            if (version.rowVersion() != request.rowVersion()) throw conflict();
            TestDataRow testData = mapper.find(problemId, request.testDataVersionId());
            if (testData == null || testData.status() != TestDataDtos.Status.READY) {
                throw state("只能绑定本题 READY 测试数据。");
            }
            if (mapper.bindDraft(problemId, versionId, testData.id(), now(), request.rowVersion()) != 1) {
                throw conflict();
            }
            audit(problemId, actorUserId, "TEST_DATA_BOUND", Map.of(
                    "problemVersionId", versionId,
                    "testDataVersionId", testData.id(),
                    "contentSha256", testData.contentSha256()));
        });
        return adminProblems.getVersion(problemId, versionId);
    }

    private ProblemRow requireProblem(String problemId, boolean forUpdate) {
        ProblemRow row = forUpdate ? problems.findProblemForUpdate(problemId) : problems.findProblem(problemId);
        if (row == null) throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        return row;
    }

    private void failUpload(String id, String code, String problemId, String actorUserId) {
        String safeCode = code == null ? "TEST_DATA_UPLOAD_FAILED" : code.substring(0, Math.min(code.length(), 128));
        try {
            transactions.executeWithoutResult(status -> {
                if (mapper.markFailed(id, safeCode) == 1) {
                    audit(problemId, actorUserId, "TEST_DATA_FAILED", Map.of("failureCode", safeCode));
                }
            });
        }
        catch (RuntimeException ignored) {
            // The original safe error remains the response; startup recovery handles stale UPLOADING rows.
        }
    }

    private void audit(String problemId, String actorUserId, String action, Map<String, Object> detail) {
        problems.insertAudit(
                ids.next().toString(), problemId, null, actorUserId, action, null, writeJson(detail), now());
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        }
        catch (Exception error) {
            throw new IllegalStateException("Could not serialize test data metadata", error);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static ProblemApiException assetProblem(AssetException error) {
        return switch (error.kind()) {
            case PAYLOAD_TOO_LARGE -> new ProblemApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "测试数据 ZIP 超过安全限额。");
            case INVALID_ARCHIVE -> new ProblemApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_TEST_DATA_ARCHIVE", "测试数据 ZIP 格式无效。");
            case NOT_FOUND, STORAGE_UNAVAILABLE -> new ProblemApiException(
                    HttpStatus.SERVICE_UNAVAILABLE, "TEST_DATA_STORAGE_UNAVAILABLE", "测试数据资产暂时不可用。");
        };
    }

    private static ProblemApiException state(String message) {
        return new ProblemApiException(HttpStatus.CONFLICT, "RESOURCE_STATE_CONFLICT", message);
    }

    private static ProblemApiException conflict() {
        return new ProblemApiException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT", "资源已被其他窗口修改，请重新加载。");
    }

    public record ReadyAsset(
            String id,
            String contentSha256,
            TestDataDtos.Manifest manifest,
            Asset asset) implements AutoCloseable {
        public ReadyAsset(String id, String contentSha256, Asset asset) {
            this(id, contentSha256, null, asset);
        }

        public InputStream stream() {
            return asset.stream();
        }

        public long size() {
            return asset.size();
        }

        @Override
        public void close() throws java.io.IOException {
            asset.close();
        }
    }

    private static final class AssetRuntimeException extends RuntimeException {
        AssetRuntimeException(AssetException cause) {
            super(cause);
        }
    }
}
