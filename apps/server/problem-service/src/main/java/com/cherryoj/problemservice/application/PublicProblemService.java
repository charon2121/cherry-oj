package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.api.PublicProblemDtos;
import com.cherryoj.problemservice.api.PublicProblemDtos.LanguageSummary;
import com.cherryoj.problemservice.api.PublicProblemDtos.ProblemDetail;
import com.cherryoj.problemservice.api.PublicProblemDtos.ProblemLanguage;
import com.cherryoj.problemservice.api.PublicProblemDtos.ProblemList;
import com.cherryoj.problemservice.api.PublicProblemDtos.ProblemSample;
import com.cherryoj.problemservice.api.PublicProblemDtos.ProblemSummary;
import com.cherryoj.problemservice.persistence.PublicProblemMapper;
import com.cherryoj.problemservice.persistence.PublicProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.PublicProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.PublicProblemSearch;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

@Service
public class PublicProblemService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PublicProblemService.class);
    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() { };

    private final PublicProblemMapper mapper;
    private final ProblemCursorCodec cursors;
    private final ObjectMapper objectMapper;

    public PublicProblemService(PublicProblemMapper mapper, ProblemCursorCodec cursors, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.cursors = cursors;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProblemList list(
            String q,
            Difficulty difficulty,
            List<String> requestedTags,
            CodeMode codeMode,
            String language,
            Sort sort,
            String cursor,
            int size) {
        Instant startedAt = Instant.now();
        String normalizedQuery = normalizeQuery(q);
        List<String> tags = normalizeTags(requestedTags);
        String normalizedLanguage = language == null ? null : language.toLowerCase(Locale.ROOT);
        String fingerprint = fingerprint(normalizedQuery, difficulty, tags, codeMode, normalizedLanguage, sort);
        ProblemCursorCodec.CursorPosition position = cursors.decode(cursor, sort.name(), fingerprint);
        PublicProblemSearch search = new PublicProblemSearch(
                escapeLike(normalizedQuery),
                name(difficulty),
                tags,
                name(codeMode),
                normalizedLanguage,
                sort.name(),
                position == null ? null : position.key(),
                position == null ? null : position.id(),
                size + 1);

        List<ProblemRow> rows = new ArrayList<>(mapper.findPublicProblems(search));
        boolean hasMore = rows.size() > size;
        if (hasMore) {
            rows.removeLast();
        }
        Map<String, List<LanguageRow>> languages = languages(rows);
        List<ProblemSummary> items = rows.stream()
                .map(row -> summary(row, languages.getOrDefault(row.versionId(), List.of())))
                .toList();
        String nextCursor = hasMore && !rows.isEmpty()
                ? cursorFor(rows.getLast(), sort, fingerprint)
                : null;
        LOGGER.atInfo()
                .addKeyValue("event", "public_problem_list")
                .addKeyValue("result_count", items.size())
                .addKeyValue("has_more", hasMore)
                .addKeyValue("elapsed_ms", Duration.between(startedAt, Instant.now()).toMillis())
                .log("Public problem list completed");
        return new ProblemList(items, nextCursor, hasMore);
    }

    @Transactional(readOnly = true)
    public ProblemDetail detail(String slug) {
        ProblemRow row = mapper.findPublicProblemBySlug(slug);
        if (row == null) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "PROBLEM_NOT_FOUND", "公开题目不存在。");
        }
        List<ProblemSample> samples = mapper.findSamples(row.versionId()).stream()
                .map(sample -> new ProblemSample(
                        sample.ordinal(), sample.inputText(), sample.expectedOutputText(), sample.explanationMarkdown()))
                .toList();
        List<ProblemLanguage> languages = mapper.findLanguages(List.of(row.versionId())).stream()
                .map(languageRow -> new ProblemLanguage(
                        languageRow.languageId(), displayName(languageRow.languageId()), languageRow.starterCode()))
                .toList();
        return new ProblemDetail(
                row.problemId(), row.versionId(), row.versionNo(), row.slug(), row.codeMode(), row.title(),
                row.difficulty(), tags(row), row.statementMarkdown(), row.inputDescriptionMarkdown(),
                row.outputDescriptionMarkdown(), row.constraintsMarkdown(), row.hintMarkdown(), samples, languages);
    }

    private Map<String, List<LanguageRow>> languages(List<ProblemRow> rows) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        List<String> ids = rows.stream().map(ProblemRow::versionId).toList();
        return mapper.findLanguages(ids).stream().collect(Collectors.groupingBy(
                LanguageRow::versionId,
                Collectors.collectingAndThen(Collectors.toList(), values -> values.stream()
                        .sorted(Comparator.comparingInt(LanguageRow::displayOrder))
                        .toList())));
    }

    private ProblemSummary summary(ProblemRow row, List<LanguageRow> languages) {
        return new ProblemSummary(
                row.problemId(), row.slug(), row.versionId(), row.versionNo(), row.title(), row.difficulty(),
                tags(row), row.codeMode(), languages.stream()
                        .map(language -> new LanguageSummary(language.languageId(), displayName(language.languageId())))
                        .toList());
    }

    private List<String> tags(ProblemRow row) {
        try {
            return List.copyOf(objectMapper.readValue(row.tagsJson(), STRING_LIST));
        }
        catch (Exception error) {
            throw new IllegalStateException("Published problem tags are invalid", error);
        }
    }

    private String cursorFor(ProblemRow row, Sort sort, String fingerprint) {
        String key = sort == Sort.TITLE_ASC
                ? row.title()
                : row.updatedAt().format(ProblemCursorCodec.DATE_TIME);
        return cursors.encode(sort.name(), key, row.problemId(), fingerprint);
    }

    private static String normalizeQuery(String q) {
        if (q == null) {
            return null;
        }
        String normalized = q.trim();
        if (normalized.isEmpty() || normalized.length() > 100) {
            throw new ProblemApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "关键词长度必须在 1 到 100 之间。");
        }
        return normalized;
    }

    private static List<String> normalizeTags(List<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return List.of();
        }
        if (requested.size() > 10) {
            throw new ProblemApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "标签筛选最多包含 10 项。");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String tag : requested) {
            String value = tag == null ? "" : tag.trim();
            if (value.isEmpty() || value.length() > 32) {
                throw new ProblemApiException(HttpStatus.BAD_REQUEST, "INVALID_QUERY", "标签长度必须在 1 到 32 之间。");
            }
            normalized.add(value);
        }
        return normalized.stream().sorted().toList();
    }

    private static String fingerprint(
            String q, Difficulty difficulty, List<String> tags, CodeMode codeMode, String language, Sort sort) {
        try {
            String source = String.join("\u001f",
                    value(q), name(difficulty), String.join("\u001e", tags), name(codeMode), value(language), sort.name());
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(source.getBytes(StandardCharsets.UTF_8)));
        }
        catch (Exception error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
    }

    private static String escapeLike(String value) {
        return value == null ? null : value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String displayName(String languageId) {
        return "cpp".equals(languageId) ? "C++" : languageId.toUpperCase(Locale.ROOT);
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }

    public enum Difficulty { UNRATED, EASY, MEDIUM, HARD }

    public enum CodeMode { ACM, CORE }

    public enum Sort { UPDATED_DESC, UPDATED_ASC, TITLE_ASC }
}
