package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguageRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.ProblemRow;
import com.cherryoj.problemservice.persistence.AdminProblemRows.SampleRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;

@Component
final class AuthoringInput {
    static final int MAX_JOB_BYTES = 8 << 20;
    private static final int MAX_SOURCE_BYTES = 64 << 10;

    record Language(LanguageRow row, String prefix) {}

    record Snapshot(
            CodeMode codeMode,
            String statementDigest,
            String digest,
            List<SampleRow> samples,
            Map<String, Language> languages) {}

    record Frozen(String kind, String snapshotDigest, String json) {}

    private final AdminProblemMapper problems;
    private final ObjectMapper json;

    AuthoringInput(AdminProblemMapper problems, ObjectMapper json) {
        this.problems = problems;
        this.json = json;
    }

    Snapshot snapshot(ProblemRow row) {
        var samples = problems.findSamples(row.id());
        var languages = new TreeMap<String, Language>();
        var configured = problems.findLanguages(row.id());
        for (String id :
                configured.stream().map(LanguageRow::languageId).sorted().toList()) {
            var item = configured.stream()
                    .filter(value -> value.languageId().equals(id))
                    .findFirst()
                    .orElseThrow();
            var prefix = row.codeMode() == CodeMode.CORE ? problems.lockLanguagePrefix(id) : null;
            languages.put(id, new Language(item, prefix == null ? null : prefix.solutionPrefix()));
        }
        var statement = json.createObjectNode();
        statement.put("title", row.title());
        statement.put("statementMarkdown", row.statementMarkdown());
        statement.put("inputDescriptionMarkdown", row.inputDescriptionMarkdown());
        statement.put("outputDescriptionMarkdown", row.outputDescriptionMarkdown());
        statement.put("constraintsMarkdown", row.constraintsMarkdown());
        statement.put("hintMarkdown", row.hintMarkdown());
        statement.set("samples", json.valueToTree(samples));
        String statementDigest = digest(json.writeValueAsString(statement));
        var state = json.createObjectNode();
        state.put("statementDigest", statementDigest);
        state.put("rowVersion", row.rowVersion());
        state.put("testDataLocation", row.testDataLocation());
        state.put("codeMode", row.codeMode().name());
        state.set("languages", json.valueToTree(languages));
        return new Snapshot(
                row.codeMode(),
                statementDigest,
                digest(json.writeValueAsString(state)),
                samples,
                Map.copyOf(languages));
    }

    Frozen build(JsonNode request, Snapshot snapshot) {
        fields(request, "kind", "rowVersion", "input");
        var plan = runnerInput(request.path("input"), snapshot);
        String result = json.writeValueAsString(plan);
        if (bytes(result) > MAX_JOB_BYTES) throw tooLarge();
        return new Frozen("BUILD", snapshot.digest(), result);
    }

    Frozen proposal(JsonNode request, Snapshot snapshot, Function<String, JsonNode> previous) {
        fields(
                request,
                "kind",
                "rowVersion",
                "knowledgePoint",
                "difficulty",
                "goal",
                "basedOnProposalId",
                "revisionInstructions");
        var prompt = json.createObjectNode();
        prompt.put("knowledgePoint", requiredText(request, "knowledgePoint", 160));
        String difficulty = requiredText(request, "difficulty", 12);
        if (!Set.of(Difficulty.values()).stream().anyMatch(value -> value.name().equals(difficulty)))
            throw invalid("difficulty 无效。");
        prompt.put("difficulty", difficulty);
        prompt.put("goal", requiredText(request, "goal", 4000));
        if (request.has("basedOnProposalId") != request.has("revisionInstructions")) {
            throw invalid("修订需同时指定原提案和修订指令。");
        }
        if (request.has("basedOnProposalId")) {
            String previousId = identifier(requiredText(request, "basedOnProposalId", 36));
            prompt.set("previousProposal", previous.apply(previousId));
            prompt.put("revisionInstructions", requiredText(request, "revisionInstructions", 4000));
        }
        String input = json.writeValueAsString(prompt);
        if (bytes(input) > MAX_JOB_BYTES) throw tooLarge();
        return new Frozen("PROPOSAL", snapshot.digest(), input);
    }

