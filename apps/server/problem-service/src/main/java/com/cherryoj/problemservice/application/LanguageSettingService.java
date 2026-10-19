package com.cherryoj.problemservice.application;

import com.cherryoj.problemservice.api.LanguageSettingDtos.LanguageSetting;
import com.cherryoj.problemservice.api.LanguageSettingDtos.LanguageSettingList;
import com.cherryoj.problemservice.api.LanguageSettingDtos.UpdateLanguageSettingRequest;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.persistence.AdminProblemMapper;
import com.cherryoj.problemservice.persistence.AdminProblemRows.LanguagePrefixRow;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 语言前缀：该语言所有 CORE 题共用，用户文件 = 前缀 + 用户代码。改了立即对之后的提交、自测与标定生效，已冻结的判题输入不变；
 * 已标定的题目只在公开检查里提示重新标定（比较 updated_at），不自动失效。
 */
@Service
public class LanguageSettingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(LanguageSettingService.class);

    private final AdminProblemMapper mapper;
    private final Clock clock;

    public LanguageSettingService(AdminProblemMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    public LanguageSettingList list() {
        return new LanguageSettingList(mapper.findLanguageSettings().stream()
                .map(LanguageSettingService::view)
                .toList());
    }

    @Transactional
    public LanguageSetting update(String languageId, UpdateLanguageSettingRequest request, String actorUserId) {
        LanguagePrefixRow current = mapper.findLanguagePrefix(languageId);
        if (current == null) {
            throw new ProblemApiException(HttpStatus.NOT_FOUND, "LANGUAGE_NOT_FOUND", "语言不存在。");
        }
        String prefix = request.solutionPrefix();
        // 前缀行数 = 换行个数，judging-service 据此换算报错行号；不以换行结尾时最后一行会和用户代码的第一行粘在一起。
        if (!prefix.endsWith("\n")) {
            throw validation("前缀代码必须以换行结尾。");
        }
        if (prefix.indexOf('\r') >= 0 || prefix.indexOf('\0') >= 0) {
            throw validation("前缀代码只能用 \\n 换行，且不能包含空字符。");
        }
        if (prefix.equals(current.solutionPrefix())) {
            return view(current);
        }
        LocalDateTime now =
                LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (mapper.updateLanguagePrefix(languageId, prefix, request.expectedUpdatedAt(), now) != 1) {
            throw new ProblemApiException(HttpStatus.CONFLICT, "ROW_VERSION_CONFLICT", "前缀已被其他窗口修改，请重新加载。");
        }
        // 语言不是题目，挂不进 problem_audit_event；留一条结构化日志，只记指纹。
        LOGGER.atInfo()
                .addKeyValue("event", "language.prefix_changed")
                .addKeyValue("language_id", languageId)
                .addKeyValue("actor_user_id", actorUserId)
                .addKeyValue("previous_sha256", sha256(current.solutionPrefix()))
                .addKeyValue("solution_prefix_sha256", sha256(prefix))
                .log("Language solution prefix changed");
        return view(mapper.findLanguagePrefix(languageId));
    }

    private static LanguageSetting view(LanguagePrefixRow row) {
        return new LanguageSetting(row.languageId(), row.displayName(), row.solutionPrefix(), row.updatedAt());
    }

    private static ProblemApiException validation(String message) {
        return new ProblemApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_FAILED", message);
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
