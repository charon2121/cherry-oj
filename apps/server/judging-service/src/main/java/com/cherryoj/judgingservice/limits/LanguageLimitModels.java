package com.cherryoj.judgingservice.limits;

import java.util.Map;

/**
 * 各语言的限制参数，以 contracts/languages.json 为准；服务打包后读不到 contracts/，这里保留一份，
 * 由 LanguageLimitModelsContractTests 钉住两边一致。
 */
public final class LanguageLimitModels {
    /** cpp 是“C++ 当量”的定义，必须恒等。 */
    private static final Map<String, LimitModel> MODELS =
            Map.of("cpp", new LimitModel(1000, null, 0, 0, 1000, 0, true));

    private LanguageLimitModels() {}

    /** 没有参数的语言返回 null：它只能靠覆盖标定开放，不能推导。 */
    public static LimitModel of(String languageId) {
        return MODELS.get(languageId);
    }

    static Map<String, LimitModel> all() {
        return MODELS;
    }
}