    JsonNode candidate(JsonNode candidate) {
        fields(
                candidate,
                "title",
                "statementMarkdown",
                "inputDescriptionMarkdown",
                "outputDescriptionMarkdown",
                "constraintsMarkdown",
                "samples",
                "languages",
                "input");
        requiredText(candidate, "title", 200);
        for (String field : List.of("statementMarkdown", "inputDescriptionMarkdown", "outputDescriptionMarkdown")) {
            requiredText(candidate, field, MAX_SOURCE_BYTES);
        }
        text(candidate, "constraintsMarkdown", MAX_SOURCE_BYTES);
        var samples = new java.util.ArrayList<SampleRow>();
        for (JsonNode sample : array(candidate, "samples", 1, 64)) {
            fields(sample, "ordinal", "input", "output", "explanationMarkdown");
            if (!sample.has("explanationMarkdown")) throw invalid("样例缺少 explanationMarkdown。");
            String explanation = sample.path("explanationMarkdown").isNull()
                    ? null
                    : text(sample, "explanationMarkdown", MAX_SOURCE_BYTES);
            int ordinal = (int) integer(sample, "ordinal", 1, 100);
            if (ordinal != samples.size() + 1) throw invalid("样例编号必须连续。");
            samples.add(new SampleRow(
                    ordinal,
                    text(sample, "input", MAX_SOURCE_BYTES),
                    requiredText(sample, "output", MAX_SOURCE_BYTES),
                    explanation));
        }
        var languages = new TreeMap<String, Language>();
        CodeMode mode = null;
        for (JsonNode item : array(candidate, "languages", 1, 4)) {
            fields(item, "id", "starterCode", "judgeTemplate");
            String id = requiredText(item, "id", 32);
            if (!id.matches("[a-z][a-z0-9-]{0,31}") || languages.containsKey(id) || !item.has("judgeTemplate")) {
                throw invalid("提案语言配置无效。");
            }
            var platform = problems.findLanguagePrefix(id);
            if (platform == null) throw invalid("提案语言未登记。");
            String driver =
                    item.path("judgeTemplate").isNull() ? null : requiredText(item, "judgeTemplate", MAX_SOURCE_BYTES);
            CodeMode next = driver == null ? CodeMode.ACM : CodeMode.CORE;
            if (mode != null && next != mode) throw invalid("提案语言的代码模式必须一致。");
            mode = next;
            languages.put(
                    id,
                    new Language(
                            new LanguageRow(
                                    id,
                                    platform.displayName(),
                                    languages.size(),
                                    text(item, "starterCode", MAX_SOURCE_BYTES),
                                    driver,
                                    null),
                            driver == null ? null : platform.solutionPrefix()));
        }
        runnerInput(candidate.path("input"), new Snapshot(mode, "0".repeat(64), "", samples, languages));
        return candidate;
    }

    private ObjectNode runnerInput(JsonNode plan, Snapshot snapshot) {
        fields(plan, "generatorSource", "validatorSource", "oracleSource", "answerLanguageId", "cases", "languages");
        var runner = json.createObjectNode().put("schemaVersion", 1).put("statementDigest", snapshot.statementDigest());
        for (String name : List.of("generatorSource", "validatorSource", "oracleSource")) {
            runner.put(name, requiredText(plan, name, MAX_SOURCE_BYTES));
        }
        String answer = requiredText(plan, "answerLanguageId", 32);
        runner.put("answerLanguageId", answer);
        var cases = new TreeMap<String, ObjectNode>();
        var samples = snapshot.samples().stream().collect(Collectors.toMap(SampleRow::ordinal, Function.identity()));
        var ordinals = new HashSet<Integer>();
        boolean hasOracle = false;
        for (JsonNode item : array(plan, "cases", 1, 64)) {
            fields(item, "name", "generatorInput", "oracle", "expectedOutput", "sampleOrdinal");
            String name = requiredText(item, "name", 63);
            if (!name.matches("[a-z][a-z0-9-]{0,62}")
                    || cases.containsKey(name)
                    || !item.path("oracle").isBoolean()) {
                throw invalid("测试点名字须唯一，且需明确是否使用独立解。");
            }
            boolean oracle = item.path("oracle").asBoolean();
            hasOracle |= oracle;
            var testcase = json.createObjectNode()
                    .put("name", name)
                    .put("generatorInput", text(item, "generatorInput", MAX_SOURCE_BYTES))
                    .put("oracle", oracle);
            String expected =
                    item.has("expectedOutput") ? requiredText(item, "expectedOutput", MAX_SOURCE_BYTES) : null;
            if (item.has("sampleOrdinal")) {
                int ordinal = (int) integer(item, "sampleOrdinal", 1, 100);
                var sample = samples.get(ordinal);
                if (sample == null || !ordinals.add(ordinal)) throw invalid("公开样例编号不存在或重复。");
                if (expected != null && !expected.equals(sample.expectedOutputText())) throw invalid("独立答案与公开样例输出不一致。");
                expected = source(sample.expectedOutputText(), "样例输出");
                if (bytes(sample.inputText()) > MAX_SOURCE_BYTES) throw tooLarge();
                testcase.put("sampleInput", sample.inputText());
            }
            if (expected != null) testcase.put("expectedOutput", expected);
            else if (!oracle) throw invalid("每个点都需要独立核对依据。");
            cases.put(name, testcase);
        }
        if (!hasOracle || samples.isEmpty() || ordinals.size() != samples.size()) {
            throw invalid("所有公开样例都须绑定测试点，至少一项须使用独立解核对。");
        }
        var caseList = runner.putArray("cases");
        cases.values().forEach(caseList::add);

        var configured = new TreeMap<String, ObjectNode>();
        for (JsonNode item : array(plan, "languages", 1, 4)) {
            fields(item, "languageId", "limits", "referenceSource", "wrongSolutions");
            String id = requiredText(item, "languageId", 32);
            var language = snapshot.languages().get(id);
            if (language == null || configured.containsKey(id)) throw invalid("作答语言未配置或重复。");
            String reference = requiredText(item, "referenceSource", MAX_SOURCE_BYTES);
            var target = json.createObjectNode().put("languageId", id);
            var limits = item.path("limits");
            fields(limits, "cpuNs", "clockNs", "memoryBytes");
            target.putObject("limits")
                    .put("cpuNs", integer(limits, "cpuNs", 1, 10_000_000_000L))
                    .put("clockNs", integer(limits, "clockNs", 1, 120_000_000_000L))
                    .put("memoryBytes", integer(limits, "memoryBytes", 1, 1L << 30));
            boolean core = snapshot.codeMode() == CodeMode.CORE;
            target.set("reference", program(language, reference, core));
            if (core)
                target.set("starter", program(language, source(language.row().starterCode(), "CORE 起始代码"), true));
            var wrong = target.putArray("wrongSolutions");
            for (JsonNode solution : array(item, "wrongSolutions", 1, 16)) {
                fields(solution, "witnessCaseName", "source");
                String witness = requiredText(solution, "witnessCaseName", 63);
                if (!cases.containsKey(witness)) throw invalid("错误解的指定测试点不存在。");
                wrong.addObject()
                        .put("witnessCaseName", witness)
                        .set("program", program(language, requiredText(solution, "source", MAX_SOURCE_BYTES), core));
            }
            configured.put(id, target);
        }
        if (!configured.containsKey(answer)) throw invalid("答案语言必须在作答语言列表中。");
        var languageList = runner.putArray("languages");
        configured.values().forEach(languageList::add);
        return runner;
    }

