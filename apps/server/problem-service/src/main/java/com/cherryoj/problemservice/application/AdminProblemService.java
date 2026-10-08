package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Problem;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemPage;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemSummary;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.PublicProblemDtos;
import com.cherryoj.problemservice.api.TestDataDtos;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.SampleRow;
import com.cherryoj.problemservice.storage.TestDataStore;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * 题目没有版本：题面、样例、语言和测试数据地址都直接属于题目，改了就是改了。
 * 可见性（私有/公开）由 {@link ProblemPublicationService} 管理，测试数据由 {@link TestDataService} 管理。
 */
@Service
public class AdminProblemService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final AdminProblemMapper mapper;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TestDataStore testDataStore;

    public AdminProblemService(
            AdminProblemMapper mapper, UuidV7 ids, Clock clock, ObjectMapper json, TestDataStore testDataStore) {
        this.mapper = mapper;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.testDataStore = testDataStore;
    }

    @Transactional(readOnly = true)
    public ProblemPage list(String q, ProblemStatus status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw validation("分页参数无效。");
        }
        String query = normalizeSearch(q);
        long total = mapper.countProblems(query, status);
        List<ProblemSummary> items = mapper.listProblems(query, status, (long) (page - 1) * size, size).stream()
                .map(row -> new ProblemSummary(row.id(), row.slug(), row.title(), row.visibility(), row.status(),
                        row.difficulty(), row.testDataLocation() != null, row.updatedAt(), row.publishedAt(),
                        row.rowVersion()))
                .toList();
        return new ProblemPage(items, page, size, total, total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public Problem getProblem(String problemId) {
        return problem(requireProblem(problemId, false));
    }

    @Transactional
    public Problem create(CreateProblemRequest request, String actorUserId) {
        if (request.codeMode() != CodeMode.ACM || !"cpp".equals(request.languageId())) {
            throw validation("目前只支持 C++ ACM 题目。");
        }
        LocalDateTime now = now();
        String problemId = ids.next().toString();
        ProblemRow row = new ProblemRow(
                problemId, request.slug(), Visibility.PRIVATE, ProblemStatus.ACTIVE, CodeMode.ACM, request.title(),
                "", "", "", null, null, request.difficulty(), "[]", null, null, now, now, null, 0);
        try {
            mapper.insertProblem(row, actorUserId);
            mapper.insertLanguage(problemId, "cpp", 1, "");
            audit(problemId, actorUserId, "PROBLEM_CREATED", Map.of());
        }
        catch (DuplicateKeyException error) {
            throw slugConflict();
        }
        return getProblem(problemId);
    }

    @Transactional
    public Problem update(String problemId, UpdateProblemRequest request, String actorUserId) {
        ProblemRow current = requireProblem(problemId, true);
        requireRowVersion(current.rowVersion(), request.rowVersion());
        if (current.status() != ProblemStatus.ACTIVE) {
            throw state("归档题目不可修改。");
        }
        List<String> tags = normalizeTags(request.tags());
        validateSamples(request.samples());
        // 公开题目的修改立即生效，所以不能把它改成用户看不懂的样子；要大改请先转为私有。
        if (current.visibility() == Visibility.PUBLIC
                && (!hasContent(request.title(), request.statementMarkdown(), request.inputDescriptionMarkdown(),
                        request.outputDescriptionMarkdown()) || request.samples().isEmpty())) {
            throw validation("已公开的题目必须保留完整题面和至少一个样例；需要大改请先转为私有。");
        }
        LocalDateTime now = now();
        ProblemRow changed = new ProblemRow(
                current.id(), request.slug(), current.visibility(), current.status(), current.codeMode(),
                request.title(), request.statementMarkdown(), request.inputDescriptionMarkdown(),
                request.outputDescriptionMarkdown(), request.constraintsMarkdown(), request.hintMarkdown(),
                request.difficulty(), writeJson(tags), current.testDataLocation(), current.testDataUpdatedAt(),
                current.createdAt(), now, current.publishedAt(), current.rowVersion());
        try {
            if (mapper.updateContent(changed, request.rowVersion()) != 1) {
                throw conflict();
            }
        }
        catch (DuplicateKeyException error) {
            throw slugConflict();
        }
        mapper.deleteSamples(problemId);
        mapper.deleteLanguages(problemId);
        for (var sample : request.samples()) {
            mapper.insertSample(ids.next().toString(), problemId, sample.ordinal(), sample.input(), sample.output(),
                    sample.explanationMarkdown());
        }
        mapper.insertLanguage(problemId, "cpp", 1, request.starterCode());
        audit(problemId, actorUserId, "PROBLEM_UPDATED", Map.of(
                "sampleCount", request.samples().size(), "tagCount", tags.size()));
        return getProblem(problemId);
    }

    @Transactional
    public Problem archive(String problemId, long rowVersion, String actorUserId) {
        ProblemRow current = requireProblem(problemId, true);
        requireRowVersion(current.rowVersion(), rowVersion);
        if (mapper.archiveProblem(problemId, now(), rowVersion) != 1) {
            throw state("题目已经归档或状态已改变。");
        }
        audit(problemId, actorUserId, "PROBLEM_ARCHIVED", Map.of());
        return getProblem(problemId);
    }

    /** 只能删从未公开过的题目：它不可能有提交。公开过的题目只能归档。 */
    @Transactional
    public void delete(String problemId, long rowVersion) {
        ProblemRow current = requireProblem(problemId, true);
        requireRowVersion(current.rowVersion(), rowVersion);
        if (current.publishedAt() != null) {
            throw state("公开过的题目不能删除，请归档。");
        }
        // 审计事件挂在题目上，题目删掉时一并删：这道题从未对外生效，没有需要保留的事实。
        mapper.deleteSamples(problemId);
        mapper.deleteLanguages(problemId);
        mapper.deleteAudits(problemId);
        if (mapper.deleteNeverPublished(problemId, rowVersion) != 1) {
            throw conflict();
        }
        // 数据库提交之后才动文件：事务回滚时不能把测试数据已经删掉。
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                testDataStore.remove(problemId);
            }
        });
    }

    @Transactional(readOnly = true)
    public PublicProblemDtos.ProblemDetail preview(String problemId) {
        Problem value = problem(requireProblem(problemId, false));
        return new PublicProblemDtos.ProblemDetail(
                value.id(), value.slug(), value.codeMode().name(), value.title(), value.difficulty().name(),
                value.tags(), value.statementMarkdown(), value.inputDescriptionMarkdown(),
                value.outputDescriptionMarkdown(), value.constraintsMarkdown(), value.hintMarkdown(), value.samples(),
                value.allowedLanguages().stream().map(language -> new PublicProblemDtos.ProblemLanguage(
                        language.id(), language.displayName(), language.starterCode())).toList());
    }

    private Problem problem(ProblemRow row) {
        List<PublicProblemDtos.ProblemSample> samples = mapper.findSamples(row.id()).stream()
                .map((SampleRow sample) -> new PublicProblemDtos.ProblemSample(
                        sample.ordinal(), sample.inputText(), sample.expectedOutputText(), sample.explanationMarkdown()))
                .toList();
        List<AdminProblemDtos.Language> languages = mapper.findLanguages(row.id()).stream()
                .map((LanguageRow language) -> new AdminProblemDtos.Language(
                        language.languageId(), displayName(language.languageId()), language.starterCode()))
                .toList();
        return new Problem(
                row.id(), row.slug(), row.visibility(), row.status(), row.codeMode(), row.title(),
                row.statementMarkdown(), row.inputDescriptionMarkdown(), row.outputDescriptionMarkdown(),
                row.constraintsMarkdown(), row.hintMarkdown(), row.difficulty(), readTags(row.tagsJson()), samples,
                languages, testData(row), row.createdAt(), row.updatedAt(), row.publishedAt(), row.rowVersion());
    }

    /** 读不到的测试数据按「没有」展示；公开检查会指出具体原因。 */
    private TestDataDtos.TestData testData(ProblemRow row) {
        if (row.testDataLocation() == null) {
            return null;
        }
        try {
            TestDataStore.Info info = testDataStore.describe(row.testDataLocation());
            return new TestDataDtos.TestData(
                    info.digest(), info.testcaseCount(), info.totalBytes(), row.testDataUpdatedAt(), info.manifest());
        }
        catch (TestDataStore.AssetException error) {
            return null;
        }
    }

    private ProblemRow requireProblem(String problemId, boolean forUpdate) {
        ProblemRow row = forUpdate ? mapper.findProblemForUpdate(problemId) : mapper.findProblem(problemId);
        if (row == null) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        }
        return row;
    }

    private static boolean hasContent(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) {
                return false;
            }
        }
        return true;
    }

    private static void requireRowVersion(long actual, long expected) {
        if (actual != expected) {
            throw conflict();
        }
    }

    private static void validateSamples(List<AdminProblemDtos.SampleInput> samples) {
        for (int index = 0; index < samples.size(); index++) {
            if (samples.get(index).ordinal() != index + 1) {
                throw validation("样例 ordinal 必须从 1 连续递增。");
            }
        }
    }

    private static List<String> normalizeTags(List<String> tags) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String tag : tags) {
            String value = tag.trim();
            if (value.isEmpty() || value.length() > 32 || !values.add(value)) {
                throw validation("标签必须非空、长度不超过 32 且不能重复。");
            }
        }
        return List.copyOf(values);
    }

    private String writeJson(Object value) {
        try {
            return json.writeValueAsString(value);
        }
        catch (Exception error) {
            throw new IllegalStateException("Could not serialize problem data", error);
        }
    }

    private List<String> readTags(String tagsJson) {
        try {
            return List.copyOf(json.readValue(tagsJson, STRING_LIST));
        }
        catch (Exception error) {
            throw new IllegalStateException("Problem tags are invalid", error);
        }
    }

    private void audit(String problemId, String actorUserId, String action, Map<String, Object> detail) {
        mapper.insertAudit(ids.next().toString(), problemId, actorUserId, action, null, writeJson(detail), now());
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private static String normalizeSearch(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        String value = query.trim();
        if (value.length() > 100) {
            throw validation("查询关键词不能超过 100 个字符。");
        }
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String displayName(String languageId) {
        return "cpp".equals(languageId) ? "C++" : languageId;
    }

    private static ProblemApiException validation(String message) {
        return new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
    }

    private static ProblemApiException state(String message) {
        return new ProblemApiException(HttpStatus.CONFLICT, "RESOURCE_STATE_CONFLICT", message);
    }

    private static ProblemApiException conflict() {
        return new ProblemApiException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT", "资源已被其他窗口修改，请重新加载。");
    }

    private static ProblemApiException slugConflict() {
        return new ProblemApiException(HttpStatus.CONFLICT, "SLUG_CONFLICT", "题目标识已存在。");
    }
}
