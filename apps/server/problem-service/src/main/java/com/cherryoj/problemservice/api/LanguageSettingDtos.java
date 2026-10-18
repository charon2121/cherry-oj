package com.cherryoj.problemservice.api;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** 语言的平台级设置（contracts/web-api.openapi.json 的 AdminLanguageSetting）：目前只有 CORE 用户文件的前缀。 */
public final class LanguageSettingDtos {

    private LanguageSettingDtos() {
    }

    public record LanguageSetting(String id, String displayName, String solutionPrefix, LocalDateTime updatedAt) {
    }

    public record LanguageSettingList(List<LanguageSetting> items) {
    }

    /** 语言表没有 rowVersion：以读到的 updatedAt 作为并发前提。 */
    public record UpdateLanguageSettingRequest(
            @NotNull @Size(min = 1, max = 65_536) String solutionPrefix,
            @NotNull LocalDateTime expectedUpdatedAt) {

        @Override
        public String toString() {
            return "UpdateLanguageSettingRequest[solutionPrefix=<" + solutionPrefix.length() + " chars>, expectedUpdatedAt="
                    + expectedUpdatedAt + "]";
        }
    }
}
