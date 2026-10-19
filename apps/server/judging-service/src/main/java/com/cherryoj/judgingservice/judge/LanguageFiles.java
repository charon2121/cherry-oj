package com.cherryoj.judgingservice.judge;

import java.util.Map;

/**
 * CORE 的驱动文件名与用户文件名，以 contracts/judge.schema.json 的 x-language-files 为准：judge 按它写文件，
 * 这里按它在报错里认出用户文件。两边各有对齐测试，任何一处改名都会立即失败。
 */
public record LanguageFiles(String driverFile, String solutionFile) {
    private static final Map<String, LanguageFiles> REGISTRY =
            Map.of("cpp", new LanguageFiles("Main.cpp", "solution.cpp"));

    /** 不支持 CORE 的语言返回 null。 */
    public static LanguageFiles of(String languageId) {
        return REGISTRY.get(languageId);
    }

    static Map<String, LanguageFiles> all() {
        return REGISTRY;
    }
}
