package com.cherryoj.judgingservice.judge;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class JudgeDiagnosticsTests {

    /**
     * 13 门语言的真实编译、运行输出（测试资源 diagnostics/）：用户文件前都有 3 行前缀，
     * 在用户代码的某一行引用了未定义的名字。改写后，所有指向用户文件的位置都必须是用户自己的行号，且不再有沙箱目录。
     */
    @ParameterizedTest(name = "{0}")
    @CsvSource({
        "c, solution.c, 7",
        "cpp, solution.cpp, 7",
        "java, Solution.java, 8",
        "python, solution.py, 6",
        "go, solution.go, 7",
        "javascript, solution.js, 9",
        "typescript, solution.ts, 5",
        "csharp, Solution.cs, 5",
        "kotlin, Solution.kt, 6",
        "rust, solution.rs, 8",
        "swift, solution.swift, 5",
        "php, solution.php, 12",
        "ruby, solution.rb, '6 7'",
    })
    void everyLanguagesUserFileLinesComeBackToTheEditorsNumbers(String language, String solutionFile, String lines)
            throws IOException {
        String raw = fixture(language);

        String rewritten = JudgeDiagnostics.forUser(raw, solutionFile, 3, false);

        Set<Long> expected = java.util.Arrays.stream(lines.split(" "))
                .map(Long::parseLong)
                .collect(Collectors.toCollection(TreeSet::new));
        assertThat(userFileLines(rewritten, solutionFile)).isEqualTo(expected);
        assertThat(userFileLines(raw.replace("/w/", ""), solutionFile))
                .isEqualTo(expected.stream().map(line -> line + 3).collect(Collectors.toCollection(TreeSet::new)));
        assertThat(rewritten).doesNotContain("/w/");
    }

    @Test
    void sourceExcerptGuttersFollowTheirLocationAndKeepTheirWidth() throws IOException {
        String cpp = JudgeDiagnostics.forUser(fixture("cpp"), "solution.cpp", 3, false);
        assertThat(cpp).contains("    7 |").doesNotContain("   10 |");

        String swift = JudgeDiagnostics.forUser(fixture("swift"), "solution.swift", 3, false);
        assertThat(swift).contains(" 3 |", " 5 |", " 7 |").doesNotContain("10 |", " 8 |");
    }

    @Test
    void driverFileLocationsAndSourceAreShownAsTheyAre() {
        String raw = """
                Main.cpp: In function 'int main()':
                Main.cpp:8:30: error: no matching function for call to 'Solution::add(long long int&, long long int&)'
                    8 |     cout << Solution().add(a, b) << "\\n";
                      |             ~~~~~~~~~~~~~~^~~~~~
                solution.cpp:5:15: note: candidate: 'long long int Solution::add(long long int)'
                    5 |     long long add(long long a) { return a; }
                """;

        String rewritten = JudgeDiagnostics.forUser(raw, "solution.cpp", 2, false);

        assertThat(rewritten)
                .contains(
                        "Main.cpp:8:30:",
                        "    8 |     cout << Solution().add(a, b)",
                        "solution.cpp:3:15:",
                        "    3 |     long long add(long long a)");
    }

    @Test
    void linesInsideThePrefixAreLabelledInsteadOfTurningIntoZeroOrNegative() {
        String raw = "solution.cpp:2:1: error: redefinition of 'y1'\n    2 | using namespace std;\n";

        String rewritten = JudgeDiagnostics.forUser(raw, "solution.cpp", 2, false);

        assertThat(rewritten)
                .startsWith("[预置代码]:2:1: error")
                .contains("    2 | using namespace std;")
                .doesNotContain("solution.cpp:0", "solution.cpp:-");
    }

    @Test
    void aCutOffLastLineIsDroppedBeforeRewritingSoItCannotBeShiftedWrongly() {
        String cut = "solution.cpp:12:5: error: a\nsolution.cpp:1";

        assertThat(JudgeDiagnostics.forUser(cut, "solution.cpp", 2, true)).isEqualTo("solution.cpp:10:5: error: a\n");
        assertThat(JudgeDiagnostics.forUser("ends without newline", "solution.cpp", 2, false))
                .isEqualTo("ends without newline");
        assertThat(JudgeDiagnostics.looksCut("x\n")).isFalse();
        assertThat(JudgeDiagnostics.looksCut("x")).isTrue();
    }

    @Test
    void acmOnlyLosesDirectoriesAndControlCharacters() {
        String raw = "/private/w/Main.cpp:3:1: error\u001b[31m\u0000 in /usr/include/c++/13/bits/stl_vector.h:1125\n"
                + "  a / b and 2/3 stay; [/w/Main.csproj] and ./main.go:5 lose their directories\n";

        String rewritten = JudgeDiagnostics.forUser(raw, null, null, true);

        assertThat(rewritten)
                .isEqualTo("Main.cpp:3:1: error in stl_vector.h:1125\n"
                        + "  a / b and 2/3 stay; [Main.csproj] and main.go:5 lose their directories\n");
    }

    /** 文件名以 contracts/judge.schema.json 的 x-language-files 为准；judge 那边也有同样的对齐测试。 */
    @Test
    void languageFilesMatchTheContract() throws IOException {
        JsonNode files = JsonMapper.builder()
                .build()
                .readTree(Files.readString(Path.of("../../../contracts/judge.schema.json")))
                .get("x-language-files");
        Map<String, LanguageFiles> contract = files.propertyNames().stream()
                .collect(Collectors.toMap(
                        name -> name,
                        name -> new LanguageFiles(
                                files.get(name).get("driverFile").asString(),
                                files.get(name).get("solutionFile").asString())));

        assertThat(LanguageFiles.all()).isEqualTo(contract);
    }

    private static Set<Long> userFileLines(String text, String solutionFile) {
        Matcher matcher = Pattern.compile("(?<![\\w.\\-])" + Pattern.quote(solutionFile)
                        + "(?::(\\d+)|\\((\\d+),|\", line (\\d+)| on line (\\d+))")
                .matcher(text);
        Set<Long> lines = new TreeSet<>();
        while (matcher.find()) {
            for (int group = 1; group <= 4; group++) {
                if (matcher.group(group) != null) {
                    lines.add(Long.parseLong(matcher.group(group)));
                }
            }
        }
        return lines;
    }

    private static String fixture(String language) throws IOException {
        try (InputStream in = JudgeDiagnosticsTests.class.getResourceAsStream("/diagnostics/" + language + ".txt")) {
            if (in == null) {
                throw new IllegalStateException("missing fixture " + language);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
