package com.cherryoj.problemservice.persistence;

import static com.cherryoj.problemservice.persistence.ProblemFixtures.ACTOR;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.cherryoj.problemservice.api.AdminProblemDtos.CodeMode;
import com.cherryoj.problemservice.api.AdminProblemDtos.CreateProblemRequest;
import com.cherryoj.problemservice.api.AdminProblemDtos.Difficulty;
import com.cherryoj.problemservice.api.LanguageSettingDtos.LanguageSetting;
import com.cherryoj.problemservice.api.LanguageSettingDtos.UpdateLanguageSettingRequest;
import com.cherryoj.problemservice.api.ProblemApiException;
import com.cherryoj.problemservice.application.AdminProblemService;
import com.cherryoj.problemservice.application.LanguageSettingService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@ActiveProfiles("dev")
@Testcontainers(disabledWithoutDocker = true)
class LanguageSettingIntegrationTests {

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.4")
            .withDatabaseName("cherry_oj_problem_language")
            .withUsername("cherry")
            .withPassword("test-password");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) throws Exception {
        Path root = Files.createTempDirectory("cherry-language-settings");
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("cherry.problem.test-data.root", root::toString);
        registry.add("cherry.problem.test-data.recovery-enabled", () -> "false");
    }

    @Autowired
    LanguageSettingService languages;

    @Autowired
    AdminProblemService admin;

    /** 前缀改了立即生效：CORE 题拿到新前缀、修改时间前进；没变就不动时间；拿旧时间保存的另一个窗口被拒绝。 */
    @Test
    void aPrefixChangeTakesEffectAndAStaleWindowIsRejected() {
        LanguageSetting cpp = languages.list().items().getFirst();
        assertThat(cpp.id()).isEqualTo("cpp");
        assertThat(cpp.solutionPrefix()).isEqualTo("#include <bits/stdc++.h>\nusing namespace std;\n");

        assertThat(languages.update("cpp", new UpdateLanguageSettingRequest(cpp.solutionPrefix(), cpp.updatedAt()), ACTOR))
                .isEqualTo(cpp);

        String changed = cpp.solutionPrefix() + "using ll = long long;\n";
        LanguageSetting saved = languages.update("cpp", new UpdateLanguageSettingRequest(changed, cpp.updatedAt()), ACTOR);
        assertThat(saved.solutionPrefix()).isEqualTo(changed);
        assertThat(saved.updatedAt()).isAfter(cpp.updatedAt());

        var core = admin.create(new CreateProblemRequest(
                "prefix-" + UUID.randomUUID().toString().substring(0, 8), "Prefix", Difficulty.EASY, CodeMode.CORE, "cpp"),
                ACTOR);
        assertThat(core.allowedLanguages().getFirst().solutionPrefix()).isEqualTo(changed);

        assertThatThrownBy(() -> languages.update("cpp",
                new UpdateLanguageSettingRequest(cpp.solutionPrefix(), cpp.updatedAt()), ACTOR))
                .isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("ROW_VERSION_CONFLICT"));
        languages.update("cpp", new UpdateLanguageSettingRequest(cpp.solutionPrefix(), saved.updatedAt()), ACTOR);
    }

    @Test
    void aPrefixMustEndWithAPlainNewlineAndTheLanguageMustExist() {
        LanguageSetting cpp = languages.list().items().getFirst();
        for (String invalid : new String[] {"#include <vector>", "#include <vector>\r\n", "a\0b\n"}) {
            assertThatThrownBy(() -> languages.update("cpp", new UpdateLanguageSettingRequest(invalid, cpp.updatedAt()),
                    ACTOR)).isInstanceOfSatisfying(ProblemApiException.class,
                            error -> assertThat(error.code()).isEqualTo("VALIDATION_FAILED"));
        }
        assertThatThrownBy(() -> languages.update("rust", new UpdateLanguageSettingRequest("use std::io;\n",
                cpp.updatedAt()), ACTOR)).isInstanceOfSatisfying(ProblemApiException.class,
                        error -> assertThat(error.code()).isEqualTo("LANGUAGE_NOT_FOUND"));
        assertThat(languages.list().items().getFirst()).isEqualTo(cpp);
    }
}
