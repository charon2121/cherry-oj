package com.cherryoj.judgingservice.judge;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** 构建时直接打包语言契约；新增语言不再复制一份编译阶段白名单。 */
@Component
public final class LanguageCatalog {
    private final Map<String, Boolean> compilation;

    public LanguageCatalog(ObjectMapper json) {
        try (var input = LanguageCatalog.class.getResourceAsStream("/contracts/languages.json")) {
            if (input == null) throw new IllegalStateException("missing contracts/languages.json");
            compilation = read(json.readTree(input));
        } catch (IOException error) {
            throw new IllegalStateException("cannot read contracts/languages.json", error);
        }
    }

    static Map<String, Boolean> read(JsonNode catalog) {
        if (!catalog.path("x-contract-version").isInt()
                || catalog.path("x-contract-version").intValue() != 2
                || !catalog.path("languages").isObject())
            throw new IllegalStateException("unsupported language catalog");
        var result = new HashMap<String, Boolean>();
        for (var entry : catalog.path("languages").properties()) {
            var value = entry.getValue().path("requiresCompilation");
            if (!entry.getKey().matches("^[a-z][a-z0-9-]{0,31}$") || !value.isBoolean())
                throw new IllegalStateException("invalid requiresCompilation for language " + entry.getKey());
            result.put(entry.getKey(), value.booleanValue());
        }
        if (result.isEmpty()) throw new IllegalStateException("empty language catalog");
        return Map.copyOf(result);
    }

    public boolean requiresCompilation(String languageId) {
        Boolean value = compilation.get(languageId);
        if (value == null) throw new IllegalArgumentException("language not in catalog: " + languageId);
        return value;
    }
}
