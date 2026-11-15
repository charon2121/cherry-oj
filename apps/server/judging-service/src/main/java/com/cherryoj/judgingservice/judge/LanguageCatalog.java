package com.cherryoj.judgingservice.judge;

import com.cherryoj.judgingservice.limits.LimitModel;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 构建时直接打包语言契约；文件名、限制参数与编译阶段声明只从这一份目录读取。 */
@Component
public final class LanguageCatalog {
    private final Map<String, Entry> languages;

    record Entry(boolean requiresCompilation, LanguageFiles files, LimitModel limitModel) {}

    public LanguageCatalog(ObjectMapper json) {
        try (var input = LanguageCatalog.class.getResourceAsStream("/contracts/languages.json")) {
            if (input == null) throw new IllegalStateException("missing contracts/languages.json");
            languages = read(json.readTree(input));
        } catch (IOException error) {
            throw new IllegalStateException("cannot read contracts/languages.json", error);
        }
    }

    static Map<String, Entry> read(JsonNode catalog) {
        if (!catalog.path("x-contract-version").isInt()
                || catalog.path("x-contract-version").intValue() != 2
                || !catalog.path("languages").isObject())
            throw new IllegalStateException("unsupported language catalog");
        var result = new HashMap<String, Entry>();
        for (var entry : catalog.path("languages").properties()) {
            String id = entry.getKey();
            if (!id.matches("^[a-z][a-z0-9-]{0,31}$")) throw new IllegalStateException("invalid language id: " + id);
            try {
                JsonNode value = entry.getValue();
                var model = readModel(value.path("limitModel"));
                if ("cpp".equals(id) && !new LimitModel(1000, null, 0, 0, 1000, 0, true).equals(model))
                    throw new IllegalArgumentException("cpp limitModel must be the identity model");
                result.put(id, new Entry(bool(value, "requiresCompilation"), readFiles(value.path("files")), model));
            } catch (IllegalArgumentException error) {
                throw new IllegalStateException(
                        "invalid language catalog entry: " + id + ": " + error.getMessage(), error);
            }
        }
        if (result.isEmpty()) throw new IllegalStateException("empty language catalog");
        return Map.copyOf(result);
    }

    public boolean requiresCompilation(String languageId) {
        Entry value = languages.get(languageId);
        if (value == null) throw new IllegalArgumentException("language not in catalog: " + languageId);
        return value.requiresCompilation();
    }

    /** 没有用户文件的语言不支持 CORE；未知语言也不推测文件名。 */
    public LanguageFiles coreFiles(String languageId) {
        Entry value = languages.get(languageId);
        return value == null || value.files().solutionFile() == null ? null : value.files();
    }

    /** 没有参数的语言只能靠覆盖标定开放，不能推导。 */
    public LimitModel limitModel(String languageId) {
        Entry value = languages.get(languageId);
        return value == null ? null : value.limitModel();
    }

    private static LanguageFiles readFiles(JsonNode files) {
        if (!files.path("driverFile").isString()
                || !(files.path("solutionFile").isString()
                        || files.path("solutionFile").isNull()))
            throw new IllegalArgumentException("files must declare driverFile and solutionFile");
        return new LanguageFiles(
                files.path("driverFile").asString(),
                files.path("solutionFile").isNull()
                        ? null
                        : files.path("solutionFile").asString());
    }

    private static LimitModel readModel(JsonNode model) {
        if (model.isMissingNode() || model.isNull()) return null;
        JsonNode cap = model.path("cpuFactorCapPermille");
        return new LimitModel(
                integer(model, "cpuFactorPermille"),
                cap.isNull() ? null : integer(model, "cpuFactorCapPermille"),
                integer(model, "cpuOverheadNs"),
                integer(model, "ioPicosPerByte"),
                integer(model, "memoryFactorPermille"),
                integer(model, "memoryOverheadBytes"),
                bool(model, "baseEligible"));
    }

    private static long integer(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong())
            throw new IllegalArgumentException(field + " must be an int64 integer");
        return value.longValue();
    }

    private static boolean bool(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (!value.isBoolean()) throw new IllegalArgumentException(field + " must be a boolean");
        return value.booleanValue();
    }
}
