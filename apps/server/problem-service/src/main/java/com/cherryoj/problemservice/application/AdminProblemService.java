package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos;
import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateRevisionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Problem;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemPage;
import com.cherryoj.problemservice.api.AdminProblemDtos.ProblemStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.UpdateVersionRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Version;
import com.cherryoj.problemservice.api.AdminProblemDtos.VersionStatus;
import com.cherryoj.problemservice.api.AdminProblemDtos.Visibility;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.PublicProblemDtos;
import com.cherryoj.problemservice.domain.UuidV7;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.VersionRow;
import com.cherryoj.problemservice.persistence.TestDataMapper;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.core.type.TypeReference;

@Service
public class AdminProblemService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final AdminProblemMapper mapper;
    private final UuidV7 ids;
    private final Clock clock;
    private final ObjectMapper json;
    private final TestDataMapper testData;
    private final TestDataViewMapper testDataViews;

    public AdminProblemService(
            AdminProblemMapper mapper,
            UuidV7 ids,
            Clock clock,
            ObjectMapper json,
            TestDataMapper testData,
            TestDataViewMapper testDataViews) {
        this.mapper = mapper;
        this.ids = ids;
        this.clock = clock;
        this.json = json;
        this.testData = testData;
        this.testDataViews = testDataViews;
    }

    @Transactional(readOnly = true)
    public ProblemPage list(String q, ProblemStatus status, int page, int size) {
        if (page < 1 || size < 1 || size > 100) {
            throw validation("分页参数无效。");
        }
        String query = normalizeSearch(q);
        long total = mapper.countProblems(query, status);
        List<ProblemRow> rows = mapper.listProblems(query, status, (long) (page - 1) * size, size);
        Map<String, List<VersionRow>> versions = rows.isEmpty()
                ? Map.of()
                : mapper.findVersionsForProblems(rows.stream().map(ProblemRow::id).toList()).stream()
                        .collect(Collectors.groupingBy(VersionRow::problemId));
        List<Problem> items = rows.stream().map(row -> problem(row, versions.getOrDefault(row.id(), List.of()))).toList();
        return new ProblemPage(items, page, size, total, total == 0 ? 0 : (int) ((total + size - 1) / size));
    }

    @Transactional(readOnly = true)
    public Problem getProblem(String problemId) {
        ProblemRow row = requireProblem(problemId, false);
        return problem(row, mapper.findVersions(problemId));
    }

    @Transactional
    public Problem create(CreateProblemRequest request, String actorUserId) {
        requireFirstVersion(request.codeMode(), request.languageId());
        LocalDateTime now = now();
        String problemId = ids.next().toString();
        String versionId = ids.next().toString();
        ProblemRow problem = new ProblemRow(
                problemId, request.slug(), Visibility.PRIVATE, ProblemStatus.ACTIVE, null, now, now, 0);
        VersionRow version = new VersionRow(
                versionId, problemId, 1, VersionStatus.DRAFT, CodeMode.ACM, request.title(), "", "", "",
                null, null, request.difficulty(), "[]", null, null, now, now, null, 0);
        try {
            mapper.insertProblem(problem, actorUserId);
            mapper.insertVersion(version, actorUserId);
            mapper.insertLanguage(versionId, "cpp", 1, "");
            audit(problemId, actorUserId, "PROBLEM_CREATED", Map.of("versionNo", 1));
        }
        catch (DuplicateKeyException error) {
            throw new ProblemApiException(HttpStatus.CONFLICT, "SLUG_CONFLICT", "题目标识已存在。");
        }
        return getProblem(problemId);
    }

    @Transactional
    public Problem updateProblem(String problemId, UpdateProblemRequest request, String actorUserId) {
        ProblemRow current = requireProblem(problemId, true);
        requireRowVersion(current.rowVersion(), request.rowVersion());
        if (current.status() != ProblemStatus.ACTIVE) {
            throw state("归档题目不可修改。");
        }
        if (request.visibility() == Visibility.PUBLIC && current.currentPublishedVersionId() == null) {
            throw state("没有已发布版本的题目不能设为公开。");
        }
        try {
            if (mapper.updateProblem(problemId, request.slug(), request.visibility(), now(), request.rowVersion()) != 1) {
                throw conflict();
            }
        }
        catch (DuplicateKeyException error) {
            throw new ProblemApiException(HttpStatus.CONFLICT, "SLUG_CONFLICT", "题目标识已存在。");
        }
        audit(problemId, actorUserId, "PROBLEM_UPDATED", Map.of("visibility", request.visibility().name()));
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

    @Transactional(readOnly = true)
    public Version getVersion(String problemId, String versionId) {
        requireProblem(problemId, false);
        return version(requireVersion(problemId, versionId, false));
    }

    @Transactional
    public Version updateVersion(
            String problemId, String versionId, UpdateVersionRequest request, String actorUserId) {
        requireProblem(problemId, true);
        VersionRow current = requireVersion(problemId, versionId, true);
        requireEditable(current);
        requireRowVersion(current.rowVersion(), request.rowVersion());
        List<String> tags = normalizeTags(request.tags());
        validateSamples(request.samples());
        LocalDateTime now = now();
        VersionRow changed = new VersionRow(
                current.id(), current.problemId(), current.versionNo(), current.status(), current.codeMode(),
                request.title(), request.statementMarkdown(), request.inputDescriptionMarkdown(),
                request.outputDescriptionMarkdown(), request.constraintsMarkdown(), request.hintMarkdown(),
                request.difficulty(), writeJson(tags), current.testDataVersionId(), request.changeSummary(),
                current.createdAt(), now, current.publishedAt(), current.rowVersion());
        if (mapper.updateDraft(changed, request.rowVersion()) != 1) {
            throw conflict();
        }
        mapper.deleteSamples(versionId);
        mapper.deleteLanguages(versionId);
        for (var sample : request.samples()) {
            mapper.insertSample(ids.next().toString(), versionId, sample.ordinal(), sample.input(), sample.output(),
                    sample.explanationMarkdown());
        }
        mapper.insertLanguage(versionId, "cpp", 1, request.starterCode());
        audit(problemId, actorUserId, "DRAFT_UPDATED", Map.of(
                "versionNo", current.versionNo(), "sampleCount", request.samples().size(), "tagCount", tags.size()));
        return getVersion(problemId, versionId);
    }

    @Transactional
    public Version createRevision(
            String problemId, CreateRevisionRequest request, String actorUserId) {
        ProblemRow problem = requireProblem(problemId, true);
        requireRowVersion(problem.rowVersion(), request.rowVersion());
        if (problem.status() != ProblemStatus.ACTIVE) {
            throw state("归档题目不能创建修订。");
        }
        if (mapper.countDrafts(problemId) > 0) {
            throw state("题目已经存在未发布版本。");
        }
        VersionRow source = problem.currentPublishedVersionId() == null
                ? null
                : requireVersion(problemId, problem.currentPublishedVersionId(), true);
        if (source != null && source.status() != VersionStatus.PUBLISHED) {
            throw state("当前公开版本状态无效。");
        }
        LocalDateTime now = now();
        int versionNo = mapper.maxVersionNo(problemId) + 1;
        String versionId = ids.next().toString();
        VersionRow revision = source == null
                ? new VersionRow(versionId, problemId, versionNo, VersionStatus.DRAFT, CodeMode.ACM,
                        "未命名题目", "", "", "", null, null, AdminProblemDtos.Difficulty.UNRATED,
                        "[]", null, null, now, now, null, 0)
                : new VersionRow(versionId, problemId, versionNo, VersionStatus.DRAFT, source.codeMode(),
                        source.title(), source.statementMarkdown(), source.inputDescriptionMarkdown(),
                        source.outputDescriptionMarkdown(), source.constraintsMarkdown(), source.hintMarkdown(),
                        source.difficulty(), source.tagsJson(), request.reuseTestData() ? source.testDataVersionId() : null,
                        null, now, now, null, 0);
        mapper.insertVersion(revision, actorUserId);
        if (source == null) {
            mapper.insertLanguage(versionId, "cpp", 1, "");
        }
        else {
            for (var sample : mapper.findSamples(source.id())) {
                mapper.insertSample(ids.next().toString(), versionId, sample.ordinal(), sample.inputText(),
                        sample.expectedOutputText(), sample.explanationMarkdown());
            }
            for (LanguageRow language : mapper.findLanguages(source.id())) {
                mapper.insertLanguage(versionId, language.languageId(), language.displayOrder(), language.starterCode());
            }
        }
        if (mapper.touchProblem(problemId, now, request.rowVersion()) != 1) {
            throw conflict();
        }
        audit(problemId, actorUserId, "REVISION_CREATED", Map.of(
                "versionNo", versionNo, "reusedTestData", request.reuseTestData() && source != null));
        return getVersion(problemId, versionId);
    }

    @Transactional
    public void deleteDraft(String problemId, String versionId, long rowVersion, String actorUserId) {
        requireProblem(problemId, true);
        VersionRow current = requireVersion(problemId, versionId, true);
        requireEditable(current);
        requireRowVersion(current.rowVersion(), rowVersion);
        mapper.deleteSamples(versionId);
        mapper.deleteLanguages(versionId);
        if (mapper.deleteDraft(problemId, versionId) != 1) {
            throw state("草稿已经被引用或状态已改变。");
        }
        audit(problemId, actorUserId, "DRAFT_DELETED", Map.of("versionNo", current.versionNo()));
    }

    @Transactional(readOnly = true)
    public PublicProblemDtos.ProblemDetail preview(String problemId, String versionId) {
        ProblemRow problem = requireProblem(problemId, false);
        Version value = version(requireVersion(problemId, versionId, false));
        return new PublicProblemDtos.ProblemDetail(
                problem.id(), value.id(), value.versionNo(), problem.slug(), value.codeMode().name(), value.title(),
                value.difficulty().name(), value.tags(), value.statementMarkdown(), value.inputDescriptionMarkdown(),
                value.outputDescriptionMarkdown(), value.constraintsMarkdown(), value.hintMarkdown(), value.samples(),
                value.allowedLanguages().stream().map(language -> new PublicProblemDtos.ProblemLanguage(
                        language.id(), language.displayName(), language.starterCode())).toList());
    }

    private Problem problem(ProblemRow row, List<VersionRow> versions) {
        return new Problem(
                row.id(), row.slug(), row.visibility(), row.status(), row.currentPublishedVersionId(),
                versions.stream().map(version -> new AdminProblemDtos.VersionSummary(
                        version.id(), version.versionNo(), version.status(), version.title(), version.updatedAt(),
                        version.publishedAt(), version.rowVersion())).toList(),
                row.createdAt(), row.updatedAt(), row.rowVersion());
    }

    private Version version(VersionRow row) {
        List<PublicProblemDtos.ProblemSample> samples = mapper.findSamples(row.id()).stream()
                .map(sample -> new PublicProblemDtos.ProblemSample(
                        sample.ordinal(), sample.inputText(), sample.expectedOutputText(), sample.explanationMarkdown()))
                .toList();
        List<AdminProblemDtos.Language> languages = mapper.findLanguages(row.id()).stream()
                .map(language -> new AdminProblemDtos.Language(
                        language.languageId(), displayName(language.languageId()), language.starterCode()))
                .toList();
        return new Version(
                row.id(), row.problemId(), row.versionNo(), row.status(), row.codeMode(), row.title(),
                row.statementMarkdown(), row.inputDescriptionMarkdown(), row.outputDescriptionMarkdown(),
                row.constraintsMarkdown(), row.hintMarkdown(), row.difficulty(), readTags(row.tagsJson()), samples,
                languages,
                row.testDataVersionId() == null
                        ? null
                        : testDataViews.map(testData.find(row.problemId(), row.testDataVersionId())),
                row.changeSummary(), row.createdAt(), row.updatedAt(), row.publishedAt(), row.rowVersion());
    }

    private ProblemRow requireProblem(String problemId, boolean forUpdate) {
        ProblemRow row = forUpdate ? mapper.findProblemForUpdate(problemId) : mapper.findProblem(problemId);
        if (row == null) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "题目不存在。");
        }
        return row;
    }

    private VersionRow requireVersion(String problemId, String versionId, boolean forUpdate) {
        VersionRow row = forUpdate
                ? mapper.findVersionForUpdate(problemId, versionId)
                : mapper.findVersion(problemId, versionId);
        if (row == null) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_VERSION_NOT_FOUND", "题目版本不存在。");
        }
        return row;
    }

    private static void requireEditable(VersionRow version) {
        if (version.status() != VersionStatus.DRAFT) {
            throw state("只有 DRAFT 版本可以编辑或删除。");
        }
    }

    private static void requireFirstVersion(CodeMode mode, String languageId) {
        if (mode != CodeMode.ACM || !"cpp".equals(languageId)) {
            throw validation("首版只支持 C++ ACM 题目。");
        }
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
        mapper.insertAudit(ids.next().toString(), problemId, null, actorUserId, action, null, writeJson(detail), now());
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
}