    private ObjectNode program(Language language, String submission, boolean core) {
        var program = json.createObjectNode();
        if (!core) return program.put("source", submission);
        if (language.prefix() == null) throw invalid("缺少 CORE 语言前缀配置。");
        return program.put("source", source(language.row().judgeTemplate(), "CORE 驱动"))
                .put("solutionSource", source(language.prefix() + submission, "CORE 用户文件"));
    }

    static String identifier(String value) {
        try {
            if (UUID.fromString(value).toString().equalsIgnoreCase(value))
                return value.toLowerCase(java.util.Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            // 统一返回请求错误。
        }
        throw invalid("标识符格式无效。");
    }

    static void fields(JsonNode node, String... names) {
        if (!node.isObject() || !Set.of(names).containsAll(node.propertyNames())) throw invalid("请求包含未知字段或对象格式无效。");
    }

    private static Iterable<JsonNode> array(JsonNode node, String name, int min, int max) {
        var value = node.path(name);
        if (!value.isArray() || value.size() < min || value.size() > max) throw invalid(name + " 数量无效。");
        return value;
    }

    static long integer(JsonNode node, String name, long min, long max) {
        var value = node.path(name);
        if (!value.isIntegralNumber() || !value.canConvertToLong() || value.asLong() < min || value.asLong() > max) {
            throw invalid(name + " 数值无效。");
        }
        return value.asLong();
    }

    static String requiredText(JsonNode node, String name, int limit) {
        String value = text(node, name, limit);
        if (value.isBlank()) throw invalid(name + " 不能为空。");
        return value;
    }

    private static String text(JsonNode node, String name, int limit) {
        var value = node.path(name);
        if (!value.isString() || bytes(value.asText()) > limit) throw invalid(name + " 格式无效或超过大小上限。");
        return value.asText();
    }

    private static String source(String value, String name) {
        if (value == null || value.isBlank() || bytes(value) > MAX_SOURCE_BYTES) throw invalid(name + " 缺失或超过大小上限。");
        return value;
    }

    private static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    static String digest(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static ProblemApiException invalid(String detail) {
        return new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", detail);
    }

    static ProblemApiException tooLarge() {
        return new ProblemApiException(HttpStatus.PAYLOAD_TOO_LARGE, "PAYLOAD_TOO_LARGE", "出题输入超过大小上限。");
    }
}
